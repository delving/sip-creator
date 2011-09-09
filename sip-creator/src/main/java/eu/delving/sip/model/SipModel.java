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
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.files.Statistics;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
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
    private Storage storage;
    private GroovyCodeResource groovyCodeResource;
    private Preferences preferences = Preferences.userNodeForPackage(getClass());
    private UserNotifier userNotifier;
    private FieldListModel fieldListModel;
    private CompileModel recordCompileModel;
    private CompileModel fieldCompileModel;
    private MetadataParser metadataParser;
    private AnalysisModel analysisModel;
    private DataSetModel dataSetModel = new DataSetModel();
    private FactModel dataSetFacts = new FactModel();
    private FieldMappingListModel fieldMappingListModel = new FieldMappingListModel();
    private MappingModel mappingModel = new MappingModel();
    private ReportFileModel reportFileModel = new ReportFileModel(this);
    private List<ParseListener> parseListeners = new CopyOnWriteArrayList<ParseListener>();
    private boolean allowInvalidRecords;

    public interface AnalysisListener {
        void finished(boolean success);

        void analysisProgress(long elementCount);
    }

    public interface ValidationListener {

        void failed(ValidationException validationException);
    }

    public interface ParseListener {
        void updatedRecord(MetadataRecord metadataRecord);
    }

    public interface ScanPredicate {
        boolean accept(MetadataRecord record);
    }

    public SipModel(Storage storage, GroovyCodeResource groovyCodeResource, UserNotifier userNotifier) throws StorageException {
        this.storage = storage;
        this.groovyCodeResource = groovyCodeResource;
        this.userNotifier = userNotifier;
        fieldListModel = new FieldListModel(dataSetModel);
        recordCompileModel = new CompileModel(CompileModel.Type.RECORD, dataSetModel, groovyCodeResource);
        fieldCompileModel = new CompileModel(CompileModel.Type.FIELD, dataSetModel, groovyCodeResource);
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
                deleteSourceFile();
            }

            @Override
            public void uniqueElementSet(Path uniqueElementPath) {
                deleteSourceFile();
            }

            private void deleteSourceFile() {
                DataSet dataSet = dataSetModel.getDataSet();
                try {
                    dataSet.deleteConverted();
                }
                catch (StorageException e) {
                    SipModel.this.userNotifier.tellUser("Unable to delete converted source file", e);
                }
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

    public Storage getStorage() {
        return storage;
    }

    public FactModel getDataSetFacts() {
        return dataSetFacts;
    }

    public DataSetModel getDataSetModel() {
        return dataSetModel;
    }

    public MappingModel getMappingModel() {
        return mappingModel;
    }

    public boolean hasDataSet() {
        return dataSetModel.hasDataSet();
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

    public boolean isAllowInvalidRecords() {
        return allowInvalidRecords;
    }

    public void setAllowInvalidRecords(boolean allowInvalidRecords) {
        this.allowInvalidRecords = allowInvalidRecords;
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

    public void setDataSet(final DataSet dataSet) {
        Exec.work(new Runnable() {
            @Override
            public void run() {
                try {
                    final Statistics statistics = dataSet.getLatestStatistics();
                    final Map<String, String> facts = dataSet.getDataSetFacts();
                    final Map<String, String> hints = dataSet.getHints();
                    final String latestPrefix = dataSet.getLatestPrefix();
                    dataSetModel.setDataSet(dataSet);
                    Exec.swing(new Runnable() {
                        @Override
                        public void run() {
                            dataSetFacts.set(facts);
                            analysisModel.set(hints);
                            analysisModel.setStatistics(statistics);
                            seekFirstRecord();
                            if (latestPrefix != null) {
                                setMetadataPrefix(latestPrefix, false);
                            }
                        }
                    });
                }
                catch (StorageException e) {
                    userNotifier.tellUser(String.format("Unable to switch to data set %s", dataSet.getSpec()), e);
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
                            dataSetModel.getDataSet().setLatestPrefix(metadataPrefix, dataSetModel) :
                            dataSetModel.getDataSet().getRecordMapping(metadataPrefix, dataSetModel);
                    dataSetFacts.set("spec", dataSetModel.getDataSet().getSpec());
                    dataSetFacts.copyToRecordMapping(recordMapping);
                    mappingModel.setRecordMapping(recordMapping);
                    recordCompileModel.setRecordValidator(new RecordValidator(groovyCodeResource, getRecordDefinition()));
                }
                catch (StorageException e) {
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
        return dataSetModel.getRecordDefinition(recordMapping.getPrefix());
    }

    public void saveAsTemplate(final String name) {
        try {
            storage.setTemplate(name, mappingModel.getRecordMapping());
        }
        catch (StorageException e) {
            userNotifier.tellUser("Unable to save template", e);
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
                    dataSetModel.getDataSet().externalToImported(file, progressListener);
                }
                catch (StorageException e) {
                    userNotifier.tellUser("Couldn't create Data Set from " + file.getAbsolutePath(), e);
                }
            }
        });
    }

    public void analyzeFields(final AnalysisListener listener) {
        Exec.work(new AnalysisParser(dataSetModel.getDataSet(), new AnalysisParser.Listener() {
            @Override
            public void success(final Statistics statistics) {
                try {
                    dataSetModel.getDataSet().setStatistics(statistics);
                    Exec.swing(new Runnable() {
                        @Override
                        public void run() {
                            analysisModel.setStatistics(statistics);
                        }
                    });
                    listener.finished(true);
                }
                catch (StorageException e) {
                    userNotifier.tellUser("Problem storing statistics", e);
                    listener.finished(false);
                }
            }

            @Override
            public void failure(Exception exception) {
                listener.finished(false);
                userNotifier.tellUser("Analysis failed", exception);
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
                    dataSetModel.getDataSet().importedToSource(progressListener);
                    dataSetModel.getDataSet().setStatistics(analysisModel.convertStatistics());
                }
                catch (StorageException e) {
                    userNotifier.tellUser("Conversion failed", e);
                }
            }
        });
    }

    public void validateFile(final ProgressListener progressListener, final ValidationListener validationListener) {
        Exec.work(new FileValidator(
                this,
                allowInvalidRecords,
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
                        validationListener.failed(exception);
                        Exec.swing(new Runnable() {
                            @Override
                            public void run() {
                                seekRecordNumber(exception.getRecordNumber(), progressListener);
                            }
                        });
                    }

                    @Override
                    public void finished(final BitSet valid, int recordCount) {
                        try {
                            dataSetModel.getDataSet().setValidation(getMappingModel().getRecordMapping().getPrefix(), valid, recordCount);
                        }
                        catch (StorageException e) {
                            userNotifier.tellUser("Unable to store validation results", e);
                        }
                        reportFileModel.kick();
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
                if (!analysisModel.hasRecordRoot() || dataSetModel.getDataSet().getState().ordinal() < DataSetState.ANALYZED.ordinal()) {
                    return;
                }
                if (metadataParser == null) {
                    metadataParser = new MetadataParser(
                            dataSetModel.getDataSet().sourceInput(),
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
