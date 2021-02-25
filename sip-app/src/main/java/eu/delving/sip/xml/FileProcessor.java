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
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactoryConfigurationException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TransferQueue;

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
    private TransferQueue<MetadataRecord> recordSource = new LinkedTransferQueue<>();
    private Stats stats;
    private Termination termination = new Termination();

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
            XmlOutput xmlOutput = new XmlOutput(getDataSet().targetOutput(), recDef().getNamespaceMap());
            this.stats = createStats();
            Consumer consumer = new Consumer(reportWriter, xmlOutput);
            QueueFiller queueFiller = new QueueFiller(parser, recordSource, consumer);
            int engineCount = Runtime.getRuntime().availableProcessors();
            info(String.format("Processing with %d engines", engineCount));
            for (int walk = 0; walk < Math.min(engineCount, 1); walk++) {
                boolean enableXSDValidation = sipModel.getPreferences().getProperty(XSD_VALIDATION, "false").contentEquals("true");
                Validator validator = null;
                if (enableXSDValidation) {
                    validator = dataSet.newValidator();
                    validator.setErrorHandler(null);
                }
                MappingEngine engine = new MappingEngine(
                        walk, recordSource, validator, consumer, allowInvalid, termination
                );
                engine.start();
            }
            queueFiller.start();
            info(Thread.currentThread().getName() + " about to consume");
            consumer.run();
        }
        catch (Exception e) {
            termination.dueToException(e);
            sipModel.getFeedback().alert("File processing setup problem", e);
        }
    }

//    public void runHeadless(boolean enableXSDValidation) {
//        groovyCodeResource.clearMappingScripts();
//        try {
//            MetadataParser parser = new MetadataParser(getDataSet().openSourceInputStream(), 0);
//            ReportWriter reportWriter = getDataSet().openReportWriter(recMapping.getRecDefTree().getRecDef());
//            XmlOutput xmlOutput = new XmlOutput(getDataSet().targetOutput(), recDef().getNamespaceMap());
//            // this.stats = createStats();
//            Consumer consumer = new Consumer(reportWriter, xmlOutput);
//            QueueFiller queueFiller = new QueueFiller(parser, recordSource, consumer);
//            int engineCount = Runtime.getRuntime().availableProcessors();
//            System.out.println(String.format("Processing with %d engines", engineCount));
//            for (int walk = 0; walk < engineCount; walk++) {
//                Validator validator = null;
//                if (enableXSDValidation) {
//                    validator = dataSet.newValidator();
//                    validator.setErrorHandler(null);
//                }
//                MappingRunner MappingRunner = new AppMappingRunner(groovyCodeResource, recMapping, null, false);
//                List<AssertionTest> assertionTests = AssertionTest.listFrom(recMapping.getRecDefTree().getRecDef(), groovyCodeResource);
//                MappingEngine engine = new MappingEngine(
//                    walk, recordSource, MappingRunner, validator, assertionTests, consumer, allowInvalid, termination
//                );
//                engine.start();
//            }
//            queueFiller.start();
//            System.out.println(Thread.currentThread().getName() + " about to consume");
//            consumer.run();
//        }
//        catch (Exception e) {
//            termination.dueToException(e);
//            info("File processing setup problem: " + e);
//        }
//    }

    private class QueueFiller implements Runnable {
        final MetadataParser parser;
        final TransferQueue<MetadataRecord> sink;
        private final Consumer consumer;
        private Thread thread = new Thread(this);

        private QueueFiller(MetadataParser parser, TransferQueue<MetadataRecord> sink, Consumer consumer) {
            this.parser = parser;
            this.sink = sink;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            try {
                while (termination.notYet()) {
                    MetadataRecord metadataRecord = parser.nextRecord();
                    while (!sink.tryTransfer(metadataRecord, 1000, TimeUnit.MILLISECONDS)) {
                        System.out.println("retry feeding record");
                        if (!termination.notYet()) break;
                    }
                }
                System.out.println("Queue filler done");
                consumer.poison();
            }
            catch (Exception e) {
                termination.dueToException(e);
            }
        }

        public void start() {
            info("Queue filler started");
            thread.start();
        }

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

        private MappingOutput() {
            this.metadataRecord = null;
            this.mappingResult = null;
            this.exception = null;
        }

        public boolean isPoison() {
            return metadataRecord == null;
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
        final BlockingQueue<MappingOutput> outputQueue = new LinkedBlockingDeque<MappingOutput>();
        final ReportWriter reportWriter;
        final XmlOutput xmlOutput;
        private int recordCount, validCount;

        private Consumer(ReportWriter reportWriter, XmlOutput xmlOutput) {
            this.reportWriter = reportWriter;
            this.xmlOutput = xmlOutput;
        }

        public void accept(MetadataRecord metadataRecord, MappingResult mappingResult, Exception exception) {
            outputQueue.add(new MappingOutput(metadataRecord, mappingResult, exception));
        }

        public void poison() {
            outputQueue.add(new MappingOutput());
        }

        @Override
        public void run() {
            try {
                while (true) {
                    MappingOutput mappingOutput = outputQueue.take();
                    if (mappingOutput.isPoison()) {
                        outputQueue.add(mappingOutput);
                        info("Consumer stopping");
                        break;
                    }
                    recordCount++;
                    if (mappingOutput.exception == null) {
                        validCount++;
                    }
                    mappingOutput.record(reportWriter, xmlOutput);
                }
            }
            catch (InterruptedException e) {
                termination.dueToException(e);
            }
            xmlOutput.finish(termination.isIncomplete());
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
    }

    private class MappingEngine implements Runnable {
        private final BlockingQueue<MetadataRecord> recordSource;
        private final Validator validator;
        private final Consumer consumer;
        private final boolean allowInvalid;
        private final Termination termination;
        private Thread thread;
        private XmlSerializer serializer = new XmlSerializer();

        private MappingEngine(int index, BlockingQueue<MetadataRecord> recordSource,
                              Validator validator, Consumer consumer,
                              boolean allowInvalid, Termination termination
        ) {
            this.recordSource = recordSource;
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
            MappingRunner MappingRunner = new AppMappingRunner(groovyCodeResource, recMapping, null, false);
            List<AssertionTest> assertionTests = null;
            try {
                assertionTests = AssertionTest.listFrom(recMapping.getRecDefTree().getRecDef(), groovyCodeResource);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            try {
                while (true) {
                    MetadataRecord record = recordSource.take();
                    if (record.isPoison()) {
                        recordSource.add(record);
                        consumer.poison();
                        return;
                    }
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
