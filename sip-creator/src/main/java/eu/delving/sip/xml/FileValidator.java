/*
 * Copyright 2007 EDL FOUNDATION
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.sip.xml;

import eu.delving.groovy.DiscardRecordException;
import eu.delving.groovy.GroovyCodeResource;
import eu.delving.groovy.MappingException;
import eu.delving.groovy.MappingRunner;
import eu.delving.groovy.MetadataRecord;
import eu.delving.metadata.RecordMapping;
import eu.delving.metadata.RecordValidator;
import eu.delving.metadata.Uniqueness;
import eu.delving.metadata.ValidationException;
import eu.delving.sip.ProgressListener;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.SipModel;
import groovy.util.Node;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.BitSet;
import java.util.Set;

/**
 * Take the input and config informationm and produce an output xml file
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class FileValidator implements Runnable {
    private SipModel sipModel;
    private GroovyCodeResource groovyCodeResource;
    private ProgressAdapter progressAdapter;
    private Listener listener;
    private volatile boolean aborted = false;
    private boolean allowInvalid;
    private int validCount, invalidCount;

    public interface Listener {
        void invalidInput(MappingException exception);

        void invalidOutput(ValidationException exception);

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
        this.progressAdapter = new ProgressAdapter(progressListener);
        this.listener = listener;
    }

    public void run() {
        if (!sipModel.hasDataSet()) {
            throw new RuntimeException("No data set selected");
        }
        DataSet dataSet = sipModel.getDataSetModel().getDataSet();
        Uniqueness uniqueness = new Uniqueness();
        RecordValidator recordValidator = new RecordValidator(groovyCodeResource, sipModel.getRecordDefinition());
        recordValidator.guardUniqueness(uniqueness);
        BitSet valid = new BitSet(sipModel.getAnalysisModel().getRecordCount());
        try {
            RecordMapping recordMapping = sipModel.getMappingModel().getRecordMapping();
            if (recordMapping == null) {
                return;
            }
            MappingRunner mappingRunner = new MappingRunner(
                    groovyCodeResource,
                    recordMapping.toCompileCode(sipModel.getDataSetModel())
            );
            MetadataParser parser = new MetadataParser(
                    sipModel.getDataSetModel().getDataSet().sourceInput(),
                    sipModel.getAnalysisModel().getRecordCount()
            );
            parser.setProgressListener(progressAdapter);
            PrintWriter out = dataSet.reportWriter(recordMapping);
            try {
                MetadataRecord record;
                while ((record = parser.nextRecord()) != null && !aborted) {
                    try {
                        Node outputNode = mappingRunner.runMapping(record);
                        recordValidator.validateRecord(outputNode, record.getRecordNumber());
                        validCount++;
                        valid.set(record.getRecordNumber());
                    }
                    catch (MappingException e) {
                        out.println("Mapping exception!");
                        out.println(record.toString());
                        e.printStackTrace(out);
                        out.println("========");
                        listener.invalidInput(e);
                        abort();
                    }
                    catch (ValidationException e) {
                        invalidCount++;
                        out.println(record.toString());
                        out.println("=========");
                        if (!allowInvalid) {
                            listener.invalidOutput(e);
                            abort();
                        }
                    }
                    catch (DiscardRecordException e) {
                        invalidCount++;
                        out.println("Discarded explicitly: \n" + record.toString());
                        out.println("=========");
                    }
                    catch (Exception e) {
                        sipModel.getUserNotifier().tellUser("Problem writing output", e);
                        out.println("Unexpected exception!");
                        e.printStackTrace(out);
                        abort();
                    }
                }
                Set<String> repeated = uniqueness.getRepeated();
                if (!repeated.isEmpty()) {
                    out.println("There were non-unique identifiers:");
                    for (String line : repeated) {
                        out.println(line);
                    }
                    sipModel.getUserNotifier().tellUser("Identifier should be unique, but there were %d repeated values");
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
                sipModel.getUserNotifier().tellUser("Unable to write discarded record", e);
                abort();
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
        }
        finally {
            listener.finished(aborted ? null : valid, sipModel.getAnalysisModel().getRecordCount());
            uniqueness.destroy();
            if (aborted) { // aborted, so metadataparser will not call finished()
                progressAdapter.finished(false);
            }
        }
    }

    private void abort() {
        aborted = true;
    }

//    private void write(Node outputNode) {
//        StringWriter writer = new StringWriter();
//        XmlNodePrinter xmlNodePrinter = new XmlNodePrinter(new PrintWriter(writer));
//        xmlNodePrinter.print(outputNode);
//    }

    // just so we receive the cancel signal
    private class ProgressAdapter implements ProgressListener {
        private ProgressListener progressListener;

        private ProgressAdapter(ProgressListener progressListener) {
            this.progressListener = progressListener;
        }

        @Override
        public void prepareFor(int total) {
            progressListener.prepareFor(total);
        }

        @Override
        public boolean setProgress(int progress) {
            boolean proceed = progressListener.setProgress(progress);
            if (!proceed) {
                abort();
            }
            return !aborted && proceed;
        }

        @Override
        public void finished(boolean success) {
            progressListener.finished(success);
        }
    }
}