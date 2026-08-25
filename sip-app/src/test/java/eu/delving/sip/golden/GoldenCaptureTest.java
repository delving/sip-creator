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

package eu.delving.sip.golden;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test for {@link GoldenCapture}: runs one T1-only case (the smallest
 * of the 10 selected cases, {@code nationaal-gevangenismuseum}) through the
 * real Groovy mapping engine and asserts the captured output is non-empty
 * and parses as valid RDF/XML.
 *
 * <p>This is also the empirical check for open question #1 in
 * {@code docs/specs/mapping-language-core.md} §10: whether generated
 * subscript access (e.g. {@code _input['@id'][0]}) actually works at
 * runtime despite {@code MappingCategory.getAt} throwing unconditionally.
 * A passing capture on real corpus mappings settles it (see
 * {@code lua-poc/golden/mappings/README.md}).
 */
public class GoldenCaptureTest {

    private static final Path CASE_DIR = Path.of(
            "..", "lua-poc", "golden", "mappings", "nationaal-gevangenismuseum");

    @Test
    public void capturesNonEmptyParseableRdf(@TempDir Path tempDir) throws Exception {
        Path outFile = tempDir.resolve("nationaal-gevangenismuseum.rdf.xml");

        GoldenCapture.capture(CASE_DIR, outFile);

        assertTrue(Files.exists(outFile), "capture must write the output file");
        String xml = Files.readString(outFile, StandardCharsets.UTF_8);
        assertFalse(xml.isBlank(), "captured RDF/XML must not be empty");

        // Must parse as valid RDF/XML via Jena (RIOT), matching how
        // MappingResult.hasRDFError validates captured output elsewhere.
        Model model = ModelFactory.createDefaultModel();
        try (InputStream in = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            RDFDataMgr.read(model, in, Lang.RDFXML);
        }
        assertFalse(model.isEmpty(), "captured RDF/XML must contain at least one triple");
    }
}
