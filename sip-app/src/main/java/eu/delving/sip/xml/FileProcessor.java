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

import eu.delving.MappingResult;
import eu.delving.groovy.DiscardRecordException;
import eu.delving.groovy.GroovyCodeResource;
import eu.delving.groovy.MappingException;
import eu.delving.groovy.MappingRunner;
import eu.delving.groovy.MetadataRecord;
import eu.delving.groovy.XmlSerializer;
import eu.delving.metadata.AssertionException;
import eu.delving.metadata.AssertionTest;
import eu.delving.metadata.MappingResultImpl;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.ReportWriter;
import eu.delving.sip.model.Feedback;
import eu.delving.sip.model.SipModel;
import eu.delving.stats.Stats;
import org.w3c.dom.Node;

import javax.swing.*;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Validator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TransferQueue;

/**
 * Process an input file, mapping it to output records which are validated and used to gather statistics.
 * A validation report is produced. Output can be recorded for experts as well.  The processing is paused
 * for user input when invalid records are encountered.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class FileProcessor implements Work.DataSetPrefixWork, Work.LongTermWork {
    private SipModel sipModel;
    private DataSet dataSet;
    private RecMapping recMapping;
    private GroovyCodeResource groovyCodeResource;
    private UriGenerator uriGenerator;
    private ProgressListener progressListener;
    private boolean allowInvalid;
    private TransferQueue<MetadataRecord> recordSource = new LinkedTransferQueue<MetadataRecord>();
    private Stats stats;
    private Termination termination;

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
        this.termination = new Termination(this, sipModel.getFeedback(), listener);
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

    public int getRecordCount() {
        return sipModel.getStatsModel().getRecordCount();
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

    private static class QueueFiller implements Runnable {
        final MetadataParser parser;
        final TransferQueue<MetadataRecord> sink;
        final Termination termination;
        final GroovyCodeResource groovyCodeResource;
        private Thread thread = new Thread(this);

        private QueueFiller(MetadataParser parser, TransferQueue<MetadataRecord> sink, Termination termination, GroovyCodeResource groovyCodeResource) {
            this.parser = parser;
            this.sink = sink;
            this.termination = termination;
            this.groovyCodeResource = groovyCodeResource;
        }

        @Override
        public void run() {
            try {
                while (termination.notYet()) {
                    MetadataRecord metadataRecord = parser.nextRecord();
                    while (!sink.tryTransfer(metadataRecord, 1000, TimeUnit.MILLISECONDS)) {
                        System.out.println("retry feeding record");
                    }
                    if (metadataRecord.isPoison()) {
                        termination.normalCompletion();
                    }
                }
            }
            catch (Exception e) {
                termination.dueToException(e);
            }
        }

        public void start() {
            thread.start();
        }

    }

    private static class MappingOutput {
        final MetadataRecord metadataRecord;
        final MappingResult mappingResult;
        final Exception exception;
        final Termination termination;

        private MappingOutput(MetadataRecord metadataRecord, MappingResult mappingResult, Exception exception, Termination termination) {
            this.metadataRecord = metadataRecord;
            this.mappingResult = mappingResult;
            this.exception = exception;
            this.termination = termination;
        }

        private MappingOutput() {
            this.metadataRecord = null;
            this.mappingResult = null;
            this.exception = null;
            this.termination = null;
        }

        public boolean isPoison() {
            return termination == null;
        }

        public void record(ReportWriter reportWriter, XmlOutput xmlOutput) {
            try {
                if (exception == null) {
                    reportWriter.valid(mappingResult.getLocalId(), mappingResult);
                    xmlOutput.write(mappingResult.getLocalId(), mappingResult.rootAugmented());
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

    private static class Consumer implements Runnable {
        final BlockingQueue<MappingOutput> outputQueue = new LinkedBlockingDeque<MappingOutput>();
        final ReportWriter reportWriter;
        final XmlOutput xmlOutput;
        final Termination termination;
        private int recordCount, validCount;

        private Consumer(ReportWriter reportWriter, XmlOutput xmlOutput, Termination termination) {
            this.reportWriter = reportWriter;
            this.xmlOutput = xmlOutput;
            this.termination = termination;
        }

        public void accept(MetadataRecord metadataRecord, MappingResult mappingResult, Exception exception) {
//            timer("mapping", false);
            recordCount++;
            outputQueue.add(new MappingOutput(metadataRecord, mappingResult, exception, termination));
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
                reportWriter.abort();
            }
            else {
                reportWriter.finish(validCount, recordCount - validCount);
            }
        }
    }

    @Override
    public void run() {
        try {
            MetadataParser parser = new MetadataParser(getDataSet().openSourceInputStream(), sipModel.getStatsModel().getRecordCount());
            parser.setProgressListener(progressListener);
            ReportWriter reportWriter = getDataSet().openReportWriter(recMapping.getRecDefTree().getRecDef());
            XmlOutput xmlOutput = new XmlOutput(getDataSet().targetOutput(getPrefix()), recDef().getNamespaceMap());
            QueueFiller queueFiller = new QueueFiller(parser, recordSource, termination, groovyCodeResource);
            this.stats = createStats();
            Consumer consumer = new Consumer(reportWriter, xmlOutput, termination);
            int engineCount = Runtime.getRuntime().availableProcessors();
            for (int walk = 0; walk < engineCount; walk++) {
                Validator validator = dataSet.newValidator(recDef().prefix);
                validator.setErrorHandler(null);
                MappingRunner mappingRunner = new MappingRunner(groovyCodeResource, recMapping, null, false);
                List<AssertionTest> assertionTests = AssertionTest.listFrom(recMapping.getRecDefTree().getRecDef(), groovyCodeResource);
                MappingEngine engine = new MappingEngine(
                        walk, recordSource, mappingRunner, validator, assertionTests, consumer, allowInvalid, termination
                );
                engine.start();
            }
            queueFiller.start();
            System.out.println(Thread.currentThread().getName() + " about to consume");
            consumer.run();
        }
        catch (Exception e) {
            termination.dueToException(e);
            sipModel.getFeedback().alert("File processing setup problem", e);
        }
    }

    private class MappingEngine implements Runnable {
        private final BlockingQueue<MetadataRecord> recordSource;
        private final MappingRunner mappingRunner;
        private final Validator validator;
        private final List<AssertionTest> assertionTests;
        private final Consumer consumer;
        private final boolean allowInvalid;
        private final Termination termination;
        private Thread thread;
        private XmlSerializer serializer = new XmlSerializer();

        private MappingEngine(int index, BlockingQueue<MetadataRecord> recordSource, MappingRunner mappingRunner,
                              Validator validator, List<AssertionTest> assertionTests, Consumer consumer,
                              boolean allowInvalid, Termination termination
        ) {
            this.recordSource = recordSource;
            this.mappingRunner = mappingRunner;
            this.validator = validator;
            this.assertionTests = assertionTests;
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
                while (true) {
                    MetadataRecord record = recordSource.take();
                    if (record.isPoison()) {
                        recordSource.add(record);
                        consumer.poison();
                        return;
                    }
                    try {
//                        timer("mapping", true);
                        Node node = mappingRunner.runMapping(record);
                        MappingResult result = new MappingResultImpl(serializer, uriGenerator.generateUri(record.getId()), node, mappingRunner.getRecDefTree()).resolve();
                        try {
                            Source source = new DOMSource(node);
                            validator.validate(source);
                            result.checkMissingFields();
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
        stats.setRecordRoot(recDefTree().getRoot().getPath());
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

    private static class Termination {
        final FileProcessor fileProcessor;
        final Feedback feedback;
        final Listener listener;

        private MetadataRecord failedRecord;
        private Exception exception;
        private boolean cancelled, completed;
        private NextStep nextStep = NextStep.CONTINUE;

        private Termination(FileProcessor fileProcessor, Feedback feedback, Listener listener) {
            this.fileProcessor = fileProcessor;
            this.feedback = feedback;
            this.listener = listener;
        }

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
            listener.succeeded(fileProcessor);
        }

        void dueToCancellation() {
            cancelled = true;
            listener.aborted(fileProcessor);
        }

        void dueToException(Exception exception) {
            dueToException(null, exception);
        }

        synchronized void dueToException(MetadataRecord failedRecord, Exception exception) {
            if (this.exception == null) { // only show one of them
                feedback.alert("Problem processing", exception);
            }
            this.failedRecord = failedRecord;
            this.exception = exception;
            listener.failed(fileProcessor);
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
                    fileProcessor.getPrefix(), fileProcessor.getSpec(), recordNumber
            ));
            JRadioButton investigateButton = new JRadioButton(String.format(
                    "<html><b>Investigate</b> - Stop and fix the %s mapping of data set %s, with invalid record %d in view",
                    fileProcessor.getPrefix(), fileProcessor.getSpec(), recordNumber
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

//    static class MapStat {
//        final String name;
//        long totalIn, totalOut, count;
//        long in, out;
//        long lastAppearance;
//
//        MapStat(String name) {
//            this.name = name;
//        }
//
//        boolean shouldShow() {
//            if (lastAppearance > System.currentTimeMillis() - 1000) return false;
//            lastAppearance = System.currentTimeMillis() + (long) (Math.random() * 200);
//            return true;
//        }
//
//        public String toString() {
//            Runtime r = Runtime.getRuntime();
//            return name + "/" + count + ": " + (out - in) + ": in=" + totalIn + ": out=" + totalOut;
//        }
//
//        public void stamp(boolean goIn) {
//            if (goIn) {
//                count++;
//                in = System.currentTimeMillis();
//                if (out == 0) {
//                    out = in;
//                }
//                else {
//                    totalOut += in - out;
//                }
//            }
//            else {
//                out = System.currentTimeMillis();
//                totalIn += out - in;
//            }
//        }
//    }
//
//    static Map<String, MapStat> mapStats = new ConcurrentHashMap<String, MapStat>();
//
//    static void timer(String message, boolean goIn) {
//        String name = Thread.currentThread().getName() + ":" + message;
//        MapStat mapStat = mapStats.get(name);
//        if (mapStat == null) {
//            mapStats.put(name, mapStat = new MapStat(name));
//        }
//        mapStat.stamp(goIn);
//        if (!goIn && mapStat.shouldShow()) {
//            System.out.println(mapStat);
//        }
//    }
}
