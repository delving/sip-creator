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
import eu.delving.metadata.MappingModelAdapter;
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
import org.apache.log4j.Logger;

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

    private static final Logger LOG = Logger.getLogger(SipModel.class);
    private Storage storage;
    private GroovyCodeResource groovyCodeResource;
    private Preferences preferences = Preferences.userNodeForPackage(getClass());
    private Feedback feedback;
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

    public SipModel(Storage storage, GroovyCodeResource groovyCodeResource, final Feedback feedback) throws StorageException {
        this.storage = storage;
        this.groovyCodeResource = groovyCodeResource;
        this.feedback = feedback;
        fieldListModel = new FieldListModel(dataSetModel);
        recordCompileModel = new CompileModel(CompileModel.Type.RECORD, dataSetModel, feedback, groovyCodeResource);
        fieldCompileModel = new CompileModel(CompileModel.Type.FIELD, dataSetModel, feedback, groovyCodeResource);
        parseListeners.add(recordCompileModel);
        parseListeners.add(fieldCompileModel);
        mappingModel.addListener(fieldMappingListModel);
        mappingModel.addListener(reportFileModel);
        mappingModel.addListener(recordCompileModel);
        mappingModel.addListener(fieldCompileModel);
        mappingModel.addListener(new MappingSaveTimer(this));
        mappingModel.addListener(
                new MappingModelAdapter() {

                    @Override
                    public void fieldMappingChanged() {
                        LOG.info("FieldMapping has changed");
                        clearValidation(mappingModel.getRecordMapping());
                    }

                    @Override
                    public void recordMappingChanged(RecordMapping recordMapping) {
                        clearValidation(recordMapping);
                    }
                }
        );
        analysisModel = new AnalysisModel(this);
        analysisModel.addListener(new AnalysisModel.Listener() {
            @Override
            public void statisticsSelected(FieldStatistics fieldStatistics) {
            }

            @Override
            public void recordRootSet(Path recordRootPath) {
                deleteSourceFile();
                clearValidation(mappingModel.getRecordMapping());
            }

            @Override
            public void uniqueElementSet(Path uniqueElementPath) {
                deleteSourceFile();
                clearValidation(mappingModel.getRecordMapping());
            }

            private void deleteSourceFile() {
                DataSet dataSet = dataSetModel.getDataSet();
                try {
                    dataSet.deleteConverted();
                }
                catch (StorageException e) {
                    feedback.alert("Unable to delete converted source file", e);
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

    private void clearValidation(RecordMapping recordMapping) {
        try {
            if (dataSetModel.hasDataSet() && recordMapping != null) {
                dataSetModel.getDataSet().deleteValidation(recordMapping.getPrefix());
                feedback.say(String.format("Validation cleared for %s", recordMapping.getPrefix()));
            }
        }
        catch (StorageException e) {
            LOG.warn(String.format("Error while deleting file: %s%n", e));
        }
    }

    private void clearValidations() {
        try {
            if (dataSetModel.hasDataSet()) {
                dataSetModel.getDataSet().deleteValidations();
            }
            feedback.say("Validation cleared for all mappings");
        }
        catch (StorageException e) {
            LOG.warn(String.format("Error while deleting file: %s%n", e));
        }
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

    public Feedback getFeedback() {
        return feedback;
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
                            feedback.say("Loaded data set " + dataSet.getSpec());
                            if (latestPrefix != null) {
                                feedback.say("There is a recent mapping for " + dataSet.getSpec());
                                setMetadataPrefix(latestPrefix, false);
                            }
                        }
                    });
                }
                catch (StorageException e) {
                    feedback.alert(String.format("Unable to switch to data set %s", dataSet.getSpec()), e);
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
                    feedback.say(String.format("Using '%s' mapping", metadataPrefix));
                }
                catch (StorageException e) {
                    feedback.alert("Unable to select Metadata Prefix " + metadataPrefix, e);
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

//    public void saveAsTemplate(final String name) {
//        try {
//            storage.setTemplate(name, mappingModel.getRecordMapping());
//        }
//        catch (StorageException e) {
//            feedback.tellUser("Unable to save template", e);
//        }
//    }
//
//    public void applyTemplate(RecordMapping template) {
//        if (!mappingModel.getRecordMapping().getFieldMappings().isEmpty()) {
//            feedback.tellUser("Record must be empty to use a template.");
//        }
//        else {
//            try {
//                template.apply(getRecordDefinition());
//                mappingModel.applyTemplate(template);
//                seekFirstRecord();
//            }
//            catch (Exception e) {
//                feedback.tellUser("Unable to load template", e);
//            }
//        }
//    }

    public void importSource(final File file, final ProgressListener progressListener) {
        clearValidations();
        feedback.say("Importing metadata from " + file.getAbsolutePath());
        Exec.work(new Runnable() {
            @Override
            public void run() {
                try {
                    dataSetModel.getDataSet().externalToImported(file, progressListener);
                    feedback.say("Finished importing metadata");
                }
                catch (StorageException e) {
                    feedback.alert(String.format("Couldn't create Data Set from %s: %s", file.getAbsolutePath(), e.getMessage()), e);
                }
            }
        });
    }

    public void analyzeFields(final AnalysisListener listener) {
        feedback.say("Analyzing import from " + dataSetModel.getDataSet().getSpec());
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
                    feedback.say("Import analyzed");
                }
                catch (StorageException e) {
                    feedback.alert("Problem storing statistics", e);
                }
            }

            @Override
            public void failure(Exception exception) {
                feedback.alert("Analysis failed: " + exception.getMessage(), exception);
            }

            @Override
            public void progress(long elementCount) {
                listener.analysisProgress(elementCount);
            }
        }));
    }

    public void convertSource(final ProgressListener progressListener) {
        clearValidations();
        feedback.say("Converting to source for " + dataSetModel.getDataSet().getSpec());
        Exec.work(new Runnable() {
            @Override
            public void run() {
                try {
                    dataSetModel.getDataSet().importedToSource(progressListener);
                    dataSetModel.getDataSet().setStatistics(analysisModel.convertStatistics());
                    feedback.say("Source conversion complete");
                }
                catch (StorageException e) {
                    feedback.alert("Conversion failed: " + e.getMessage(), e);
                }
            }
        });
    }

    public void validateFile(final ProgressListener progressListener, final ValidationListener validationListener) {
        feedback.say(String.format("Validating mapping %s for data set %s", mappingModel.getRecordMapping().getPrefix(), dataSetModel.getDataSet().getSpec()));
        Exec.work(new FileValidator(
                this,
                allowInvalidRecords,
                groovyCodeResource,
                progressListener,
                new FileValidator.Listener() {
                    @Override
                    public void invalidInput(final MappingException exception) {
                        feedback.alert("Problem validating " + exception.getMetadataRecord().toString(), exception);
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
                            feedback.alert("Unable to store validation results", e);
                        }
                        reportFileModel.kick();
                        feedback.say("Validation complete, report available");
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
                if (!analysisModel.hasRecordRoot() || dataSetModel.getDataSet().getState().ordinal() < DataSetState.ANALYZED_SOURCE.ordinal()) {
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
                feedback.alert("Unable to fetch the next record", e);
                metadataParser = null;
            }
        }
    }
}
