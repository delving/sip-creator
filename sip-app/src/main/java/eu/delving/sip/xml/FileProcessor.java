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
import eu.delving.metadata.MetadataException;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import eu.delving.metadata.Tag;
import eu.delving.sip.base.CancelException;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.ReportWriter;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.Feedback;
import eu.delving.stats.Stats;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.swing.*;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

/**
 * Process an input file, mapping it to output records which are validated and used to gather statistics.
 * A validation report is produced. Output can be recorded for experts as well.  The processing is paused
 * for user input when invalid records are encountered.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class FileProcessor implements Work.DataSetPrefixWork, Work.LongTermWork {
    public static final int MAX_INVALID = 200000;
    public static final String OUTPUT_FILE_PREF = "outputFile";
    private XmlSerializer serializer = new XmlSerializer();
    private Feedback feedback;
    private DataSet dataSet;
    private RecMapping recMapping;
    private BitSet valid;
    private GroovyCodeResource groovyCodeResource;
    private ProgressListener progressListener;
    private Listener listener;
    private volatile boolean aborted = false;
    private boolean allowInvalid;
    private int validCount, invalidCount, recordCount, recordNumber;
    private Stats stats;
    private File outputDirectory;
    private PrintWriter expertOutput;
    private int maxUniqueValueLength;

    public interface Listener {

        void failed(FileProcessor fileProcessor);

        void aborted(FileProcessor fileProcessor);

        void succeeded(FileProcessor fileProcessor);
    }

    public FileProcessor(
            Feedback feedback,
            DataSet dataSet,
            RecMapping recMapping,
            int maxUniqueValueLength,
            int recordCount,
            File outputDirectory,
            boolean allowInvalid,
            GroovyCodeResource groovyCodeResource,
            Listener listener
    ) {
        this.maxUniqueValueLength = maxUniqueValueLength;
        this.recordCount = recordCount;
        this.feedback = feedback;
        this.dataSet = dataSet;
        this.recMapping = recMapping;
        this.outputDirectory = outputDirectory;
        this.allowInvalid = allowInvalid;
        this.groovyCodeResource = groovyCodeResource;
        this.listener = listener;
    }

    public String getSpec() {
        return dataSet.getSpec();
    }

    public BitSet getValid() {
        return valid;
    }

    public Stats getStats() {
        return stats;
    }

    public int getRecordNumber() {
        return recordNumber;
    }

    public int getRecordCount() {
        return recordCount;
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
        valid = new BitSet(recordCount);
        stats = createStats();
        MappingRunner mappingRunner;
        MetadataParser parser;
        Validator validator;
        List<AssertionTest> assertionTests;
        ReportWriter reportWriter;
        try {
            validator = createValidator();
            mappingRunner = new MappingRunner(groovyCodeResource, recMapping, null, false);
            parser = new MetadataParser(getDataSet().openSourceInputStream(), recordCount);
            parser.setProgressListener(new FakeProgressListener());
            assertionTests = AssertionTest.listFrom(recMapping.getRecDefTree().getRecDef(), groovyCodeResource);
            reportWriter = getDataSet().openReportWriter(recMapping.getRecDefTree().getRecDef());
            if (outputDirectory != null) expertOutput = createExpertOutput();
        }
        catch (Exception e) {
            feedback.alert("Initialization of file processor failed", e);
            return;
        }
        try {
            progressListener.prepareFor(recordCount);
            while (!aborted) {
                MetadataRecord record = parser.nextRecord();
                if (record == null) break;
                progressListener.setProgress(record.getRecordNumber());
                this.recordNumber = record.getRecordNumber();
                try {
                    Node node = mappingRunner.runMapping(record);
                    MappingResult result = new MappingResultImpl(serializer, record.getId(), node, recDefTree()).resolve();
                    try {
                        Source source = new DOMSource(node);
                        validator.validate(source);
                        result.checkMissingFields();
                        for (AssertionTest assertionTest : assertionTests) {
                            String violation = assertionTest.getViolation(result.root());
                            if (violation != null) throw new AssertionException(violation);
                        }
                        validCount++;
                        valid.set(record.getRecordNumber());
                        recordStatistics((Element) node, Path.create());
                        if (expertOutput != null) {
                            for (Map.Entry<String, List<String>> copyField : result.copyFields().entrySet()) {
                                for (String value : copyField.getValue()) {
                                    expertOutput.printf("%s = %s\n", copyField.getKey(), value);
                                }
                            }
                            expertOutput.println("==");
                        }
                    }
                    catch (Exception e) {
                        invalidCount++;
                        reportWriter.invalid(result, e);
                        if (!allowInvalid) {
                            switch (askHowToProceed(record.getRecordNumber())) {
                                case ABORT:
                                    aborted = true;
                                    break;
                                case CONTINUE:
                                    break;
                                case IGNORE:
                                    allowInvalid = true;
                                    break;
                                case INVESTIGATE:
                                    aborted = true;
                                    listener.failed(this);
                                    break;
                            }
                        }
                        else if (invalidCount >= MAX_INVALID) {
                            feedback.alert("Maximum number of invalid records reached: " + MAX_INVALID);
                            aborted = true;
                        }

                    }
                }
                catch (DiscardRecordException e) {
                    invalidCount++;
                    reportWriter.discarded(record, e.getMessage());
                }
                catch (MappingException e) {
                    reportWriter.unexpected(record, e);
                    aborted = true;
                    listener.failed(this);
                    break;
                }
            }
        }
        catch (CancelException e) {
            aborted = true;
            listener.aborted(this);
        }
        catch (Exception e) {
            aborted = true;
            feedback.alert("File processing problem", e);
        }
        finally {
            if (expertOutput != null) expertOutput.close();
            if (aborted) {
                reportWriter.abort();
                listener.aborted(this);
            }
            else {
                listener.succeeded(this);
                reportWriter.finish(validCount, invalidCount);
                String message = String.format(
                        "Total records %d, with %d valid and %d invalid.",
                        recordCount, validCount, invalidCount
                );
                feedback.alert(message);
            }
        }
    }

//    private XmlOutput createXmlOutput() throws FileNotFoundException, UnsupportedEncodingException, XMLStreamException {
//        String fileName = String.format("%s-%s.xml", getDataSet().getSpec(), recMapping.getPrefix());
//        File outputFile = new File(outputDirectory, fileName);
//        OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(outputFile));
//        return new XmlOutput(outputStream, recDef().getNamespaceMap());
//    }

    private PrintWriter createExpertOutput() throws FileNotFoundException, UnsupportedEncodingException {
        String fileName = String.format("%s-%s.txt", getDataSet().getSpec(), recMapping.getPrefix());
        File outputFile = new File(outputDirectory, fileName);
        return new PrintWriter(new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8"));
    }

    private RecDef recDef() {
        return recDefTree().getRecDef();
    }

    private RecDefTree recDefTree() {
        return recMapping.getRecDefTree();
    }

    private synchronized Validator createValidator() throws MetadataException {
        try {
            Validator validator = dataSet.newValidator(getPrefix());
            validator.setErrorHandler(null);
            return validator;
        }
        catch (StorageException e) {
            throw new MetadataException("Unable to get validator", e);
        }
    }

    private Stats createStats() {
        Stats stats = new Stats();
        stats.setRecordRoot(recDefTree().getRoot().getPath());
        stats.prefix = recMapping.getPrefix();
        Map<String, String> facts = dataSet.getDataSetFacts();
        stats.name = facts.get("name");
        stats.maxUniqueValueLength = maxUniqueValueLength;
        return stats;
    }

    private void recordStatistics(Element element, Path path) {
        String prefix = element.getPrefix();
        String name = element.getLocalName();
        String namespaceUri = element.getNamespaceURI();
        path = path.child(Tag.element(prefix, name, null));
        if (!prefix.isEmpty()) stats.recordNamespace(prefix, namespaceUri);
        stats.recordValue(path, getTextContent(element));
        NodeList childNodes = element.getChildNodes();
        for (int walk = 0; walk < childNodes.getLength(); walk++) {
            Node kid = childNodes.item(walk);
            switch (kid.getNodeType()) {
                case Node.ATTRIBUTE_NODE:
                    break;
                case Node.TEXT_NODE:
                    break;
                case Node.ELEMENT_NODE:
                    recordStatistics((Element) kid, path);
                    break;
                default:
                    throw new RuntimeException("Node type not implemented: " + kid.getNodeType());
            }
        }
    }

    private String getTextContent(Element element) {
        NodeList childNodes = element.getChildNodes();
        String text = null;
        for (int walk = 0; walk < childNodes.getLength(); walk++) {
            Node kid = childNodes.item(walk);
            if (kid.getNodeType() == Node.TEXT_NODE) {
                String content = kid.getTextContent().trim();
                if (content.isEmpty()) continue;
                if (text != null) throw new RuntimeException("Multiple text nodes??");
                text = content;
            }
        }
        return text;
    }

    private enum NextStep {
        ABORT,
        INVESTIGATE,
        CONTINUE,
        IGNORE
    }

    private NextStep askHowToProceed(int recordNumber) {
        JRadioButton continueButton = new JRadioButton(String.format(
                "<html><b>Continue</b> - Continue the %s mapping of data set %s, discarding invalid record %d",
                getPrefix(), getSpec(), recordNumber
        ));
        JRadioButton investigateButton = new JRadioButton(String.format(
                "<html><b>Investigate</b> - Stop and fix the %s mapping of data set %s, with invalid record %d in view",
                getPrefix(), getSpec(), recordNumber
        ));
        JRadioButton ignoreButton = new JRadioButton(
                "<html><b>Ignore</b> - Accept this record as invalid and ignore subsequent invalid records"
        );
        ButtonGroup bg = new ButtonGroup();
        bg.add(continueButton);
        continueButton.setSelected(true);
        bg.add(investigateButton);
        bg.add(ignoreButton);
        if (feedback.form("Invalid Record! How to proceed?", continueButton, investigateButton, ignoreButton)) {
            if (investigateButton.isSelected()) {
                return NextStep.INVESTIGATE;
            }
            else if (ignoreButton.isSelected()) {
                return NextStep.IGNORE;
            }
            else {
                return NextStep.CONTINUE;
            }
        }
        else {
            return NextStep.ABORT;
        }
    }

    private class FakeProgressListener implements ProgressListener {

        @Override
        public void setProgressMessage(String message) {
        }

        @Override
        public void prepareFor(int total) {
        }

        @Override
        public void setProgress(int progress) throws CancelException {
        }

        @Override
        public Feedback getFeedback() {
            return feedback;
        }
    }
}
