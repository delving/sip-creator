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
import eu.delving.metadata.MetadataException;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecMapping;
import eu.delving.metadata.Tag;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.SipModel;
import eu.delving.stats.Stats;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

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

public class FileProcessor implements Runnable {
    public static final String OUTPUT_FILE_PREF = "outputFile";
    private static final Logger LOG = Logger.getLogger(FileProcessor.class);
    private SipModel sipModel;
    private GroovyCodeResource groovyCodeResource;
    private ProgressListener progressListener;
    private Listener listener;
    private volatile boolean aborted = false;
    private boolean allowInvalid;
    private int validCount, invalidCount;
    private Stats stats = new Stats();
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
        BitSet valid = new BitSet(sipModel.getStatsModel().getRecordCount());
        PrintWriter reportWriter = null;
        try {
            RecMapping recMapping = sipModel.getMappingModel().getRecMapping();
            if (recMapping == null) return;
            stats.setRecordRoot(recMapping.getRecDefTree().getRoot().getPath());
            stats.prefix = recMapping.getPrefix();
            stats.name = sipModel.getDataSetFacts().get("name");
            stats.maxUniqueValueLength = sipModel.getStatsModel().getMaxUniqueValueLength();
            Validator validator = sipModel.getDataSetModel().newValidator();
            validator.setErrorHandler(null);
            MappingRunner mappingRunner = new MappingRunner(groovyCodeResource, recMapping, null);
            MetadataParser parser = new MetadataParser(
                    sipModel.getDataSetModel().getDataSet().openSourceInputStream(),
                    sipModel.getStatsModel().getRecordCount()
            );
            progressListener.prepareFor(sipModel.getStatsModel().getRecordCount());
            reportWriter = sipModel.getDataSetModel().getDataSet().openReportWriter(recMapping);
            int count = 0;
            try {
                if (outputDirectory != null) {
                    String fileName = String.format("%s-%s.xml", sipModel.getDataSetModel().getDataSet().getSpec(), recMapping.getPrefix());
                    File outputFile = new File(outputDirectory, fileName);
                    OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(outputFile));
                    xmlOutput = new XmlOutput(outputStream, recMapping.getRecDefTree().getRecDef().getNamespacesMap());
                }
                MetadataRecord record;
                while ((record = parser.nextRecord()) != null && !aborted) {
                    if (!progressListener.setProgress(count++)) abort();
                    Node outputNode = null;
                    try {
                        outputNode = mappingRunner.runMapping(record);
                        Source source = new DOMSource(outputNode);
                        validator.validate(source);
                        recordStatistics((Element) outputNode, Path.create());
                        validCount++;
                        valid.set(record.getRecordNumber());
                        if (xmlOutput != null) xmlOutput.write(outputNode);
                    }
                    catch (MappingException e) {
                        reportWriter.println("Mapping exception!");
                        reportWriter.println(XmlNodePrinter.toXml(e.getMetadataRecord().getRootNode()));
                        e.printStackTrace(reportWriter);
                        reportWriter.println("========");
                        abort();
                        listener.mappingFailed(e);
                    }
                    catch (SAXException e) {
                        invalidCount++;
                        reportWriter.println(XmlSerializer.toXml(outputNode));
                        reportWriter.println("=========");
                        if (!allowInvalid) {
                            abort();
                            listener.outputInvalid(record.getRecordNumber(), outputNode, e.getMessage());
                        }
                    }
                    catch (AssertionError e) {
                        invalidCount++;
                        reportWriter.println("Discarded explicitly:");
                        reportWriter.println(XmlNodePrinter.toXml(record.getRootNode()));
                        reportWriter.println("=========");
                    }
                    catch (Exception e) {
                        abort();
                        sipModel.getFeedback().alert("Problem writing output", e);
                        reportWriter.println("Unexpected exception!");
                        e.printStackTrace(reportWriter);
                    }
                }
                reportWriter.println();
                if (aborted) {
                    reportWriter.println("Validation was aborted!");
                }
                else {
                    reportWriter.println("Validation was completed:");
                    reportWriter.println("Total Valid Records: " + validCount);
                    reportWriter.println("Total Invalid Records: " + invalidCount);
                    reportWriter.println("Total Records: " + (validCount + invalidCount));
                    reportWriter.close();
                }
                if (xmlOutput != null) xmlOutput.finish();
            }
            catch (IOException e) {
                abort();
                sipModel.getFeedback().alert("Unable to write discarded record", e);
            }
        }
        catch (XMLStreamException e) {
            throw new RuntimeException("XML Problem", e);
        }
        catch (StorageException e) {
            throw new RuntimeException("Storage Problem", e);
        }
        catch (MetadataParser.AbortException e) {
            aborted = true;
            LOG.info("Validation aborted by user");
        }
        catch (MetadataException e) {
            aborted = true;
            LOG.info("Validation problem", e);
        }
        finally {
            IOUtils.closeQuietly(reportWriter);
            if (aborted) {
                sipModel.getFeedback().say("Validation canceled");
                listener.finished(null, null, 0);
            }
            else {
                sipModel.getFeedback().say("Finished validating");
                listener.finished(aborted ? null : stats, aborted ? null : valid, sipModel.getStatsModel().getRecordCount());
            }
            if (!aborted) progressListener.finished(true);
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
                    recordStatistics((Element)kid, path);
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
