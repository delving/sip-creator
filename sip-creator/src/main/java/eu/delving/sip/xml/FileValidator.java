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
import eu.delving.groovy.XmlNodePrinter;
import eu.delving.metadata.RecordMapping;
import eu.delving.metadata.RecordValidator;
import eu.delving.metadata.Uniqueness;
import eu.delving.metadata.ValidationException;
import eu.delving.sip.ProgressListener;
import eu.delving.sip.files.FileStore;
import eu.delving.sip.files.FileStoreException;
import eu.delving.sip.model.SipModel;
import groovy.util.Node;

import javax.xml.stream.XMLStreamException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
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
    private boolean saveInvalidRecords;
    private int validCount, invalidCount;

    public interface Listener {
        void invalidInput(MappingException exception);

        void invalidOutput(ValidationException exception);

        void finished(boolean aborted, int validCount, int invalidCount);
    }

    public FileValidator(
            SipModel sipModel,
            boolean saveInvalidRecords,
            GroovyCodeResource groovyCodeResource,
            ProgressListener progressListener,
            Listener listener
    ) {
        this.sipModel = sipModel;
        this.saveInvalidRecords = saveInvalidRecords;
        this.groovyCodeResource = groovyCodeResource;
        this.progressAdapter = new ProgressAdapter(progressListener);
        this.listener = listener;
    }

    public void run() {
        FileStore.DataSetStore dataSetStore = sipModel.getDataSetStore();
        if (dataSetStore == null) {
            throw new RuntimeException("No data set store selected");
        }
        Uniqueness uniqueness = new Uniqueness();
        RecordValidator recordValidator = new RecordValidator(groovyCodeResource, sipModel.getRecordDefinition());
        recordValidator.guardUniqueness(uniqueness);
        File discardedFile = null;
        Writer discarded = null;
        try {
            RecordMapping recordMapping = sipModel.getMappingModel().getRecordMapping();
            if (recordMapping == null) {
                return;
            }
            MappingRunner mappingRunner = new MappingRunner(
                    groovyCodeResource,
                    recordMapping.toCompileCode(sipModel.getMetadataModel())
            );
            MetadataParser parser = new MetadataParser(
                    sipModel.getDataSetStore().getSourceInputStream(),
                    sipModel.getRecordRoot(),
                    sipModel.getRecordCount()
            );
            parser.setProgressListener(progressAdapter);
            if (saveInvalidRecords) {
                discardedFile = dataSetStore.getDiscardedFile(recordMapping);
                discarded = new BufferedWriter(new FileWriter(discardedFile));
            }
            MetadataRecord record;
            while ((record = parser.nextRecord()) != null && !aborted) {
                try {
                    Node outputNode = mappingRunner.runMapping(record);
                    StringWriter writer = new StringWriter();
                    XmlNodePrinter xmlNodePrinter = new XmlNodePrinter(new PrintWriter(writer));
                    xmlNodePrinter.print(outputNode);
                    recordValidator.validateRecord(outputNode, record.getRecordNumber());
                    validCount++;
                }
                catch (MappingException e) {
                    if (discarded != null) {
                        try {
                            discarded.write(record.toString());
                            e.printStackTrace(new PrintWriter(discarded));
                            discarded.write("\n========================================\n");
                        }
                        catch (IOException e1) {
                            sipModel.getUserNotifier().tellUser("Unable to write discarded record", e1);
                            abort();
                        }
                        invalidCount++;
                    }
                    else {
                        listener.invalidInput(e);
                        abort();
                    }
                }
                catch (ValidationException e) {
                    if (discarded != null) {
                        try {
                            discarded.write(record.toString());
                            e.printStackTrace(new PrintWriter(discarded));
                            discarded.write("\n========================================\n");
                        }
                        catch (IOException e1) {
                            sipModel.getUserNotifier().tellUser("Unable to write discarded record", e1);
                            abort();
                        }
                        invalidCount++;
                    }
                    else {
                        listener.invalidOutput(e);
                        abort();
                    }
                }
                catch (DiscardRecordException e) {
                    if (discarded != null) {
                        try {
                            discarded.write("Discarded explicitly: \n" + record.toString());
                            discarded.write("\n========================================\n");
                        }
                        catch (IOException e1) {
                            sipModel.getUserNotifier().tellUser("Unable to write discarded record", e1);
                            abort();
                        }
                    }
                    invalidCount++;
                }
                catch (Exception e) {
                    sipModel.getUserNotifier().tellUser("Problem writing output", e);
                    abort();
                }
            }
            Set<String> repeated = uniqueness.getRepeated();
            if (!repeated.isEmpty()) {
                discarded.write("\n\nThere were non-unique identifiers:\n");
                for (String line : repeated) {
                    discarded.write(line);
                    discarded.write("\n");
                }
                sipModel.getUserNotifier().tellUser("Identifier should be unique, but there were %d repeated values");
            }
            discarded.write("\nTHE END\n");
        }
        catch (XMLStreamException e) {
            throw new RuntimeException("XML Problem", e);
        }
        catch (IOException e) {
            throw new RuntimeException("IO Problem", e);
        }
        catch (FileStoreException e) {
            throw new RuntimeException("Datastore Problem", e);
        }
        catch (MetadataParser.AbortException e) {
            aborted = true;
        }
        finally {
            if (discarded != null) {
                try {
                    discarded.close();
                }
                catch (IOException e) {
                    sipModel.getUserNotifier().tellUser("Unable to close discarded records file", e);
                }
                if (aborted) {
                    if (discardedFile != null) {
                        if (!discardedFile.delete()) {
                            sipModel.getUserNotifier().tellUser("Unable to delete discarded records file");
                        }
                    }
                }
            }
            listener.finished(aborted, validCount, invalidCount);
            uniqueness.destroy();
            if (aborted) { // aborted, so metadataparser will not call finished()
                progressAdapter.finished(false);
            }
        }
    }

    private void abort() {
        aborted = true;
    }

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
