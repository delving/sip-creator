/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.metadata;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import eu.delving.groovy.XmlSerializer;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.JsonLDWriteContext;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.WriterGraphRIOT;
import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.riot.system.RiotLib;
import org.w3c.dom.Node;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class JenaHelper {

    public static String convertRDF(String defaultPrefix, Node node, RDFFormat outputFormat) {
        String rdf = new XmlSerializer().toXml(node, true);
        return convertRDF(defaultPrefix, rdf, outputFormat);
    }

    public static String convertRDF(String defaultPrefix, String rdf, RDFFormat outputFormat) {
        if (outputFormat == RDFFormat.RDFXML)
            return rdf;

        if (outputFormat == RDFFormat.JSONLD_FRAME_PRETTY) {
            Map<String, Object> defaultFrame = new HashMap<>();
            defaultFrame.put("@type", "Thing");
            return convertRDF(defaultPrefix, rdf, outputFormat, defaultFrame);
        }

        byte[] out = convertRDFTo(defaultPrefix, rdf, outputFormat);
        if (outputFormat == RDFFormat.JSONLD_COMPACT_PRETTY) {
            return formatJSON(out);
        }
        if (outputFormat == RDFFormat.NTRIPLES) {
            return formatXML(out);
        }
        if (outputFormat == RDFFormat.NQUADS) {
            return formatXML(out);
        }
        if (outputFormat == RDFFormat.TURTLE) {
            return formatXML(out);
        }
        throw new UnsupportedOperationException("Conversion to " + outputFormat + " is not supported");
    }

    public static String convertRDF(String defaultPrefix, String rdf, RDFFormat outputFormat, Map<String, Object> frame) {
        if (outputFormat == RDFFormat.JSONLD_FRAME_PRETTY) {
            if (frame == null) {
                throw new IllegalArgumentException("frame cannot be null for JSONLD_FRAME_PRETTY format");
            }
            return convertRDFWithFrame(defaultPrefix, rdf, outputFormat, frame);
        }

        return convertRDF(defaultPrefix, rdf, outputFormat);
    }

    /**
     * Preview-oriented JSON-LD conversion driven by an explicit (typically recdef-generated)
     * {@code @context} and/or frame, both supplied as raw JSON strings.
     *
     * <p>Both {@code contextJson} and {@code frameJson} may be {@code null}; if both are
     * {@code null} this delegates unchanged to {@link #convertRDF(String, String, RDFFormat)}.
     *
     * <p>For {@link RDFFormat#JSONLD_COMPACT_PRETTY}, {@code contextJson} drives compaction
     * (short terms in the output), but the output's own {@code "@context"} node is replaced
     * with an empty object ({@code "{}"}) via
     * {@link org.apache.jena.riot.JsonLDWriteContext#setJsonLDContextSubstitution(String)}
     * rather than echoing {@code contextJson} back verbatim. This is deliberate: the JSON-LD
     * compaction algorithm always echoes the context it was given into the compacted result
     * (see {@code com.github.jsonldjava.core.JsonLdProcessor#compact}), and a recdef-derived
     * context can be large; re-embedding it in every previewed record would be noisy. As a
     * result, <b>the returned string for the compact case is preview output, not a
     * self-contained/round-trippable JSON-LD document</b> — its {@code "@context"} does not
     * carry the term definitions used to produce it. Callers that need the full context
     * in-band (e.g. downstream consumers expanding/re-compacting the result) should use the
     * 3-arg {@link #convertRDF(String, String, RDFFormat)} overload (auto-generated context,
     * embedded as-is) or extend this API with an explicit "embed context" option — do not
     * assume this overload's compact output already carries one.
     *
     * <p>For {@link RDFFormat#JSONLD_FRAME_PRETTY}, {@code frameJson} drives framing via
     * {@link org.apache.jena.riot.JsonLDWriteContext#setFrame(String)}; a non-null
     * {@code contextJson} is set on the write context but has no effect on the frame branch
     * (framing does not consult {@code JSONLD_CONTEXT}) and is otherwise harmless to pass.
     */
    public static String convertRDF(String defaultPrefix, String rdf, RDFFormat outputFormat, String contextJson, String frameJson) {
        if (contextJson == null && frameJson == null) {
            return convertRDF(defaultPrefix, rdf, outputFormat);
        }

        String compliantRDF = toJenaCompliantRDFIfNeeded(defaultPrefix, rdf);
        InputStream in = new ByteArrayInputStream(compliantRDF.getBytes(StandardCharsets.UTF_8));
        Model model = ModelFactory.createDefaultModel().read(in, "", "RDF/XML");

        ByteArrayOutputStream out = new ByteArrayOutputStream(2048);

        JsonLDWriteContext ctx = new JsonLDWriteContext();
        if (contextJson != null) {
            ctx.setJsonLDContext(contextJson);
            // The JSON-LD compaction algorithm always echoes the context it was given
            // back into the "@context" node of the compacted output (see
            // JsonLdProcessor#compact). For a recdef-derived context that can be large,
            // so substitute an empty placeholder for the echoed node while still using
            // the real context (set above) to drive the compaction itself.
            ctx.setJsonLDContextSubstitution("{}");
        }
        if (frameJson != null) {
            ctx.setFrame(frameJson);
        }

        WriterGraphRIOT writer = RDFDataMgr.createGraphWriter(outputFormat);
        PrefixMap prefixes = RiotLib.prefixMap(model.getGraph());
        writer.write(out, model.getGraph(), prefixes, null, ctx);

        return formatJSON(out.toByteArray());
    }

    private static String convertRDFWithFrame(String defaultPrefix, String rdf, RDFFormat outputFormat, Map<String, Object> frame) {
        String compliantRDF = toJenaCompliantRDFIfNeeded(defaultPrefix, rdf);
        InputStream in = new ByteArrayInputStream(compliantRDF.getBytes(StandardCharsets.UTF_8));
        Model model = ModelFactory.createDefaultModel().read(in, "", "RDF/XML");
        
        ByteArrayOutputStream out = new ByteArrayOutputStream(2048);
        
        JsonLDWriteContext ctx = new JsonLDWriteContext();
        ctx.setFrame(frame);
        
        WriterGraphRIOT writer = RDFDataMgr.createGraphWriter(outputFormat);
        PrefixMap prefixes = RiotLib.prefixMap(model.getGraph());
        writer.write(out, model.getGraph(), prefixes, null, ctx);
        
        return formatJSON(out.toByteArray());
    }

    private static String formatXML(byte[] out) {
        return new String(out, StandardCharsets.UTF_8);
    }

    private static String formatJSON(byte[] out) {
        Reader jsonReader = new InputStreamReader(new ByteArrayInputStream(out));
        JsonElement json = new JsonParser().parse(jsonReader);
        return new GsonBuilder()
                .setPrettyPrinting()
                .create()
                .toJson(json);
    }

    private static byte[] convertRDFTo(String defaultPrefix, String rdf, RDFFormat outputFormat) {
        String compliantRDF = toJenaCompliantRDFIfNeeded(defaultPrefix, rdf);
        InputStream in = new ByteArrayInputStream(compliantRDF.getBytes(StandardCharsets.UTF_8));
        Model model = ModelFactory.createDefaultModel().read(in, "", "RDF/XML");
        ByteArrayOutputStream out = new ByteArrayOutputStream(2048);
        RDFDataMgr.write(out, model, outputFormat);
        return out.toByteArray();
    }

    private static String toJenaCompliantRDFIfNeeded(String defaultPrefix, String rdf) {
        if (rdf.contains("<rdf:RDF") && rdf.contains("xmlns:rdf=")) {
            return rdf;
        }
        return MappingResult.toJenaCompliantRDF(defaultPrefix, rdf);
    }

    public static String getExtension(RDFFormat outputFormat) {
        if (outputFormat == null) {
            throw new IllegalArgumentException("outputFormat cannot be null");
        }
        if (outputFormat == RDFFormat.JSONLD_COMPACT_PRETTY) {
            return ".json";
        }
        if (outputFormat == RDFFormat.JSONLD_FRAME_PRETTY) {
            return ".json";
        }
        if (outputFormat == RDFFormat.NTRIPLES) {
            return ".ntriples";
        }
        if (outputFormat == RDFFormat.NQUADS) {
            return ".nquads";
        }
        if (outputFormat == RDFFormat.TURTLE) {
            return ".turtle";
        }
        throw new UnsupportedOperationException("Format " + outputFormat + " is not supported");
    }
}
