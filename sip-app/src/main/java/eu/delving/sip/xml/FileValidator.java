/*
 * Copyright 2011 DELVING BV
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
import eu.delving.metadata.RecMapping;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.SipModel;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.BitSet;
import java.util.Set;

/**
 * Take the input and config informationm and produce an output xml file
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class FileValidator implements Runnable {

    private static final Logger LOG = Logger.getLogger(FileValidator.class);
    private SipModel sipModel;
    private GroovyCodeResource groovyCodeResource;
    private ProgressListener progressListener;
    private Listener listener;
    private volatile boolean aborted = false;
    private boolean allowInvalid;
    private int validCount, invalidCount;

    public interface Listener {
        void mappingFailed(MappingException exception);

        void outputInvalid(int recordNumber, Node node, String message);

        void finished(BitSet valid, int recordCount);
    }

    public FileValidator(
            SipModel sipModel,
            boolean allowInvalidRecords,
            GroovyCodeResource groovyCodeResource,
            ProgressListener progressListener,
            Listener listener
    ) {
        this.sipModel = sipModel;
        this.allowInvalid = allowInvalidRecords;
        this.groovyCodeResource = groovyCodeResource;
        this.progressListener = progressListener;
        this.listener = listener;
    }

    public void run() {
        if (!sipModel.hasDataSet() || !sipModel.hasPrefix()) {
            throw new RuntimeException("No data set selected");
        }
        Uniqueness uniqueness = new Uniqueness();
        BitSet valid = new BitSet(sipModel.getStatsModel().getRecordCount());
        PrintWriter out = null;
        try {
            RecMapping recMapping = sipModel.getMappingModel().getRecMapping();
            if (recMapping == null) return;
            Validator validator = sipModel.getDataSetModel().newValidator(recMapping.getPrefix());
            validator.setErrorHandler(null);
            MappingRunner mappingRunner = new MappingRunner(groovyCodeResource, recMapping, null);
            MetadataParser parser = new MetadataParser(
                    sipModel.getDataSetModel().getDataSet().openSourceInputStream(),
                    sipModel.getStatsModel().getRecordCount()
            );
            progressListener.prepareFor(sipModel.getStatsModel().getRecordCount());
            out = sipModel.getDataSetModel().getDataSet().openReportWriter(recMapping);
            int count = 0;
            try {
                MetadataRecord record;
                while ((record = parser.nextRecord()) != null && !aborted) {
                    if (!progressListener.setProgress(count++)) abort();
                    Node outputNode = null;
                    try {
                        outputNode = mappingRunner.runMapping(record);
                        Source source = new DOMSource(outputNode);
                        validator.validate(source);
                        validCount++;
                        valid.set(record.getRecordNumber());
                    }
                    catch (MappingException e) {
                        out.println("Mapping exception!");
                        out.println(XmlSerializer.toXml(e.getMetadataRecord().getRootNode()));
                        e.printStackTrace(out);
                        out.println("========");
                        abort();
                        listener.mappingFailed(e);
                    }
                    catch (SAXException e) {
                        invalidCount++;
                        out.println(XmlSerializer.toXml(outputNode));
                        out.println("=========");
                        if (!allowInvalid) {
                            abort();
                            listener.outputInvalid(record.getRecordNumber(), outputNode, e.getMessage());
                        }
                    }
                    catch (AssertionError e) {
                        invalidCount++;
                        out.println("Discarded explicitly:");
                        out.println(XmlSerializer.toXml(record.getRootNode()));
                        out.println("=========");
                    }
                    catch (Exception e) {
                        abort();
                        sipModel.getFeedback().alert("Problem writing output", e);
                        out.println("Unexpected exception!");
                        e.printStackTrace(out);
                    }
                }
                Set<String> repeated = uniqueness.getRepeated();
                if (!repeated.isEmpty()) {
                    out.println("There were non-unique identifiers:");
                    for (String line : repeated) {
                        out.println(line);
                    }
                    sipModel.getFeedback().alert("Identifier should be unique, but there were %d repeated values");
                }
                out.println();
                if (aborted) {
                    out.println("Validation was aborted!");
                }
                else {
                    out.println("Validation was completed:");
                    out.println("Total Valid Records: " + validCount);
                    out.println("Total Invalid Records: " + invalidCount);
                    out.println("Total Records: " + (validCount + invalidCount));
                    out.close();
                }
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
            IOUtils.closeQuietly(out);
            if (aborted) {
                sipModel.getFeedback().say("Validation canceled");
                listener.finished(null, 0);
            }
            else {
                sipModel.getFeedback().say("Finished validating");
                listener.finished(aborted ? null : valid, sipModel.getStatsModel().getRecordCount());
            }
            uniqueness.destroy();
            if (!aborted) progressListener.finished(true);
        }
    }

    private void abort() {
        aborted = true;
        progressListener.finished(false);
    }
}
