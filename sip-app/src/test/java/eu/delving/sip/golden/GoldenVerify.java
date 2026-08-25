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

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

/**
 * Canonical golden-record verifier for the Lua mapping-engine feasibility
 * project (Task 5; see
 * {@code .superpowers/sdd/2026-08-25-lua-mapping-engine-deepdive/task-5-brief.md}).
 *
 * <p>Compares two RDF/XML files &mdash; a golden {@code expected} file
 * captured by {@link GoldenCapture} (Task 4) against a candidate engine's
 * {@code actual} output &mdash; and reports one of three verdicts:
 * <ul>
 *     <li>{@link Verdict#IDENTICAL} &mdash; the files are byte-for-byte
 *     equal.</li>
 *     <li>{@link Verdict#SEMANTIC} &mdash; the files differ byte-for-byte
 *     but parse to isomorphic RDF graphs (same triples, possibly reordered
 *     and/or with differently-labelled blank nodes).</li>
 *     <li>{@link Verdict#MISMATCH} &mdash; the graphs are not isomorphic;
 *     the message reports the first differing triple found when comparing
 *     sorted N-Triples serializations of both graphs. Because blank-node
 *     labels are not canonicalized for this report, the reported line is a
 *     best-effort pointer for a human, not the isomorphism verdict itself
 *     (that always comes from {@link Model#isIsomorphicWith}).</li>
 * </ul>
 *
 * <p>Used standalone (a case at a time) via {@link #main}, and looped over
 * all golden cases by {@code lua-poc/golden/Makefile}'s {@code verify}
 * target. Task 8 (the Lua/Go spike) invokes this on its own output to
 * confirm parity with the Groovy-engine goldens.
 */
public class GoldenVerify {

    public enum Verdict {
        IDENTICAL, SEMANTIC, MISMATCH
    }

    public record Result(Verdict verdict, String message) {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: GoldenVerify <expected-file> <actual-file>");
            System.exit(1);
            return;
        }

        Result result = verify(Path.of(args[0]), Path.of(args[1]));
        switch (result.verdict()) {
            case IDENTICAL -> {
                System.out.println("IDENTICAL");
                System.exit(0);
            }
            case SEMANTIC -> {
                System.out.println("SEMANTIC");
                System.exit(0);
            }
            case MISMATCH -> {
                System.out.println("MISMATCH: " + result.message());
                System.exit(1);
            }
        }
    }

    /**
     * Compares {@code expectedFile} and {@code actualFile} as RDF/XML and
     * returns the verdict. Never throws for a mismatch &mdash; only for I/O
     * or parse failures.
     */
    public static Result verify(Path expectedFile, Path actualFile) throws Exception {
        byte[] expectedBytes = Files.readAllBytes(expectedFile);
        byte[] actualBytes = Files.readAllBytes(actualFile);
        if (Arrays.equals(expectedBytes, actualBytes)) {
            return new Result(Verdict.IDENTICAL, null);
        }

        Model expectedModel = load(expectedFile);
        Model actualModel = load(actualFile);
        if (expectedModel.isIsomorphicWith(actualModel)) {
            return new Result(Verdict.SEMANTIC, null);
        }

        return new Result(Verdict.MISMATCH, firstDifferingTriple(expectedModel, actualModel));
    }

    private static Model load(Path file) {
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, file.toUri().toString(), Lang.RDFXML);
        return model;
    }

    /**
     * Serializes both models to N-Triples, sorts the lines, and returns a
     * description of the first line at which the two diverge (or, if one
     * is a strict prefix of the other, the first extra/missing line).
     * Blank-node labels are not canonicalized, so this is a best-effort
     * pointer for a human reader &mdash; the MISMATCH verdict itself always
     * comes from {@link Model#isIsomorphicWith}.
     */
    private static String firstDifferingTriple(Model expected, Model actual) {
        List<String> expectedLines = toSortedNTriples(expected);
        List<String> actualLines = toSortedNTriples(actual);

        int n = Math.min(expectedLines.size(), actualLines.size());
        for (int i = 0; i < n; i++) {
            String expectedLine = expectedLines.get(i);
            String actualLine = actualLines.get(i);
            if (!expectedLine.equals(actualLine)) {
                return "expected " + expectedLine + " but got " + actualLine;
            }
        }
        if (expectedLines.size() > actualLines.size()) {
            return "expected " + expectedLines.get(n) + " but it is missing from actual";
        }
        if (actualLines.size() > expectedLines.size()) {
            return "unexpected extra triple in actual: " + actualLines.get(n);
        }
        // Same triple count and every sorted line matches textually, yet
        // isomorphism failed: the divergence is purely in blank-node
        // structure (e.g. a blank node's properties differ). Not expected
        // in practice for these goldens, but report rather than crash.
        return "graphs are not isomorphic but no differing N-Triples line was found "
                + "(likely a blank-node structural difference)";
    }

    private static List<String> toSortedNTriples(Model model) {
        StringWriter writer = new StringWriter();
        RDFDataMgr.write(writer, model, Lang.NTRIPLES);
        return Arrays.stream(writer.toString().split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .sorted()
                .collect(Collectors.toList());
    }
}
