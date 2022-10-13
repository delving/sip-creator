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
import eu.delving.sip.model.SipModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RiotException;
import org.w3c.dom.Node;

import javax.swing.*;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import static eu.delving.sip.files.Storage.XSD_VALIDATION;

/**
 * Process an input file, mapping it to output records which are validated and used to gather statistics.
 * A validation report is produced. Output can be recorded for experts as well.  The processing is paused
 * for user input when invalid records are encountered.
 *
 *
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

    private void info(String message) {
        if (feedback != null) {
            feedback.info(message);
        }
        else {
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
            MetadataParser parser = new MetadataParser(getDataSet().openSourceInputStream(), -1);
            parser.setProgressListener(progressListener);
            ReportWriter reportWriter = getDataSet().openReportWriter(recMapping.getRecDefTree().getRecDef());

            File outputDir = createEmptyOutputDir();

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
        }
        catch (Exception e) {
            termination.dueToException(e);
            feedback.alert("File processing setup problem", e);
        }
    }

    private File createEmptyOutputDir() {
        File sipDir = getDataSet().targetOutput().getParentFile();
        File outputDir = new File(sipDir, "output");
        if (!outputDir.exists()) {
            if(!outputDir.mkdir()) {
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

    private class MappingOutput {
        final MetadataRecord metadataRecord;
        final MappingResult mappingResult;
        final Exception exception;

        private MappingOutput(MetadataRecord metadataRecord, MappingResult mappingResult, Exception exception) {
            this.metadataRecord = metadataRecord;
            this.mappingResult = mappingResult;
            this.exception = exception;
        }

        public void record(ReportWriter reportWriter, XmlOutput xmlOutput) {
            try {
                if (exception == null) {
                    xmlOutput.write(mappingResult.getLocalId(), mappingResult.root());
                }
                else if (exception instanceof DiscardRecordException) {
                    synchronized (lock) {
                        reportWriter.discarded(metadataRecord, exception.getMessage());
                    }
                }
                else if (exception instanceof MappingException) {
                    synchronized (lock) {
                        reportWriter.unexpected(metadataRecord, (MappingException) exception);
                    }
                    termination.dueToException(exception);
                }
                else {
                    synchronized (lock) {
                        reportWriter.invalid(mappingResult, exception);
                    }
                }
            }
            catch (Exception e) {
                termination.dueToException(e);
            }
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
                        if (!((record = metadataParser.nextRecord()) != null)) break;
                        outputQueue.add(record);
                        while(outputQueue.size() > 1000) {
                            Thread.sleep(1000);
                        }
                    } catch (XMLStreamException | IOException | CancelException | InterruptedException e) {
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
            int recordCount = 0;
            int validCount = 0;
            for (MappingEngine engine : engines) {
                try {
                    engine.thread.join();
                    recordCount += engine.recordCount;
                    validCount += engine.validCount;
                } catch (InterruptedException e) {
                    termination.dueToException(e);
                }
            }
            if (termination.isIncomplete()) {
                info("Abort report writer");
                if (reportWriter != null) {
                    reportWriter.abort();
                }
            }
            else {
                info(String.format("Finish report writer records=%d valid=%d", recordCount, validCount));
                if(reportWriter != null) {
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
        final  ReportWriter reportWriter;
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

        public void accept(MetadataRecord metadataRecord, MappingResult mappingResult, Exception exception) {
            try {
                MappingOutput mappingOutput = new MappingOutput(metadataRecord, mappingResult, exception);
                recordCount++;
                if (mappingOutput.exception == null) {
                    validCount++;
                }

                if(Application.canWritePocketFiles()) {
                    File outputFile = new File(outputDir, mappingOutput.metadataRecord.getRecordNumber() + ".xml");
                    XmlOutput xmlOutput = new XmlOutput(outputFile, namespaceMap);
                    mappingOutput.record(reportWriter, xmlOutput);
                    xmlOutput.finish(false);
                }
            } catch (XMLStreamException | IOException e) {
                termination.dueToException(e);
            }
        }

        @Override
        public void run() {
            try {
                while (termination.notYet()) {
                    MetadataRecord record = metadataParserRunner.nextRecord();

                    if (record == null || record.isPoison()) break;

                    try {
                        Node node = null;
                        try {
                            node = MappingRunner.runMapping(record);
                        } catch (DiscardRecordException e) {
                            reportWriter.discarded(record, e.toString());
                        }

                        if (node == null) continue;
                        MappingResult result = new MappingResult(serializer, uriGenerator.generateUri(record.getId()), node, MappingRunner.getRecDefTree());
                        validateRDF(record, result);
                        List<String> uriErrors = result.getUriErrors();
                        try {
                            if (!uriErrors.isEmpty()) {
                                StringBuilder uriErrorsString = new StringBuilder();
                                for (String uriError: uriErrors) {
                                    uriErrorsString.append(uriError).append("\n");
                                }
                                throw new Exception("URI Errors\n"+ uriErrorsString);
                            }
                            if (validator == null) {
                                accept(record, result, null);
                            }
                            else {
                                try {
                                    Source source = new DOMSource(node);
                                    validator.validate(source);
                                    for (AssertionTest assertionTest : assertionTests) {
                                        String violation = assertionTest.getViolation(result.root());
                                        if (violation != null) throw new AssertionException(violation);
                                    }
                                    accept(record, result, null);
                                }
                                catch (Exception e) {
                                    accept(record, result, e);
                                    if (!allowInvalid) {
                                        termination.askHowToProceed(record, e);
                                    }
                                }
                            }
                        }
                        catch (Exception e) {
                            accept(record, result, e);
                            if (!allowInvalid) {
                                termination.askHowToProceed(record, e);
                            }
                        }


                    }
                    catch (DiscardRecordException e) {
                        accept(record, null, e);
                    }
                    catch (MappingException e) {
                        accept(record, null, e);
                        termination.dueToException(record, e);
                    }
                }
            }
            catch (Exception e) {
                termination.dueToException(e);
            } finally {
                isDone = true;
            }
        }
    }

    private void validateRDF(MetadataRecord record, MappingResult result) {
        try {
            String output = MappingResult.toJenaCompliantRDF(result.toRDF());
            InputStream in = new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8));
            ModelFactory.createDefaultModel().read(in, null, "RDF/XML");
        } catch (RiotException e) {
            throw new RuntimeException("Error for record(id=" + record.getId() + ", number=" + record.getRecordNumber() + ")", e);
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
                }
                else {
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
                    getPrefix(), getSpec(), recordNumber
            ));
            JRadioButton investigateButton = new JRadioButton(String.format(
                    "<html><b>Investigate</b> - Stop and fix the %s mapping of data set %s, with invalid record %d in view",
                    getPrefix(), getSpec(), recordNumber
            ));
            ButtonGroup bg = new ButtonGroup();
            bg.add(continueButton);
            continueButton.setSelected(true);
            bg.add(investigateButton);
            if (feedback.form("Invalid Record! How to proceed?", continueButton, investigateButton)) {
                if (investigateButton.isSelected()) {
                    return NextStep.INVESTIGATE;
                }
                else {
                    return NextStep.CONTINUE;
                }
            }
            else {
                return NextStep.ABORT;
            }
        }
    }
}
