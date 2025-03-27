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
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.w3c.dom.Node;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class JenaHelper {

    public static String convertRDF(String defaultPrefix, Node node, RDFFormat outputFormat) {
        String rdf = new XmlSerializer().toXml(node, true);
        return convertRDF(defaultPrefix, rdf, outputFormat);
    }

    public static String convertRDF(String defaultPrefix, String rdf, RDFFormat outputFormat) {
        if (outputFormat == RDFFormat.RDFXML)
            return rdf;

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
        String compliantRDF = MappingResult.toJenaCompliantRDF(defaultPrefix, rdf);
        InputStream in = new ByteArrayInputStream(compliantRDF.getBytes(StandardCharsets.UTF_8));
        Model model = ModelFactory.createDefaultModel().read(in, null, "RDF/XML");
        ByteArrayOutputStream out = new ByteArrayOutputStream(2048);
        RDFDataMgr.write(out, model, outputFormat);
        return out.toByteArray();
    }

    public static String getExtension(RDFFormat outputFormat) {
        if (outputFormat == RDFFormat.JSONLD_COMPACT_PRETTY) {
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
