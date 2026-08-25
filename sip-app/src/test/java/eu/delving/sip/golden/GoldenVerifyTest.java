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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD for {@link GoldenVerify}, the canonical golden-record verifier for
 * the Lua mapping-engine feasibility project (Task 5; see
 * {@code .superpowers/sdd/2026-08-25-lua-mapping-engine-deepdive/task-5-brief.md}).
 *
 * <p>Uses the {@code nationaal-gevangenismuseum} golden file captured by
 * Task 4 ({@link GoldenCapture}) as its base fixture, exercising three
 * verdicts:
 * <ul>
 *     <li>a file compared against itself &rarr; {@code IDENTICAL}</li>
 *     <li>the same triples with the top-level resource elements reordered
 *     &rarr; {@code SEMANTIC} (byte-different, isomorphic)</li>
 *     <li>one changed literal &rarr; {@code MISMATCH}, with the offending
 *     triple reported</li>
 * </ul>
 */
public class GoldenVerifyTest {

    private static final Path EXPECTED_FILE = Path.of(
            "..", "lua-poc", "golden", "expected", "nationaal-gevangenismuseum.rdf.xml");

    @Test
    public void identicalFileIsIdentical() throws Exception {
        GoldenVerify.Result result = GoldenVerify.verify(EXPECTED_FILE, EXPECTED_FILE);

        assertEquals(GoldenVerify.Verdict.IDENTICAL, result.verdict());
    }

    @Test
    public void reorderedButSameTriplesIsSemantic(@TempDir Path tempDir) throws Exception {
        String original = Files.readString(EXPECTED_FILE, StandardCharsets.UTF_8);

        // Swap the order of the two top-level resource blocks. The RDF
        // graph (a set of triples) is unaffected by element order, so this
        // must be byte-different but isomorphic.
        int aggStart = original.indexOf("<ore:Aggregation");
        int aggEnd = original.indexOf("</ore:Aggregation>") + "</ore:Aggregation>".length();
        int choStart = original.indexOf("<edm:ProvidedCHO");
        int choEnd = original.indexOf("</edm:ProvidedCHO>") + "</edm:ProvidedCHO>".length();
        assertTrue(aggStart >= 0 && aggEnd > aggStart, "fixture must contain ore:Aggregation");
        assertTrue(choStart >= 0 && choEnd > choStart, "fixture must contain edm:ProvidedCHO");
        assertTrue(choStart > aggEnd, "fixture must have ProvidedCHO after Aggregation");

        String aggBlock = original.substring(aggStart, aggEnd);
        String choBlock = original.substring(choStart, choEnd);
        String reordered = original.substring(0, aggStart)
                + choBlock
                + original.substring(aggEnd, choStart)
                + aggBlock
                + original.substring(choEnd);

        assertTrue(!reordered.equals(original), "reordered fixture must differ byte-for-byte from the original");

        Path reorderedFile = tempDir.resolve("reordered.rdf.xml");
        Files.writeString(reorderedFile, reordered, StandardCharsets.UTF_8);

        GoldenVerify.Result result = GoldenVerify.verify(EXPECTED_FILE, reorderedFile);

        assertEquals(GoldenVerify.Verdict.SEMANTIC, result.verdict());
    }

    @Test
    public void changedLiteralIsMismatchAndReportsTriple() throws Exception {
        String original = Files.readString(EXPECTED_FILE, StandardCharsets.UTF_8);
        String mutated = original.replace(
                "<dc:title>Ontsnappingstouw met houten balk.</dc:title>",
                "<dc:title>Something completely different.</dc:title>");
        assertTrue(!mutated.equals(original), "mutation must actually change the fixture");

        Path mutatedFile = Files.createTempFile("golden-verify-mismatch-", ".rdf.xml");
        try {
            Files.writeString(mutatedFile, mutated, StandardCharsets.UTF_8);

            GoldenVerify.Result result = GoldenVerify.verify(EXPECTED_FILE, mutatedFile);

            assertEquals(GoldenVerify.Verdict.MISMATCH, result.verdict());
            assertTrue(result.message().contains("Something completely different.")
                            || result.message().contains("Ontsnappingstouw met houten balk."),
                    "MISMATCH message must contain the differing triple, was: " + result.message());
        } finally {
            Files.deleteIfExists(mutatedFile);
        }
    }
}
