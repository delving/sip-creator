/*
 * Copyright 2011, 2012 Delving BV
 *
 * Licensed under the EUPL, Version 1.0 or? as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the
 * Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.delving.sip.xml;

import eu.delving.groovy.*;
import eu.delving.metadata.*;
import eu.delving.sip.Application;
import eu.delving.sip.base.CancelException;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.ReportWriter;
import eu.delving.sip.model.Feedback;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Process an input file, mapping it to output records which are validated and used to gather statistics.
 * A validation report is produced. Output can be recorded for experts as well.  The processing is paused
 * for user input when invalid records are encountered.
 */

public class FileProcessor implements Work.DataSetPrefixWork, Work.LongTermWork {
    private final Feedback feedback;
    private final boolean enableXSDValidation;
    private final DataSet dataSet;
    private final RecMapping recMapping;
    private final GroovyCodeResource groovyCodeResource;
    private final UriGenerator uriGenerator;
    private final boolean allowInvalid;
    private final Listener listener;
    private ProgressListener progressListener;
    private final Termination termination = new Termination();
    private final Object lock = new Object();
    private NQuadWriter nQuadWriter;

    private void info(String message) {
        if (feedback != null) {
            feedback.info(message);
        } else {
            System.out.println(message);
        }
    }

    public interface Listener {

        void failed(FileProcessor fileProcessor);

        void aborted(FileProcessor fileProcessor);

        void succeeded(FileProcessor fileProcessor);
    }

    public interface UriGenerator {
        String generateUri(String id);
    }

    public FileProcessor(
        Feedback feedback,
        boolean enableXSDValidation,
        DataSet dataSet,
        RecMapping recMapping,
        boolean allowInvalid,
        GroovyCodeResource groovyCodeResource,
        UriGenerator uriGenerator,
        Listener listener
    ) {
        this.feedback = feedback;
        this.enableXSDValidation = enableXSDValidation;
        this.dataSet = dataSet;
        this.recMapping = recMapping;
        this.allowInvalid = allowInvalid;
        this.groovyCodeResource = groovyCodeResource;
        this.uriGenerator = uriGenerator;
        this.listener = listener;
    }

    public String getSpec() {
        return dataSet.getSpec();
    }

    public int getFailedRecordNumber() {
        return termination.getRecordNumber();
    }

    private RecDef recDef() {
        return recDefTree().getRecDef();
    }

    private RecDefTree recDefTree() {
        return recMapping.getRecDefTree();
    }

    @Override
    public Job getJob() {
        return Job.PROCESS;
    }

    @Override
    public String getPrefix() {
        return recMapping.getPrefix();
    }

    @Override
    public DataSet getDataSet() {
        return dataSet;
    }

