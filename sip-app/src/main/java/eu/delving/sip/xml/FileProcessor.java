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

import eu.delving.groovy.*;
import eu.delving.metadata.*;
import eu.delving.sip.base.CancelException;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.ReportWriter;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.Feedback;
import io.sentry.*;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.ErrorHandler;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.lib.ShLib;
import org.w3c.dom.Node;

import javax.swing.*;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Validator;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Process an input file, mapping it to output records which are validated and
 * used to gather statistics.
 * A validation report is produced. Output can be recorded for experts as well.
 * The processing is paused
 * for user input when invalid records are encountered.
 *
 *
 */

public class FileProcessor implements Work.DataSetPrefixWork, Work.LongTermWork {
    private final Feedback feedback;
    private final boolean enableXSDValidation;
    private final boolean enableSHACLValidation;
    private final DataSet dataSet;
    private final RecMapping recMapping;
    private final GroovyCodeResource groovyCodeResource;
    private final UriGenerator uriGenerator;
    private final boolean allowInvalid;
    private final Listener listener;
    private ProgressListener progressListener;
    private final Termination termination = new Termination();
    private final Object lock = new Object();
    private RDFFormat rdfFormat;
    private final Date time;

    public Feedback getFeedback() {
        return feedback;
    }

    public boolean isEnableXSDValidation() {
        return enableXSDValidation;
    }

    public boolean isEnableSHACLValidation() {
        return enableSHACLValidation;
    }

    public RecMapping getRecMapping() {
        return recMapping;
    }

    public GroovyCodeResource getGroovyCodeResource() {
        return groovyCodeResource;
    }

    public UriGenerator getUriGenerator() {
        return uriGenerator;
    }

    public boolean isAllowInvalid() {
        return allowInvalid;
    }

    public Listener getListener() {
        return listener;
    }

    public ProgressListener getProgressListener() {
        return progressListener;
    }

    public Termination getTermination() {
        return termination;
    }

    public Object getLock() {
        return lock;
    }

    public RDFFormat getRdfFormat() {
        return rdfFormat;
    }

