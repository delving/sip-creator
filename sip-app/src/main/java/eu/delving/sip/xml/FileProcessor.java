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
import eu.delving.groovy.*;
import eu.delving.metadata.*;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.Feedback;
import eu.delving.sip.model.ReportFileModel;
import eu.delving.sip.model.SipModel;
import eu.delving.stats.Stats;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.swing.*;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Validator;
import java.io.*;
import java.util.BitSet;
import java.util.Map;

import static eu.delving.groovy.XmlNodePrinter.toXml;

/**
 * Process an input file, mapping it to output records which are validated and used to gather statistics.
 * A validation report is produced. Output can be recorded for experts as well.  The processing is paused
 * for user input when invalid records are encountered.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class FileProcessor implements Work.DataSetPrefixWork, Work.LongTermWork {
    public static final String OUTPUT_FILE_PREF = "outputFile";
    private XmlSerializer serializer = new XmlSerializer();
    private Feedback feedback;
    private DataSet dataSet;
    private RecMapping recMapping;
    private BitSet valid;
    private PrintWriter reportWriter;
    private GroovyCodeResource groovyCodeResource;
    private ProgressListener progressListener;
    private Listener listener;
    private volatile boolean done = false;
    private boolean allowInvalid;
    private int validCount, invalidCount, recordCount, recordNumber;
    private Stats stats;
    private File outputDirectory;
    private XmlOutput xmlOutput;
    private int maxUniqueValueLength;
    private String problemXML;
    private String problemMessage;

    public interface Listener {

        void failed(FileProcessor fileProcessor);

        void aborted(FileProcessor fileProcessor);

        void succeeded(FileProcessor fileProcessor);
    }

    public FileProcessor(
            SipModel sipModel,
            int maxUniqueValueLength,
            int recordCount,
            File outputDirectory,
            GroovyCodeResource groovyCodeResource,
            Listener listener
    ) {
        sipModel.getMappingModel().setLocked(true);
        this.maxUniqueValueLength = maxUniqueValueLength;
        this.recordCount = recordCount;
        this.feedback = sipModel.getFeedback();
        this.dataSet = sipModel.getDataSetModel().getDataSet();
        this.recMapping = sipModel.getMappingModel().getRecMapping();
        this.outputDirectory = outputDirectory;
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

    public String getProblemXML() {
        return problemXML;
    }

    public String getProblemMessage() {
        return problemMessage;
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
        progressListener.setProgressMessage(String.format(
                "Mapping raw data of '%s' into '%s' format, validating and gathering statistics",
                dataSet.getSpec(), getPrefix()
        ));
    }

    @Override
    public void run() {
        valid = new BitSet(recordCount);
        stats = createStats();
        MappingRunner mappingRunner;
        MetadataParser parser;
        Validator validator;
        try {
            validator = createValidator();
            mappingRunner = new MappingRunner(groovyCodeResource, recMapping, null);
            parser = new MetadataParser(getDataSet().openSourceInputStream(), recordCount);
            reportWriter = getDataSet().openReportWriter(recMapping.getPrefix());
            if (outputDirectory != null) xmlOutput = createXmlOutput();
        }
        catch (Exception e) {
            feedback.alert("Initialization of file processor failed", e);
            return;
        }
        try {
            progressListener.prepareFor(recordCount);
            while (!done) {
                MetadataRecord record = parser.nextRecord();
                if (record == null) break;
                if (!progressListener.setProgress(record.getRecordNumber())) {
                    done = true;
                    break;
                }
                this.recordNumber = record.getRecordNumber();
                try {
                    Node node = mappingRunner.runMapping(record);
                    try {
                        Source source = new DOMSource(node);
                        validator.validate(source);
                        MappingResult result = new MappingResultImpl(serializer, node, recDefTree()).resolve();
                        result.checkMissingFields();
                        validCount++;
                        valid.set(record.getRecordNumber());
                        recordStatistics((Element) node, Path.create());
                    }
                    catch (Exception e) {
                        invalidCount++;
                        reportWriter.println(serializer.toXml(node));
                        reportWriter.println("===");
                        if (!allowInvalid) {
                            switch (askHowToProceed(record.getRecordNumber())) {
                                case ABORT:
                                    done = true;
                                    break;
                                case CONTINUE:
                                    break;
                                case IGNORE:
                                    allowInvalid = true;
                                    break;
                                case INVESTIGATE:
                                    done = true;
                                    problemXML = serializer.toXml(node);
                                    problemMessage = e.getMessage();
                                    listener.failed(this);
                                    break;
                            }
                        }
                    }
                    if (xmlOutput != null) xmlOutput.write(node);
                }
                catch (DiscardRecordException e) {
                    invalidCount++;
                    reportWriter.println("Discarded explicitly: " + e.getMessage());
                    reportWriter.println(XmlNodePrinter.toXml(record.getRootNode()));
                }
                catch (MappingException e) {
                    reportWriter.println("Mapping exception!");
                    reportWriter.println(XmlNodePrinter.toXml(e.getMetadataRecord().getRootNode()));
                    e.printStackTrace(reportWriter);
                    done = true;
                    problemXML = toXml(e.getMetadataRecord().getRootNode());
                    problemMessage = e.getMessage();
                    listener.failed(this);
                    break;
                }
            }
        }
        catch (MetadataParser.AbortException e) {
            done = true;
        }
        catch (Exception e) {
            feedback.alert("File processing problem", e);
        }
        finally {
            finishReport();
            if (xmlOutput != null) xmlOutput.finish();
            IOUtils.closeQuietly(reportWriter);
            if (done) {
                listener.aborted(this);
            }
            else {
                listener.succeeded(this);
            }
        }
    }

    private void finishReport() {
        reportWriter.println(ReportFileModel.DIVIDER);
        if (done) {
            reportWriter.println("Validation was aborted!");
        }
        else {
            reportWriter.println("Validation was completed:");
            reportWriter.println("Total Valid Records: " + validCount);
            reportWriter.println("Total Invalid Records: " + invalidCount);
            reportWriter.println("Total Records: " + (validCount + invalidCount));
        }
        reportWriter.close();
    }

    private XmlOutput createXmlOutput() throws FileNotFoundException, UnsupportedEncodingException, XMLStreamException {
        String fileName = String.format("%s-%s.xml", getDataSet().getSpec(), recMapping.getPrefix());
        File outputFile = new File(outputDirectory, fileName);
        OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(outputFile));
        return new XmlOutput(outputStream, recDef().getNamespacesMap());
    }

    private RecDef recDef() {
        return recDefTree().getRecDef();
    }

    private RecDefTree recDefTree() {
        return recMapping.getRecDefTree();
    }

    private Validator createValidator() throws MetadataException {
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
        JCheckBox investigate = new JCheckBox(String.format(
                "<html><b>Investigate</b> - Stop and fix the %s mapping of data set %s, with invalid record %d in view",
                getPrefix(), getSpec(), recordNumber
        ));
        JCheckBox ignore = new JCheckBox(
                "<html><b>Ignore</b> - Accept this record as invalid and ignore subsequent invalid records"
        );
        if (feedback.form("Continue?", investigate, ignore)) {
            if (investigate.isSelected()) {
                return NextStep.INVESTIGATE;
            }
            else if (ignore.isSelected()) {
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
}
