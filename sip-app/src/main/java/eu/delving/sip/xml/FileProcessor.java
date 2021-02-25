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

import eu.delving.groovy.DiscardRecordException;
import eu.delving.groovy.GroovyCodeResource;
import eu.delving.groovy.MappingRunner;
import eu.delving.groovy.MappingException;
import eu.delving.groovy.AppMappingRunner;
import eu.delving.groovy.MetadataRecord;
import eu.delving.groovy.XmlSerializer;
import eu.delving.metadata.AssertionException;
import eu.delving.metadata.AssertionTest;
import eu.delving.metadata.MappingResult;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.ReportWriter;
import eu.delving.sip.model.SipModel;
import eu.delving.stats.Stats;
import org.w3c.dom.Node;

import javax.swing.*;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.IOException;
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
    private final SipModel sipModel;
    private final DataSet dataSet;
    private final RecMapping recMapping;
    private final GroovyCodeResource groovyCodeResource;
    private final UriGenerator uriGenerator;
    private final boolean allowInvalid;
    private final Listener listener;
    private ProgressListener progressListener;
    private Stats stats;
    private final Termination termination = new Termination();

    private void info(String message) {
        if (sipModel != null) {
            sipModel.getFeedback().info(message);
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
            SipModel sipModel,
            DataSet dataSet,
            RecMapping recMapping,
            boolean allowInvalid,
            GroovyCodeResource groovyCodeResource,
            UriGenerator uriGenerator,
            Listener listener
    ) {
        this.sipModel = sipModel;
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

    public Stats getStats() {
        return stats;
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
            MetadataParser parser = new MetadataParser(getDataSet().openSourceInputStream(), sipModel.getStatsModel().getRecordCount());
            parser.setProgressListener(progressListener);
            ReportWriter reportWriter = getDataSet().openReportWriter(recMapping.getRecDefTree().getRecDef());

            File outputDir = createEmptyOutputDir();

            this.stats = createStats();
            Consumer consumer = new Consumer(reportWriter, outputDir, recDef().getNamespaceMap());
            int engineCount = Math.max(Runtime.getRuntime().availableProcessors() - 2, 4);
            info(String.format("Processing with %d engines", engineCount));
            for (int walk = 0; walk < 1; walk++) {
                boolean enableXSDValidation = sipModel.getPreferences().getProperty(XSD_VALIDATION, "false").contentEquals("true");
                Validator validator = null;
                if (enableXSDValidation) {
                    validator = dataSet.newValidator();
                    validator.setErrorHandler(null);
                }
                MappingEngine engine = new MappingEngine(
                        walk, parser, validator, consumer, allowInvalid, termination
                );
                consumer.register(engine);
                engine.start();
            }
            info(Thread.currentThread().getName() + " about to consume");
            consumer.run();
        }
        catch (Exception e) {
            termination.dueToException(e);
            sipModel.getFeedback().alert("File processing setup problem", e);
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
                    reportWriter.discarded(metadataRecord, exception.getMessage());
                }
                else if (exception instanceof MappingException) {
                    reportWriter.unexpected(metadataRecord, (MappingException) exception);
                    termination.dueToException(exception);
                }
                else {
                    reportWriter.invalid(mappingResult, exception);
                }
            }
            catch (Exception e) {
                termination.dueToException(e);
            }
        }
    }

    private class Consumer implements Runnable {
        final List<MappingEngine> engines = new ArrayList<>();
        final BlockingQueue<MappingOutput> outputQueue = new LinkedBlockingDeque<>();
        final ReportWriter reportWriter;
        final File outputDir;
        final Map<String, RecDef.Namespace> namespaceMap;
        private int recordCount, validCount;

        private Consumer(ReportWriter reportWriter, File outputDir, Map<String, RecDef.Namespace> namespaceMap) {
            this.reportWriter = reportWriter;
            this.outputDir = outputDir;
            this.namespaceMap = namespaceMap;
        }

        public void accept(MetadataRecord metadataRecord, MappingResult mappingResult, Exception exception) {
            outputQueue.add(new MappingOutput(metadataRecord, mappingResult, exception));
        }

        public void poison(MetadataRecord record) {
            outputQueue.add(new MappingOutput(record, null, null));
        }

        private boolean mappingNotDone() {
            return !engines.stream().allMatch(engine -> engine.isDone);
        }

        @Override
        public void run() {
            try {
                while (mappingNotDone() && termination.notYet()) {
                    MappingOutput mappingOutput = outputQueue.take();
                    recordCount++;
                    if (mappingOutput.exception == null) {
                        validCount++;
                    }


                    File outputFile = new File(outputDir, mappingOutput.metadataRecord.getRecordNumber() + ".xml");
                    XmlOutput xmlOutput = new XmlOutput(outputFile, namespaceMap);
                    mappingOutput.record(reportWriter, xmlOutput);
                    xmlOutput.finish(false);
                }
            }
            catch (InterruptedException | XMLStreamException | IOException e) {
                termination.dueToException(e);
            }
            if (termination.isIncomplete()) {
                info("Abort report writer");
                reportWriter.abort();
            }
            else {
                info(String.format("Finish report writer records=%d valid=%d", recordCount, validCount));
                reportWriter.finish(validCount, recordCount - validCount);
                termination.normalCompletion();
            }
        }

        public void register(MappingEngine engine) {
            engines.add(engine);
        }
    }

    private class MappingEngine implements Runnable {
        private final MetadataParser metadataParser;
        private final Validator validator;
        private final Consumer consumer;
        private final boolean allowInvalid;
        private final Termination termination;
        public boolean isDone;
        private final Thread thread;
        private final XmlSerializer serializer = new XmlSerializer();

        private MappingEngine(int index, MetadataParser metadataParser,
                              Validator validator, Consumer consumer,
                              boolean allowInvalid, Termination termination
        ) {
            this.metadataParser = metadataParser;
            this.validator = validator;
            this.consumer = consumer;
            this.allowInvalid = allowInvalid;
            this.termination = termination;
            this.thread = new Thread(this);
            this.thread.setName("MappingEngine" + index);
        }

        public void start() {
            thread.start();
        }

        @Override
        public void run() {
            try {
                MappingRunner MappingRunner = new AppMappingRunner(groovyCodeResource, recMapping, null, false);
                List<AssertionTest> assertionTests = AssertionTest.listFrom(recMapping.getRecDefTree().getRecDef(), groovyCodeResource);

                while (true) {
                    MetadataRecord record = metadataParser.nextRecord();
                    if (record == null) break;
                    try {
                        Node node = MappingRunner.runMapping(record);
                        MappingResult result = new MappingResult(serializer, uriGenerator.generateUri(record.getId()), node, MappingRunner.getRecDefTree());
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
                                consumer.accept(record, result, null);
                            }
                            else {
                                try {
                                    Source source = new DOMSource(node);
                                    validator.validate(source);
                                    for (AssertionTest assertionTest : assertionTests) {
                                        String violation = assertionTest.getViolation(result.root());
                                        if (violation != null) throw new AssertionException(violation);
                                    }
                                    consumer.accept(record, result, null);
                                }
                                catch (Exception e) {
                                    consumer.accept(record, result, e);
                                    if (!allowInvalid) {
                                        termination.askHowToProceed(record, e);
                                    }
                                }
                            }
                        }
                        catch (Exception e) {
                            consumer.accept(record, result, e);
                            if (!allowInvalid) {
                                termination.askHowToProceed(record, e);
                            }
                        }


                    }
                    catch (DiscardRecordException e) {
                        consumer.accept(record, null, e);
                    }
                    catch (MappingException e) {
                        consumer.accept(record, null, e);
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

    private Stats createStats() {
        Stats stats = new Stats();
        stats.freshStats();
        stats.prefix = recMapping.getPrefix();
        Map<String, String> facts = dataSet.getDataSetFacts();
        stats.name = facts.get("name");
        stats.maxUniqueValueLength = sipModel.getStatsModel().getMaxUniqueValueLength();
        return stats;
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
                if (sipModel != null) {
                    sipModel.getFeedback().alert("Problem processing", exception);
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
            if (sipModel.getFeedback().form("Invalid Record! How to proceed?", continueButton, investigateButton)) {
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