    @Override
    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
        progressListener.setProgressMessage("Map, validate, gather stats");
    }

    @Override
    public void run() {
        groovyCodeResource.clearMappingScripts();
        try {
            // TODO record count is never used
            MetadataParser parser = new MetadataParser(getDataSet().openSourceInputStream());
            parser.setProgressListener(progressListener);
            ReportWriter reportWriter = getDataSet().openReportWriter(recMapping.getRecDefTree().getRecDef());

            File outputDir = createEmptyOutputDir();
            nQuadWriter = new NQuadWriter(outputDir.toPath());
            nQuadWriter.start();

            Consumer consumer = new Consumer(reportWriter);
            int engineCount = (int) Math.round(Runtime.getRuntime().availableProcessors() * 1.1);
            info(String.format("Processing with %d engines", engineCount));

            String code = new CodeGenerator(recMapping).withEditPath(null).withTrace(false).toRecordMappingCode();
            MappingRunner MappingRunner = new BulkMappingRunner(recMapping, code);
            List<AssertionTest> assertionTests = AssertionTest.listFrom(recMapping.getRecDefTree().getRecDef(), groovyCodeResource);

            MetadataParserRunner metadataParserRunner = new MetadataParserRunner(parser);
            metadataParserRunner.start();
            for (int walk = 0; walk < engineCount; walk++) {
                Validator validator = null;
                if (enableXSDValidation) {
                    validator = dataSet.newValidator();
                    validator.setErrorHandler(null);
                }
                MappingEngine engine = new MappingEngine(
                    walk,
                    metadataParserRunner,
                    validator,
                    allowInvalid,
                    termination,
                    outputDir,
                    recDef().getNamespaceMap(),
                    reportWriter,
                    assertionTests,
                    MappingRunner
                );
                consumer.register(engine);
                engine.start();
            }
            info(Thread.currentThread().getName() + " about to consume");
            consumer.run();
        } catch (Exception e) {
            termination.dueToException(e);
            feedback.alert("File processing setup problem", e);
        }
    }

    private File createEmptyOutputDir() {
        File sipDir = getDataSet().targetOutput().getParentFile();
        File outputDir = new File(sipDir, "output");
        if (!outputDir.exists()) {
            if (!outputDir.mkdir()) {
                throw new IllegalStateException("Unable to create directory: " + outputDir);
            }
        } else {
            File[] files = Objects.requireNonNull(outputDir.listFiles());
            for (File file : files) {
                if (!file.delete()) throw new IllegalStateException("Unable to delete file: " + file);
            }
        }
        return outputDir;
    }

    private class MetadataParserRunner implements Runnable {

        final BlockingQueue<MetadataRecord> outputQueue = new LinkedBlockingDeque<>();
        final Thread thread = new Thread(this);
        final MetadataParser metadataParser;

        private MetadataParserRunner(MetadataParser metadataParser) {
            this.metadataParser = metadataParser;
            thread.setName(getClass().getName());
        }

        public void drainTo(List<MetadataRecord> collector, int capacity) {
            outputQueue.drainTo(collector, capacity);
        }

        @Override
        public void run() {
            MetadataRecord record;
            while (true) {
                try {
                    if (!((record = metadataParser.nextRecord()) != null)) break;
                    outputQueue.add(record);
                    while (outputQueue.size() > 5000) {
                        System.out.println("Sleeping... " + System.currentTimeMillis());
                        Thread.sleep(250);
                    }
                } catch (XMLStreamException | IOException | CancelException | InterruptedException e) {
                    termination.dueToException(e);
                    break;
                }
            }
        }

        public void start() {
            thread.start();
        }
    }

    private class Consumer implements Runnable {
        final List<MappingEngine> engines = new ArrayList<>();
        final ReportWriter reportWriter;

        private Consumer(ReportWriter reportWriter) {
            this.reportWriter = reportWriter;
        }

        @Override
        public void run() {
            int recordCount = 0;
            int validCount = 0;
            for (MappingEngine engine : engines) {
                try {
                    engine.thread.join();
                    recordCount += engine.recordCount;
                    validCount += engine.validCount;
                } catch (InterruptedException e) {
                    // nothing to do here
                }
            }
            nQuadWriter.stop();

            if (termination.isIncomplete()) {
                info("Abort report writer");
                if (reportWriter != null) {
                    reportWriter.abort();
                }
            } else {
                info(String.format("Finish report writer records=%d valid=%d", recordCount, validCount));
                if (reportWriter != null) {
                    reportWriter.finish(validCount, recordCount - validCount);
                }
                termination.normalCompletion();
            }
        }

        public void register(MappingEngine engine) {
            engines.add(engine);
        }
    }

    private class MappingEngine implements Runnable {
        private final MetadataParserRunner metadataParserRunner;
        private final Validator validator;
        private final boolean allowInvalid;
        private final Termination termination;
        public boolean isDone;
        private final Thread thread;
        private final XmlSerializer serializer = new XmlSerializer();
        public int recordCount;
        public int validCount;
        final File outputDir;
        final Map<String, RecDef.Namespace> namespaceMap;
        final ReportWriter reportWriter;
        final List<AssertionTest> assertionTests;
        final MappingRunner MappingRunner;

        private MappingEngine(int index,
                              MetadataParserRunner metadataParserRunner,
                              Validator validator,
                              boolean allowInvalid, Termination termination,
                              File outputDir,
                              Map<String, RecDef.Namespace> namespaceMap,
                              ReportWriter reportWriter,
                              List<AssertionTest> assertionTests,
                              MappingRunner MappingRunner
        ) {
            this.metadataParserRunner = metadataParserRunner;
            this.validator = validator;
            this.allowInvalid = allowInvalid;
            this.termination = termination;
            this.outputDir = outputDir;
            this.namespaceMap = namespaceMap;
            this.reportWriter = reportWriter;
            this.assertionTests = assertionTests;
            this.MappingRunner = MappingRunner;
            this.thread = new Thread(this);
            this.thread.setName("MappingEngine" + index);
        }

        public void start() {
            thread.start();
        }

        public void accept(MappingResult mappingResult) throws XMLStreamException, IOException, InterruptedException {
            validCount++;

            byte[] nQuad = mappingResult.toNQuad(dataSet.getDataSetFacts());
            byte[] xml = null;
            if (Application.canWritePocketFiles()) {
                XmlOutput xmlOutput = new XmlOutput(namespaceMap);
                xmlOutput.write(mappingResult.getLocalId(), mappingResult.root());
                xmlOutput.finish();

                xml = xmlOutput.toBytes();
            }
            nQuadWriter.put(new NQuadWriter.Input(nQuad, xml));
        }

        @Override
        public void run() {
            final int capacity = 50;
            final ArrayList<MetadataRecord> records = new ArrayList<>(capacity);

            while (termination.notYet()) {

                records.clear();
                metadataParserRunner.drainTo(records, capacity);
                for (MetadataRecord record : records) {

                    try {
                        recordCount++;
                        MappingResult result = runMapping(record);
                        accept(result);
                    } catch (Throwable e) {

                        boolean isFatal = reportWriter.recordError(record, null, e);
                        if (isFatal) {
                            termination.dueToException(e);
                            return;
                        }
                    } finally {
                        isDone = true;
                    }
                }
            }
        }

        private MappingResult runMapping(MetadataRecord record) throws XPathExpressionException, MappingException, AssertionException, IOException, SAXException {
            Node node = MappingRunner.runMapping(record);
            if (node == null) return null;
            MappingResult result = new MappingResult(serializer, uriGenerator.generateUri(record.getId()), node, MappingRunner.getRecDefTree());
            validate(node, result);
            return result;
        }

        private void validate(Node node, MappingResult result) throws XPathExpressionException, AssertionException, IOException, SAXException {
            List<String> uriErrors = result.getUriErrors();
            if (!uriErrors.isEmpty()) {
                StringBuilder uriErrorsString = new StringBuilder();
                for (String uriError : uriErrors) {
                    uriErrorsString.append(uriError).append("\n");
                }
                throw new RuntimeException("URI Errors\n" + uriErrorsString);
            }
            if (validator != null) {
                Source source = new DOMSource(node);
                validator.validate(source);
                for (AssertionTest assertionTest : assertionTests) {
                    String violation = assertionTest.getViolation(result.root());
                    if (violation != null) throw new AssertionException(violation);
                }
            }
        }
    }

    private class Termination {
        private MetadataRecord failedRecord;
        private Throwable exception;
        private boolean completed;

        boolean notYet() {
            return !completed && !isIncomplete();
        }

        boolean isIncomplete() {
            return failedRecord != null || exception != null;
        }

        int getRecordNumber() {
            return failedRecord == null ? 0 : failedRecord.getRecordNumber();
        }

        void normalCompletion() {
            completed = true;
            if (listener != null) {
                listener.succeeded(FileProcessor.this);
            }
        }

        void dueToException(Throwable exception) {
            dueToException(null, exception);
        }

        synchronized void dueToException(MetadataRecord failedRecord, Throwable exception) {
            if (this.exception == null) { // only show one of them
                if (feedback != null) {
                    feedback.alert("Problem processing", exception);
                } else {
                    System.out.println("Problem processing: ");
                    exception.printStackTrace();
                }
            }
            this.failedRecord = failedRecord;
            this.exception = exception;
            if (listener != null) {
                listener.failed(FileProcessor.this);
            }
        }
    }
}
