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
import eu.delving.sip.model.SipModel;
import eu.delving.sip.model.StatsModel;
import eu.delving.stats.Stats;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Validator;
import java.io.*;
import java.util.BitSet;

/**
 * Take the input and config informationm and produce an output xml file
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class FileProcessor implements Work {
    public static final String OUTPUT_FILE_PREF = "outputFile";
    private static final Logger LOG = Logger.getLogger(FileProcessor.class);
    private XmlSerializer serializer = new XmlSerializer();
    private SipModel sipModel;
    private Validator validator;
    private BitSet valid;
    private PrintWriter reportWriter;
    private GroovyCodeResource groovyCodeResource;
    private ProgressListener progressListener;
    private Listener listener;
    private volatile boolean aborted = false;
    private boolean allowInvalid;
    private int validCount, invalidCount;
    private Stats stats;
    private File outputDirectory;
    private XmlOutput xmlOutput;

    public interface Listener {
        void mappingFailed(MappingException exception);

        void outputInvalid(int recordNumber, Node node, String message);

        void finished(Stats stats, BitSet valid, int recordCount);
    }

    public FileProcessor(
            SipModel sipModel,
            boolean allowInvalidRecords,
            File outputDirectory,
            GroovyCodeResource groovyCodeResource,
            ProgressListener progressListener,
            Listener listener
    ) {
        this.sipModel = sipModel;
        this.allowInvalid = allowInvalidRecords;
        this.outputDirectory = outputDirectory;
        this.groovyCodeResource = groovyCodeResource;
        this.progressListener = progressListener;
        this.listener = listener;
    }

    public void run() {
        if (sipModel.getDataSetModel().isEmpty()) throw new RuntimeException("No data set selected");
        valid = new BitSet(recordCount());
        stats = createStats();
        MappingRunner mappingRunner;
        MetadataParser parser;
        try {
            validator = createValidator();
            mappingRunner = new MappingRunner(groovyCodeResource, recMapping(), null);
            parser = new MetadataParser(dataSet().openSourceInputStream(), recordCount());
            reportWriter = dataSet().openReportWriter(recMapping());
            if (outputDirectory != null) xmlOutput = createXmlOutput();
        }
        catch (Exception e) {
            sipModel.getFeedback().alert("Initialization of file processor failed", e);
            return;
        }
        try {
            progressListener.prepareFor(recordCount());
            while (!aborted) {
                MetadataRecord record = parser.nextRecord();
                if (record == null) break;
                if (!progressListener.setProgress(record.getRecordNumber())) abort();
                int recordNumber = record.getRecordNumber();
                try {
                    Node node = mappingRunner.runMapping(record);
                    validateRecord(recordNumber, node);
                    if (xmlOutput != null) xmlOutput.write(node);
                }
                catch (DiscardRecordException e) {
                    invalidCount++;
                    reportWriter.println("Discarded explicitly: "+e.getMessage());
                    reportWriter.println(XmlNodePrinter.toXml(record.getRootNode()));
                    reportWriter.println("=========");
                }
                catch (MappingException e) {
                    reportWriter.println("Mapping exception!");
                    reportWriter.println(XmlNodePrinter.toXml(e.getMetadataRecord().getRootNode()));
                    e.printStackTrace(reportWriter);
                    reportWriter.println("========");
                    abort();
                    listener.mappingFailed(e);
                }
            }
        }
        catch (MetadataParser.AbortException e) {
            sipModel.getFeedback().say("File processing aborted");
            aborted = true;
        }
        catch (Exception e) {
            sipModel.getFeedback().alert("File processing problem", e);
        }
        finally {
            finishReport();
            if (xmlOutput != null) xmlOutput.finish();
            IOUtils.closeQuietly(reportWriter);
            if (aborted) {
                listener.finished(null, null, 0);
            }
            else {
                sipModel.getFeedback().say("Finished validating");
                listener.finished(aborted ? null : stats, aborted ? null : valid, recordCount());
            }
            if (!aborted) progressListener.finished(true);
        }
    }

    private void finishReport() {
        reportWriter.println();
        if (aborted) {
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
        String fileName = String.format("%s-%s.xml", dataSet().getSpec(), recMapping().getPrefix());
        File outputFile = new File(outputDirectory, fileName);
        OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(outputFile));
        return new XmlOutput(outputStream, recDef().getNamespacesMap());
    }

    private DataSet dataSet() {
        return sipModel.getDataSetModel().getDataSet();
    }

    private RecDef recDef() {
        return recDefTree().getRecDef();
    }

    private RecDefTree recDefTree() {
        return recMapping().getRecDefTree();
    }

    private RecMapping recMapping() {
        if (!sipModel.getMappingModel().hasRecMapping()) throw new IllegalStateException("Must have record mapping");
        return sipModel.getMappingModel().getRecMapping();
    }

    private int recordCount() {
        return statsModel().getRecordCount();
    }

    private StatsModel statsModel() {
        return sipModel.getStatsModel();
    }

    private Validator createValidator() throws MetadataException {
        Validator validator = sipModel.getDataSetModel().newValidator();
        validator.setErrorHandler(null);
        return validator;
    }

    private Stats createStats() {
        Stats stats = new Stats();
        stats.setRecordRoot(recDefTree().getRoot().getPath());
        stats.prefix = recMapping().getPrefix();
        stats.name = sipModel.getDataSetFacts().get("name");
        stats.maxUniqueValueLength = statsModel().getMaxUniqueValueLength();
        return stats;
    }

    private void validateRecord(int recordNumber, Node node) {
        try {
            Source source = new DOMSource(node);
            validator.validate(source);
            MappingResult result = new MappingResultImpl(serializer, node, recDefTree()).resolve();
            result.checkMissingFields();
            validCount++;
            valid.set(recordNumber);
            recordStatistics((Element) node, Path.create());
        }
        catch (Exception e) {
            invalidCount++;
            reportWriter.println(serializer.toXml(node));
            reportWriter.println("=========");
            if (!allowInvalid) {
                abort();
                listener.outputInvalid(recordNumber, node, e.getMessage());
            }
        }
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

    private void abort() {
        aborted = true;
        progressListener.finished(false);
    }

}