    public void setRdfFormat(RDFFormat rdfFormat) {
        this.rdfFormat = rdfFormat;
    }

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
            boolean enableSHACLValidation,
            DataSet dataSet,
            RecMapping recMapping,
            boolean allowInvalid,
            GroovyCodeResource groovyCodeResource,
            UriGenerator uriGenerator,
            Listener listener,
            RDFFormat rdfFormat) {
        this.feedback = feedback;
        this.enableXSDValidation = enableXSDValidation;
        this.enableSHACLValidation = enableSHACLValidation;
        this.dataSet = dataSet;
        this.recMapping = recMapping;
        this.allowInvalid = allowInvalid;
        this.groovyCodeResource = groovyCodeResource;
        this.uriGenerator = uriGenerator;
        this.listener = listener;
        this.rdfFormat = rdfFormat;
        this.time = Calendar.getInstance().getTime();
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
        OutputStream outputStream = null;
        MetadataParser parser = null;
        try {
            parser = new MetadataParser(getDataSet().openSourceInputStream(), -1);
            parser.setProgressListener(progressListener);

            Graph shape = null;
            if (enableSHACLValidation) {
                shape = dataSet.newShape();
            }

            File outputDir = createEmptyOutputDir();
            outputStream = getDataSet().openProcessedOutputStream(getPrefix(), time);

            ReportWriter reportWriter = getDataSet().openReportWriter(getPrefix(), time);
            Consumer consumer = new Consumer(reportWriter);
            int engineCount = (int) Math.round(Runtime.getRuntime().availableProcessors() * 1.1);
            info(String.format("Processing with %d engines", engineCount));

            String code = new CodeGenerator(recMapping).withEditPath(null).withTrace(false).toRecordMappingCode();
            MappingRunner MappingRunner = new BulkMappingRunner(recMapping, code);
            List<AssertionTest> assertionTests = AssertionTest.listFrom(recMapping.getRecDefTree().getRecDef(),
                    groovyCodeResource);

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
                        shape,
                        allowInvalid,
                        termination,
                        outputDir,
                        outputStream,
                        recDef().getNamespaceMap(),
                        reportWriter,
                        assertionTests,
                        MappingRunner);
                consumer.register(engine);
                engine.start();
            }
            info(Thread.currentThread().getName() + " about to consume");
            consumer.run();
        } catch (Exception e) {
            termination.dueToException(e);
            feedback.alert("File processing setup problem", e);
        } finally {
            // Close resources in reverse order of creation
            try {
                if (outputStream != null) {
                    outputStream.flush();
                    outputStream.close();
                    try {
                        if (termination.isIncomplete()) {
                            getDataSet().cancelProcessedOutput(getPrefix(), time);
                        } else {
                            getDataSet().finishProcessedOutput(getPrefix(), time);
                        }
                    } catch (StorageException e) {
                        feedback.alert("Error finishing processed output", e);
                    }
                }
                if (parser != null) {
                    parser.close();
                }
            } catch (IOException e) {
                feedback.alert("Error closing resources", e);
            }
        }
    }

    private File createEmptyOutputDir() throws StorageException {
        File sipDir = getDataSet().targetOutput().getParentFile();
        File outputDir = new File(sipDir, "output");
        if (!outputDir.exists()) {
            // We no longer use the output dir so don't actually create it
            //if (!outputDir.mkdir()) {
            //    throw new IllegalStateException("Unable to create directory: " + outputDir);
            //}
        } else {
            File[] files = Objects.requireNonNull(outputDir.listFiles());
            for (File file : files) {
                if (!file.delete())
                    throw new IllegalStateException("Unable to delete file: " + file);
            }
            // We no longer use the output dir so delete it
            if (!outputDir.delete())
                throw new IllegalStateException("Unable to delete dir: " + outputDir);
        }
        return outputDir;
    }

    private class MappingOutput {
        final MetadataRecord metadataRecord;
        final MappingResult mappingResult;
        final Exception exception;
        final List<String> events;

        private MappingOutput(MetadataRecord metadataRecord, MappingResult mappingResult, Exception exception,
                List<String> events) {
            this.metadataRecord = metadataRecord;
            this.mappingResult = mappingResult;
            this.exception = exception;
            this.events = events;
        }

        public boolean record(ReportWriter reportWriter, OutputStream outputStream) {
            try {
                if (exception == null) {
                    synchronized (lock) {
                        if (!events.isEmpty()) {
                            reportWriter.warn(metadataRecord, mappingResult, events, recMapping.getFacts());
                        }
                        mappingResult.toByteArrayOutputStream(recMapping.getFacts()).writeTo(outputStream);
                        return true;
                    }
                } else if (exception instanceof DiscardRecordException) {
                    synchronized (lock) {
                        reportWriter.discarded(metadataRecord, mappingResult, exception, recMapping.getFacts());
                    }
                } else if (exception instanceof MappingException) {
                    synchronized (lock) {
                        reportWriter.unexpected(metadataRecord, mappingResult, exception, recMapping.getFacts());
                    }
                    termination.dueToException(exception);
                } else {
                    synchronized (lock) {
                        reportWriter.invalid(metadataRecord, mappingResult, exception, recMapping.getFacts());
                    }
                }
            } catch (Exception e) {
                termination.dueToException(e);
                return false;
            }
            return false;
        }
    }

    private class MetadataParserRunner implements Runnable {

        final BlockingQueue<MetadataRecord> outputQueue = new LinkedBlockingDeque<>();
        final Thread thread = new Thread(this);
        final MetadataParser metadataParser;

        private MetadataParserRunner(MetadataParser metadataParser) {
            this.metadataParser = metadataParser;
            thread.setName(getClass().getName());
        }

        public MetadataRecord nextRecord() throws InterruptedException {
            return outputQueue.take();
        }

        @Override
        public void run() {
            MetadataRecord record;
            try {
                while (true) {
                    try {
                        if (!((record = metadataParser.nextRecord()) != null))
                            break;
                        outputQueue.add(record);
                        while (outputQueue.size() > 1000) {
                            Thread.sleep(1000);
                        }
                    } catch (CancelException e) {
                        termination.dueToCancellation();
                        break;
                    } catch (XMLStreamException | IOException | InterruptedException e) {
                        termination.dueToException(e);
                        break;
                    }
                }
            } finally {
                for (int i = 0; i < 100; i++) {
                    outputQueue.add(MetadataRecord.poisonPill());
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
            ITransaction transaction = Sentry.startTransaction("FileProcessor", "process");

            int recordCount = 0;
            int processedCount = 0;
            for (MappingEngine engine : engines) {
                try {
                    engine.thread.join();
                    recordCount += engine.recordCount;
                    processedCount += engine.processedCount;
                } catch (InterruptedException e) {
                    termination.dueToException(e);
                }
            }

            // Send final progress update before completion
            if (progressListener != null) {
                try {
                    progressListener.setProgress(recordCount);
                } catch (CancelException e) {
                    termination.dueToCancellation();
                }
            }

            if (termination.isIncomplete()) {
                info("Abort report writer");
                if (reportWriter != null) {
                    reportWriter.abort();
                    try {
                        getDataSet().cancelReportWriter(getPrefix(), time);
                    } catch (StorageException e) {
                        feedback.alert("Error cancelling report", e);
                    }
                }
                if (transaction != null) {
                    transaction.setStatus(SpanStatus.ABORTED);
                    transaction.finish();
                }
            } else {
                info(String.format("Finish report writer records=%d", recordCount));
                if (reportWriter != null) {
                    reportWriter.finish(recordCount, processedCount);
                    try {
                        getDataSet().finishReportWriter(getPrefix(), time);
                    } catch (StorageException e) {
                        feedback.alert("Error finishing report", e);
                    }
                }
                if (transaction != null) {
                    transaction.setStatus(SpanStatus.OK);
                    transaction.setMeasurement("records", recordCount);
                    transaction.setMeasurement("processed", processedCount);
                    transaction.finish();
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
        private final Graph shape;
        private final boolean allowInvalid;
        private final Termination termination;
        public boolean isDone;
        private final Thread thread;
        private final XmlSerializer serializer = new XmlSerializer();
        public int recordCount;
        public int processedCount;
        final File outputDir;
        final OutputStream outputStream;
        final Map<String, RecDef.Namespace> namespaceMap;
        final ReportWriter reportWriter;
        final List<AssertionTest> assertionTests;
        final MappingRunner MappingRunner;

        private MappingEngine(int index,
                MetadataParserRunner metadataParserRunner,
                Validator validator,
                Graph shape,
                boolean allowInvalid, Termination termination,
                File outputDir,
                OutputStream outputStream,
                Map<String, RecDef.Namespace> namespaceMap,
                ReportWriter reportWriter,
                List<AssertionTest> assertionTests,
                MappingRunner MappingRunner) {
            this.metadataParserRunner = metadataParserRunner;
            this.validator = validator;
            this.shape = shape;
            this.allowInvalid = allowInvalid;
            this.termination = termination;
            this.outputDir = outputDir;
            this.outputStream = outputStream;
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

        public void accept(MetadataRecord metadataRecord, MappingResult mappingResult, Exception exception,
                List<String> events) {
            //try {
            MappingOutput mappingOutput = new MappingOutput(metadataRecord, mappingResult, exception, events);
            recordCount++;
            if (rdfFormat == RDFFormat.RDFXML) {
                if (mappingOutput.record(reportWriter, outputStream)) {
                    processedCount++;
                }
            } else {
                // Only RDF/XML is supported without an output dir which is no longer used
                throw new RuntimeException("Only RDF/XML output format is currently supported");
                //File outputFile = new File(outputDir,
                //        mappingOutput.metadataRecord.getRecordNumber() + JenaHelper.getExtension(rdfFormat));
                //String output = JenaHelper.convertRDF(
                //        mappingResult.getRecDefTree().getRoot().getDefaultPrefix(), mappingResult.toRDF(),
                //        rdfFormat);
                //Files.write(outputFile.toPath(), output.getBytes(StandardCharsets.UTF_8));
            }
            //} catch (IOException e) {
            //    termination.dueToException(e);
            //}
        }

        @Override
        public void run() {
            try {
                while (termination.notYet()) {
                    MetadataRecord record = metadataParserRunner.nextRecord();

                    if (record == null || record.isPoison())
                        break;

                    List<String> events = new ArrayList<>(0);
                    MappingResult result = null;
                    try {
                        Node node = MappingRunner.runMapping(record);

                        if (node == null)
                            continue;
                        // Not sure why this previously generated a URI for localId, which was not done
                        // for RDF in the UI
                        // MappingResult result = new MappingResult(serializer,
                        // uriGenerator.generateUri(record.getId()), node,
                        // MappingRunner.getRecDefTree());
                        result = new MappingResult(serializer, record.getId(), node,
                                MappingRunner.getRecDefTree());
                        validateRDF(result, shape, events);
                        List<String> uriErrors = result.getUriErrors();
                        try {
                            if (!uriErrors.isEmpty()) {
                                StringBuilder uriErrorsString = new StringBuilder();
                                for (String uriError : uriErrors) {
                                    uriErrorsString.append(uriError).append("\n");
                                }
                                throw new Exception("URI Errors\n" + uriErrorsString);
                            }
                            if (validator == null) {
                                accept(record, result, null, events);
                            } else {
                                try {
                                    Source source = new DOMSource(node);
                                    validator.validate(source);
                                    for (AssertionTest assertionTest : assertionTests) {
                                        String violation = assertionTest.getViolation(result.root());
                                        if (violation != null)
                                            throw new AssertionException(violation);
                                    }
                                    accept(record, result, null, events);
                                } catch (Exception e) {
                                    accept(record, result, e, events);
                                    if (!allowInvalid) {
                                        termination.askHowToProceed(record, e);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            accept(record, result, e, events);
                            if (!allowInvalid) {
                                termination.askHowToProceed(record, e);
                            }
                        }

                    } catch (DiscardRecordException e) {
                        accept(record, result, e, events);
                    } catch (MappingException e) {
                        accept(record, result, e, events);
                        termination.dueToException(record, e);
                    } catch (RuntimeException e) {
                        accept(record, result, e, events);
                        if (!allowInvalid) {
                            termination.dueToException(record, e);
                        }
                    }
                }
            } catch (Exception e) {
                termination.dueToException(e);
            } finally {
                isDone = true;
            }
        }
    }

    private void validateRDF(MappingResult result, Graph shape, List<String> events) {
        String output = MappingResult.toJenaCompliantRDF(result.getRecDefTree().getRoot().getDefaultPrefix(),
                result.toRDF());
        InputStream in = new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8));
        Model model = ModelFactory.createDefaultModel();
        ErrorHandler eh = new ErrorHandler() {
            @Override
            public void warning(String message, long line, long col) {
                events.add(message);
            }

            @Override
            public void error(String message, long line, long col) {
                events.add(message);
            }

            @Override
            public void fatal(String message, long line, long col) {
                events.add(message);
            }
        };

        RDFParser.create()
            .errorHandler(eh)
            .source(in)
            .base(null)
            .lang(RDFLanguages.RDFXML)
            .parse(model);

        if (shape != null) {
            ValidationReport shaclReport = ShaclValidator.get().validate(shape, model.getGraph());
            if (!shaclReport.conforms()) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(bos, true, StandardCharsets.UTF_8);
                ShLib.printReport(ps, shaclReport);
                ps.close();
                events.add(bos.toString(StandardCharsets.UTF_8));
            }
        }
    }

    private enum NextStep {
        CONTINUE,
        INVESTIGATE,
        ABORT
    }

    private class Termination {
        private MetadataRecord failedRecord;
        private Exception exception;
        private boolean cancelled, completed;
        private NextStep nextStep = NextStep.CONTINUE;

        boolean notYet() {
            return !completed && !isIncomplete();
        }

        boolean isIncomplete() {
            return cancelled || failedRecord != null || exception != null;
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

        void dueToCancellation() {
            cancelled = true;
            listener.aborted(FileProcessor.this);
        }

        void dueToException(Exception exception) {
            dueToException(null, exception);
        }

        synchronized void dueToException(MetadataRecord failedRecord, Exception exception) {
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

        synchronized void askHowToProceed(MetadataRecord failedRecord, Exception exception) {
            switch (nextStep) {
                case CONTINUE:
                    break;
                case INVESTIGATE:
                case ABORT:
                    return;
            }
            nextStep = blockForNextStep(failedRecord.getRecordNumber());
            switch (nextStep) {
                case CONTINUE:
                    break;
                case INVESTIGATE:
                    this.exception = exception; // prevent reporting, because they chose to investigate
                    dueToException(failedRecord, exception);
                    break;
                case ABORT:
                    dueToCancellation();
                    break;
            }
        }

        private NextStep blockForNextStep(int recordNumber) {
            JRadioButton continueButton = new JRadioButton(String.format(
                    "<html><b>Continue</b> - Continue the %s mapping of data set %s, discarding invalid record %d",
                    getPrefix(), getSpec(), recordNumber));
            JRadioButton investigateButton = new JRadioButton(String.format(
                    "<html><b>Investigate</b> - Stop and fix the %s mapping of data set %s, with invalid record %d in view",
                    getPrefix(), getSpec(), recordNumber));
            ButtonGroup bg = new ButtonGroup();
            bg.add(continueButton);
            continueButton.setSelected(true);
            bg.add(investigateButton);
            if (feedback.form("Invalid Record! How to proceed?", continueButton, investigateButton)) {
                if (investigateButton.isSelected()) {
                    return NextStep.INVESTIGATE;
                } else {
                    return NextStep.CONTINUE;
                }
            } else {
                return NextStep.ABORT;
            }
        }
    }
}
