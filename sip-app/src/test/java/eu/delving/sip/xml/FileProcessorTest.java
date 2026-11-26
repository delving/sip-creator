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

package eu.delving.sip.xml;

import eu.delving.groovy.GroovyCodeResource;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import eu.delving.sip.MockProgressListener;
import eu.delving.sip.cli.CLIFeedback;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.ReportWriter;
import org.apache.jena.riot.RDFFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * FileProcessor end to end on a small mapping (the nationaal-gevangenismuseum
 * golden case), in its default mode: no XSD validation, so MappingCategory is
 * entered once per engine thread.
 */
public class FileProcessorTest {

    private static final Path CASE_DIR = Path.of("..", "lua-poc", "golden", "mappings", "nationaal-gevangenismuseum");
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    // more records than the parser's queue holds, so a parser that never gives up would block
    private static final int MANY = 3000;
    private static final String AGGREGATION_ABOUT =
            "&quot;${baseUrl}/resource/aggregation/${spec}/${_uniqueIdentifier.sanitizeURI()}&quot;";
    private static final String IS_SHOWN_BY =
            "&quot;http://www.dcn-images.nl/img/gm/${_inventarisnummer.sanitizeURI()}.jpg&quot;";

    @TempDir
    Path tempDir;

    @Test
    public void endOfInputCompletes() throws Exception {
        Run run = run(mapping(), MANY, false, true);
        assertEquals("succeeded", run.outcome);
        assertEquals(MANY, count(run.output, "<edm:ProvidedCHO "));
    }

    @Test
    public void engineFailureFailsTheRun() throws Exception {
        String mapping = mapping().replaceFirst(Pattern.quote(IS_SHOWN_BY),
                "throw new IllegalStateException(&quot;boom&quot;)");
        Run run = run(mapping, MANY, false, true);
        assertEquals("failed", run.outcome);
        assertEquals("cancelled", run.processedOutput);
    }

    @Test
    public void errorInMappingFailsTheRun() throws Exception {
        // an Error, not an Exception: allowInvalid must not let it through either
        String mapping = mapping().replaceFirst(Pattern.quote(IS_SHOWN_BY),
                "throw new StackOverflowError(&quot;deep&quot;)");
        Run run = run(mapping, MANY, true, true);
        assertEquals("failed", run.outcome);
        assertEquals("cancelled", run.processedOutput);
        assertParserStops();
    }

    /** The parser gives up on a full queue once the run has failed, instead of polling forever. */
    private static void assertParserStops() throws InterruptedException {
        String name = FileProcessor.class.getName() + "$MetadataParserRunner";
        long deadline = System.currentTimeMillis() + 10_000;
        while (Thread.getAllStackTraces().keySet().stream().anyMatch(t -> t.getName().equals(name))) {
            if (System.currentTimeMillis() > deadline) fail("parser thread still running");
            Thread.sleep(50);
        }
    }

    @Test
    public void skipRdfParseCheckSkipsTheCheck() throws Exception {
        // a blank-node subject with a space: the DOM checks let "_:" through, Jena rejects it
        String mapping = mapping().replace(AGGREGATION_ABOUT, "&quot;_:bad id&quot;");
        Run checked = run(mapping, 5, true, true);
        assertEquals("succeeded", checked.outcome);
        assertEquals(0, count(checked.output, "_:bad id"));
        Run unchecked = run(mapping, 5, true, false);
        assertEquals("succeeded", unchecked.outcome);
        assertEquals(5, count(unchecked.output, "_:bad id"));
    }

    private static String mapping() throws Exception {
        return Files.readString(CASE_DIR.resolve("mapping.xml"), StandardCharsets.UTF_8);
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) n++;
        return n;
    }

    static class Run {
        String outcome;
        String processedOutput;
        String output;
    }

    Run run(String mappingXml, int records, boolean allowInvalid, boolean rdfParseCheck) throws Exception {
        RecDef recDef;
        try (InputStream in = Files.newInputStream(CASE_DIR.resolve("recdef.xml"))) {
            recDef = RecDef.read(in);
        }
        RecMapping recMapping = RecMapping.read(
                new ByteArrayInputStream(mappingXml.getBytes(StandardCharsets.UTF_8)), RecDefTree.create(recDef));

        String pocket = Files.readString(CASE_DIR.resolve("record.xml"), StandardCharsets.UTF_8).trim();
        StringBuilder source = new StringBuilder("<pockets>");
        for (int i = 0; i < records; i++) {
            source.append(pocket.replace("id=\"H7807b\"", "id=\"r" + i + "\""));
        }
        byte[] sourceBytes = source.append("</pockets>").toString().getBytes(StandardCharsets.UTF_8);

        Run run = new Run();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        File report = tempDir.resolve("report-" + System.nanoTime() + ".json").toFile();
        DataSet dataSet = (DataSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{DataSet.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getSpec":
                            return "test";
                        case "openSourceInputStream":
                            return new ByteArrayInputStream(sourceBytes);
                        case "targetOutput":
                            return tempDir.resolve("sip").resolve("target").toFile();
                        case "openProcessedOutputStream":
                            return output;
                        case "finishProcessedOutput":
                            run.processedOutput = "finished";
                            return null;
                        case "cancelProcessedOutput":
                            run.processedOutput = "cancelled";
                            return null;
                        case "openReportWriter":
                            return new ReportWriter(report);
                        case "finishReportWriter":
                        case "cancelReportWriter":
                            return null;
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
        FileProcessor.Listener listener = new FileProcessor.Listener() {
            public void failed(FileProcessor p) { run.outcome = "failed"; }
            public void aborted(FileProcessor p) { run.outcome = "aborted"; }
            public void succeeded(FileProcessor p) { run.outcome = "succeeded"; }
        };
        FileProcessor processor = new FileProcessor(new CLIFeedback(), false, false, dataSet, recMapping,
                allowInvalid, new GroovyCodeResource(ClassLoader.getSystemClassLoader()), id -> id, listener,
                RDFFormat.RDFXML);
        processor.setRdfParseCheck(rdfParseCheck);
        processor.setProgressListener(new MockProgressListener());
        assertTimeoutPreemptively(TIMEOUT, processor::run);
        run.output = output.toString(StandardCharsets.UTF_8);
        return run;
    }
}
