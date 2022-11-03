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
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RiotException;
import org.w3c.dom.Node;

import javax.swing.*;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Validator;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

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
    private final List<List<String>> hashes = new CopyOnWriteArrayList<>();
    private final Map<String, List<String>> previousHashes = new HashMap<>();

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

    private String getStatus(String hubID, String inputHash, String outputHash) {
        String status = "new";
        List<String> prev = previousHashes.get(hubID);
        if (prev != null) {
            String prevInputHash = prev.get(1);
            String prevOutputHash = prev.get(2);
            if (!inputHash.equals(prevInputHash)) {
                status = "input_modified";
            } else if (!outputHash.equals(prevOutputHash)) {
                status = "output_modified";
            } else {
                status = "identical";
            }
        }
        return status;
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
            java.nio.file.Path inputHashes = new File(dataSet.getSipFile(), "hash.csv").toPath();
            if (Files.exists(inputHashes)) {
                for (String line : Files.readAllLines(inputHashes)) {
                    String[] parts = line.split(",");
                    previousHashes.put(parts[0], Arrays.asList(parts[0], parts[1], parts[2], parts[3], parts[4]));
                }
            }


            Consumer consumer = new Consumer(reportWriter);
            int engineCount = (int) Math.round(Runtime.getRuntime().availableProcessors() * 1.1);
            info(String.format("Processing with %d engines", engineCount));

            String code = new CodeGenerator(recMapping).withEditPath(null).withTrace(false).toRecordMappingCode();
            MappingRunner MappingRunner = new BulkMappingRunner(recMapping, code);
            List<AssertionTest> assertionTests = AssertionTest.listFrom(recMapping.getRecDefTree().getRecDef(), groovyCodeResource);

            PocketWriter pocketWriter = new PocketWriter(outputDir, recDef().getNamespaceMap(), reportWriter);

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
                    MappingRunner,
                    pocketWriter);
                consumer.register(engine);
                engine.start();
            }
            info(Thread.currentThread().getName() + " about to consume");
            consumer.run();

            String now = Long.toString(System.currentTimeMillis());

            File hashesFile = new File(dataSet.getSipFile(), "hash.csv");
            try(Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(hashesFile)))) {

                Set<String> hubIdentifiers = new HashSet<>();
                for (List<String> hashLine : hashes) {
                    String hubID = hashLine.get(0);
                    String inputHash = hashLine.get(1);
                    String outputHash = hashLine.get(2);
                    String modified = now;

                    String status = getStatus(hubID, inputHash, outputHash);
                    if (status.equals("identical")) {
                        List<String> prev = previousHashes.get(hubID);
                        modified = prev.get(3);
                    }

                    hubIdentifiers.add(hubID);

                    String line = String.format("%s,%s,%s,%s,%s", hubID, inputHash, outputHash, modified, status);
                    writer.write(line);
                    writer.write('\n');
                }

                for(Map.Entry<String, List<String>> entry : previousHashes.entrySet()) {
                    if(!hubIdentifiers.contains(entry.getKey())) {
                        List<String> prev = entry.getValue();
                        String hubID = prev.get(0);
                        String inputHash = prev.get(1);
                        String outputHash = prev.get(2);
                        String deleted = Long.toString(System.currentTimeMillis());
                        String status = "deleted";

                        String line = String.format("%s,%s,%s,%s,%s", hubID, inputHash, outputHash, deleted, status);
                        writer.write(line);
                        writer.write('\n');
                    }
                }

                writer.flush();
            }
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
        }
//        else {
//            File[] files = Objects.requireNonNull(outputDir.listFiles());
//            for (File file : files) {
//                if (!file.delete()) throw new IllegalStateException("Unable to delete file: " + file);
//            }
//        }
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

    private class PocketWriter implements Runnable {

        private final File outputDir;
        private final BlockingQueue<MappingOutput> queue = new LinkedBlockingQueue<>();
        private final Map<String, RecDef.Namespace> namespaceMap;
        private final ReportWriter reportWriter;

        private PocketWriter(File outputDir, Map<String, RecDef.Namespace> namespaceMap, ReportWriter reportWriter) {
            this.outputDir = outputDir;
            this.namespaceMap = namespaceMap;
            this.reportWriter = reportWriter;
        }

        @Override
        public void run() {
            File outputFile = new File(outputDir, "output.xml");

            MappingOutput mappingOutput;
            try {
                XmlOutput xmlOutput = new XmlOutput(outputFile, namespaceMap);
                while ((mappingOutput = queue.take()) != null) {
                    mappingOutput.record(reportWriter, xmlOutput);
                }
                xmlOutput.finish(false);
                Files.copy(outputFile.toPath(), outputFile.getParentFile().toPath().resolve(System.currentTimeMillis() + ".xml"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private class MappingEngine implements Runnable {
        private final MetadataParserRunner metadataParserRunner;
        private final ArrayList localHashes = new ArrayList<List<String>>();
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
        final PocketWriter pocketWriter;

        private MappingEngine(int index,
                              MetadataParserRunner metadataParserRunner,
                              Validator validator,
                              boolean allowInvalid, Termination termination,
                              File outputDir,
                              Map<String, RecDef.Namespace> namespaceMap,
                              ReportWriter reportWriter,
                              List<AssertionTest> assertionTests,
                              MappingRunner MappingRunner,
                              PocketWriter pocketWriter) {
            this.metadataParserRunner = metadataParserRunner;
            this.validator = validator;
            this.allowInvalid = allowInvalid;
            this.termination = termination;
            this.outputDir = outputDir;
            this.namespaceMap = namespaceMap;
            this.reportWriter = reportWriter;
            this.assertionTests = assertionTests;
            this.MappingRunner = MappingRunner;
            this.pocketWriter = pocketWriter;
            this.thread = new Thread(this);
            this.thread.setName("MappingEngine" + index);
        }

        public void start() {
            thread.start();
        }

        public void accept(MetadataRecord metadataRecord, MappingResult mappingResult, Exception exception) {
            MappingOutput mappingOutput = new MappingOutput(metadataRecord, mappingResult, exception);
            recordCount++;
            if (mappingOutput.exception == null) {
                validCount++;

                String orgID = dataSet.getDataSetFacts().get("orgId");
                String hubID = String.format("%s_%s_%s", orgID, dataSet.getSpec(), metadataRecord.getId());
                String inputHash = metadataRecord.sha256();
                String outputHash = mappingResult.sha256();

                List<String> line = Arrays.asList(hubID, inputHash, outputHash);
                localHashes.add(line);

                String status = getStatus(hubID, inputHash, outputHash);
                if (!status.equals("identical") && Application.canWritePocketFiles()) {
                    pocketWriter.queue.add(mappingOutput);
                }
            }

//                if(Application.canWritePocketFiles()) {
//                    File outputFile = new File(outputDir, mappingOutput.metadataRecord.getRecordNumber() + ".xml");
//                    XmlOutput xmlOutput = new XmlOutput(outputFile, namespaceMap);
//                    mappingOutput.record(reportWriter, xmlOutput);
//                    xmlOutput.finish(false);
//                }
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
                hashes.addAll(localHashes);
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
