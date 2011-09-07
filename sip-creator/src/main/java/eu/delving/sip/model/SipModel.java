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

package eu.delving.sip.model;

import eu.delving.groovy.GroovyCodeResource;
import eu.delving.groovy.MappingException;
import eu.delving.groovy.MetadataRecord;
import eu.delving.metadata.FieldDefinition;
import eu.delving.metadata.FieldStatistics;
import eu.delving.metadata.MappingModel;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecordDefinition;
import eu.delving.metadata.RecordMapping;
import eu.delving.metadata.RecordValidator;
import eu.delving.metadata.ValidationException;
import eu.delving.sip.ProgressListener;
import eu.delving.sip.base.Exec;
import eu.delving.sip.files.FileStore;
import eu.delving.sip.files.FileStoreException;
import eu.delving.sip.files.Statistics;
import eu.delving.sip.xml.AnalysisParser;
import eu.delving.sip.xml.FileValidator;
import eu.delving.sip.xml.MetadataParser;

import javax.swing.ListModel;
import java.io.File;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.Preferences;

/**
 * This model is behind the whole sip creator, as a facade for all the models related to a data set
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class SipModel {
    private FileStore fileStore;
    private GroovyCodeResource groovyCodeResource;
    private Preferences preferences = Preferences.userNodeForPackage(getClass());
    private UserNotifier userNotifier;
    private FieldListModel fieldListModel;
    private CompileModel recordCompileModel;
    private CompileModel fieldCompileModel;
    private MetadataParser metadataParser;
    private AnalysisModel analysisModel;
    private DataSetStoreModel storeModel = new DataSetStoreModel();
    private FactModel dataSetFacts = new FactModel();
    private FieldMappingListModel fieldMappingListModel = new FieldMappingListModel();
    private MappingModel mappingModel = new MappingModel();
    private ReportFileModel reportFileModel = new ReportFileModel(this);
    private List<ParseListener> parseListeners = new CopyOnWriteArrayList<ParseListener>();

    public interface AnalysisListener {
        void finished(boolean success);

        void analysisProgress(long elementCount);
    }

    public interface ParseListener {
        void updatedRecord(MetadataRecord metadataRecord);
    }

    public interface ScanPredicate {
        boolean accept(MetadataRecord record);
    }

    public SipModel(FileStore fileStore, GroovyCodeResource groovyCodeResource, UserNotifier userNotifier) throws FileStoreException {
        this.fileStore = fileStore;
        this.groovyCodeResource = groovyCodeResource;
        this.userNotifier = userNotifier;
        fieldListModel = new FieldListModel(storeModel);
        recordCompileModel = new CompileModel(CompileModel.Type.RECORD, storeModel, groovyCodeResource);
        fieldCompileModel = new CompileModel(CompileModel.Type.FIELD, storeModel, groovyCodeResource);
        parseListeners.add(recordCompileModel);
        parseListeners.add(fieldCompileModel);
        mappingModel.addListener(fieldMappingListModel);
        mappingModel.addListener(reportFileModel);
        mappingModel.addListener(recordCompileModel);
        mappingModel.addListener(fieldCompileModel);
        mappingModel.addListener(new MappingSaveTimer(this));
        analysisModel = new AnalysisModel(this);
        analysisModel.addListener(new AnalysisModel.Listener() {
            @Override
            public void statisticsSelected(FieldStatistics fieldStatistics) {
            }

            @Override
            public void recordRootSet(Path recordRootPath) {
                seekFirstRecord();
            }
        });
        fieldCompileModel.addListener(new CompileModel.Listener() {
            @Override
            public void stateChanged(CompileModel.State state) {
                switch (state) {
                    case COMMITTED:
                    case REGENERATED:
                        mappingModel.notifySelectedFieldMappingChange();
                }
            }
        });
    }

    public void addParseListener(ParseListener parseListener) {
        parseListeners.add(parseListener);
    }

    public Preferences getPreferences() {
        return preferences;
    }

    public FileStore getFileStore() {
        return fileStore;
    }

    public FactModel getDataSetFacts() {
        return dataSetFacts;
    }

    public DataSetStoreModel getStoreModel() {
        return storeModel;
    }

    public MappingModel getMappingModel() {
        return mappingModel;
    }

    public boolean hasDataSetStore() {
        return storeModel.hasStore();
    }

    public AnalysisModel getAnalysisModel() {
        return analysisModel;
    }

    public ListModel getReportFileModel() {
        return reportFileModel;
    }

    public UserNotifier getUserNotifier() {
        return userNotifier;
    }

    public ListModel getUnmappedFieldListModel() {
        return fieldListModel.getUnmapped(getMappingModel());
    }

    public CompileModel getRecordCompileModel() {
        return recordCompileModel;
    }

    public CompileModel getFieldCompileModel() {
        return fieldCompileModel;
    }

    public List<FieldDefinition> getUnmappedFields() {
        List<FieldDefinition> fields = new ArrayList<FieldDefinition>();
        ListModel listModel = getUnmappedFieldListModel();
        for (int walkField = 0; walkField < listModel.getSize(); walkField++) {
            fields.add((FieldDefinition) listModel.getElementAt(walkField));
        }
        return fields;
    }

    public ListModel getFieldMappingListModel() {
        return fieldMappingListModel;
    }

    public void setDataSetStore(final FileStore.DataSetStore dataSetStore) {
        Exec.work(new Runnable() {
            @Override
            public void run() {
                try {
                    final Statistics statistics = dataSetStore.getLatestStatistics();
                    final Map<String, String> facts = dataSetStore.getDataSetFacts();
                    final Map<String, String> hints = dataSetStore.getHints();
                    final String latestPrefix = dataSetStore.getLatestPrefix();
                    storeModel.setStore(dataSetStore);
                    Exec.swing(new Runnable() {
                        @Override
                        public void run() {
                            dataSetFacts.set(facts);
                            analysisModel.set(hints);
                            analysisModel.setStatistics(statistics);
                            if (latestPrefix != null) {
                                setMetadataPrefix(latestPrefix, false);
                            }
                        }
                    });
                }
                catch (FileStoreException e) {
                    userNotifier.tellUser(String.format("Unable to switch to data set %s", dataSetStore.getSpec()), e);
                }
            }
        });
    }

    public void setMetadataPrefix(final String metadataPrefix, final boolean promoteToLatest) {
        Exec.work(new Runnable() {
            @Override
            public void run() {
                try {
                    final RecordMapping recordMapping = promoteToLatest ?
                            storeModel.getStore().setLatestPrefix(metadataPrefix, storeModel) :
                            storeModel.getStore().getRecordMapping(metadataPrefix, storeModel);
                    dataSetFacts.set("spec", storeModel.getStore().getSpec());
                    dataSetFacts.copyToRecordMapping(recordMapping);
                    mappingModel.setRecordMapping(recordMapping);
                    recordCompileModel.setRecordValidator(new RecordValidator(groovyCodeResource, getRecordDefinition()));
                }
                catch (FileStoreException e) {
                    userNotifier.tellUser("Unable to select Metadata Prefix " + metadataPrefix, e);
                }
            }
        });
    }

    public RecordDefinition getRecordDefinition() {
        RecordMapping recordMapping = mappingModel.getRecordMapping();
        if (recordMapping == null) {
            return null;
        }
        return storeModel.getRecordDefinition(recordMapping.getPrefix());
    }

    public void saveAsTemplate(final String name) {
        try {
            fileStore.setTemplate(name, mappingModel.getRecordMapping());
        }
        catch (FileStoreException e) {
            userNotifier.tellUser("Unable to store template", e);
        }
    }

    public void applyTemplate(RecordMapping template) {
        if (!mappingModel.getRecordMapping().getFieldMappings().isEmpty()) {
            userNotifier.tellUser("Record must be empty to use a template.");
        }
        else {
            try {
                template.apply(getRecordDefinition());
                mappingModel.applyTemplate(template);
                seekFirstRecord();
            }
            catch (Exception e) {
                userNotifier.tellUser("Unable to load template", e);
            }
        }
    }

    public void importSource(final File file, final ProgressListener progressListener) {
        Exec.work(new Runnable() {
            @Override
            public void run() {
                try {
                    storeModel.getStore().externalToImported(file, progressListener);
                    storeModel.checkState();
                }
                catch (FileStoreException e) {
                    userNotifier.tellUser("Couldn't create Data Set from " + file.getAbsolutePath(), e);
                }
            }
        });
    }

    public void analyzeFields(final AnalysisListener listener) {
        Exec.work(new AnalysisParser(storeModel.getStore(), new AnalysisParser.Listener() {
            @Override
            public void success(final Statistics statistics) {
                try {
                    storeModel.getStore().setStatistics(statistics);
                    Exec.swing(new Runnable() {
                        @Override
                        public void run() {
                            analysisModel.setStatistics(statistics);
                            storeModel.checkState();
                        }
                    });
                    listener.finished(true);
                }
                catch (FileStoreException e) {
                    userNotifier.tellUser("Problem storing statistics", e);
                    listener.finished(false);
                }
            }

            @Override
            public void failure(Exception exception) {
                listener.finished(false);
                userNotifier.tellUser("Analysis failed", exception);
                storeModel.checkState();
            }

            @Override
            public void progress(long elementCount) {
                listener.analysisProgress(elementCount);
            }
        }));
    }

    public void convertSource(final ProgressListener progressListener) {
        Exec.work(new Runnable() {
            @Override
            public void run() {
                try {
                    storeModel.getStore().importedToSource(progressListener);
                    storeModel.getStore().setStatistics(analysisModel.convertStatistics());
                    storeModel.checkState();
                    // todo: make the variables list refresh
                }
                catch (FileStoreException e) {
                    userNotifier.tellUser("Conversion failed", e);
                }
            }
        });
    }

    public void validateFile(boolean allowInvalid, final ProgressListener progressListener) {
        Exec.work(new FileValidator(
                this,
                allowInvalid,
                groovyCodeResource,
                progressListener,
                new FileValidator.Listener() {
                    @Override
                    public void invalidInput(final MappingException exception) {
                        userNotifier.tellUser("Problem validating " + exception.getMetadataRecord().toString(), exception);
                        Exec.swing(new Runnable() {
                            @Override
                            public void run() {
                                seekRecordNumber(exception.getMetadataRecord().getRecordNumber(), progressListener);
                            }
                        });
                    }

                    @Override
                    public void invalidOutput(final ValidationException exception) {
                        userNotifier.tellUser("Invalid output record", exception);
                        Exec.swing(new Runnable() {
                            @Override
                            public void run() {
                                seekRecordNumber(exception.getRecordNumber(), progressListener);
                            }
                        });
                    }

                    @Override
                    public void finished(final BitSet valid, int recordCount) {
                        reportFileModel.kick();
                        storeModel.checkState();
                    }
                }
        ));
    }

    public void seekFresh() {
        if (metadataParser != null) {
            metadataParser.close();
            metadataParser = null;
        }
    }

    public void seekFirstRecord() {
        seekRecordNumber(0, null);
    }

    public void seekRecordNumber(final int recordNumber, ProgressListener progressListener) {
        seekFresh();
        seekRecord(
                new ScanPredicate() {
                    @Override
                    public boolean accept(MetadataRecord record) {
                        return record.getRecordNumber() == recordNumber;
                    }
                },
                progressListener
        );
    }

    public void seekRecord(ScanPredicate scanPredicate, ProgressListener progressListener) {
        if (analysisModel.hasRecordRoot()) {
            Exec.work(new RecordScanner(scanPredicate, progressListener));
        }
    }

    // === privates

    private class RecordScanner implements Runnable {
        private ScanPredicate scanPredicate;
        private ProgressListener progressListener;

        private RecordScanner(ScanPredicate scanPredicate, ProgressListener progressListener) {
            this.scanPredicate = scanPredicate;
            this.progressListener = progressListener;
        }

        @Override
        public void run() {
            try {
                if (!analysisModel.hasRecordRoot() || storeModel.getStore().getState().ordinal() < FileStore.StoreState.ANALYZED.ordinal()) {
                    return;
                }
                if (metadataParser == null) {
                    metadataParser = new MetadataParser(
                            storeModel.getStore().sourceInput(),
                            analysisModel.getRecordCount()
                    );
                }
                metadataParser.setProgressListener(progressListener);
                MetadataRecord metadataRecord;
                while ((metadataRecord = metadataParser.nextRecord()) != null) {
                    if (scanPredicate == null || scanPredicate.accept(metadataRecord)) {
                        for (ParseListener parseListener : parseListeners) {
                            parseListener.updatedRecord(metadataRecord);
                        }
                        if (progressListener != null) {
                            progressListener.finished(true);
                        }
                        break;
                    }
                }
            }
            catch (Exception e) {
                userNotifier.tellUser("Unable to fetch the next record", e);
                metadataParser = null;
            }
        }
    }
}
