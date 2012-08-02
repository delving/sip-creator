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

package eu.delving.sip.model;

import eu.delving.groovy.GroovyCodeResource;
import eu.delving.groovy.MetadataRecord;
import eu.delving.metadata.*;
import eu.delving.sip.base.NodeTransferHandler;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.xml.AnalysisParser;
import eu.delving.sip.xml.FileProcessor;
import eu.delving.sip.xml.MetadataParser;
import eu.delving.stats.Stats;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.Preferences;

import static eu.delving.sip.files.DataSetState.ANALYZED_SOURCE;

/**
 * This is the grand model behind the whole SIP-Creator and it holds all of the other models.  Its state
 * corresponds that of a dataset with attention set specific record mapping within, described by its prefix.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class SipModel {
    private static final Logger LOG = Logger.getLogger(SipModel.class);
    private WorkModel workModel;
    private Storage storage;
    private GroovyCodeResource groovyCodeResource;
    private Preferences preferences;
    private Feedback feedback;
    private FunctionCompileModel functionCompileModel;
    private MappingCompileModel recordCompileModel;
    private MappingCompileModel fieldCompileModel;
    private MetadataParser metadataParser;
    private StatsModel statsModel;
    private MappingHintsModel mappingHintsModel;
    private DataSetModel dataSetModel;
    private FactModel dataSetFacts = new FactModel();
    private CreateModel createModel;
    private ReportFileModel reportFileModel;
    private NodeTransferHandler nodeTransferHandler;
    private MappingSaveTimer mappingSaveTimer;
    private List<ParseListener> parseListeners = new CopyOnWriteArrayList<ParseListener>();

    public interface ParseListener {
        void updatedRecord(MetadataRecord metadataRecord);
    }

    public interface ScanPredicate {
        boolean accept(MetadataRecord record);
    }

    public SipModel(Storage storage, GroovyCodeResource groovyCodeResource, final Feedback feedback) throws StorageException {
        this.preferences = Preferences.userNodeForPackage(getClass());
        this.storage = storage;
        this.groovyCodeResource = groovyCodeResource;
        this.feedback = feedback;
        this.workModel = new WorkModel(feedback);
        dataSetModel = new DataSetModel(this);
        functionCompileModel = new FunctionCompileModel(this, groovyCodeResource);
        recordCompileModel = new MappingCompileModel(this, MappingCompileModel.Type.RECORD, groovyCodeResource);
        fieldCompileModel = new MappingCompileModel(this, MappingCompileModel.Type.FIELD, groovyCodeResource);
        parseListeners.add(recordCompileModel.getParseEar());
        parseListeners.add(fieldCompileModel.getParseEar());
        mappingHintsModel = new MappingHintsModel(this);
        createModel = new CreateModel(this);
        reportFileModel = new ReportFileModel(this);
        nodeTransferHandler = new NodeTransferHandler(this);
        mappingSaveTimer = new MappingSaveTimer(this);
        MappingModel mm = dataSetModel.getMappingModel();
        mm.addSetListener(recordCompileModel.getMappingModelSetListener());
        mm.addChangeListener(recordCompileModel.getMappingModelChangeListener());
        mm.addSetListener(fieldCompileModel.getMappingModelSetListener());
        mm.addChangeListener(fieldCompileModel.getMappingModelChangeListener());
        mm.addChangeListener(mappingHintsModel);
        mm.addSetListener(mappingSaveTimer);
        mm.addChangeListener(mappingSaveTimer);
        mm.addChangeListener(new MappingModel.ChangeListener() {
            @Override
            public void lockChanged(MappingModel mappingModel, boolean locked) {
            }

            @Override
            public void functionChanged(MappingModel mappingModel, MappingFunction function) {
                clearValidation();
            }

            @Override
            public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping, NodeMappingChange change) {
                clearValidation();
            }

            @Override
            public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
                clearValidation();
            }

            @Override
            public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
                clearValidation();
            }
        });

// todo: on changes in create model?..  clearValidation(recordMapping);

        statsModel = new StatsModel(this);
        statsModel.addListener(new StatsModel.Listener() {
            @Override
            public void mappingHints(List<NodeMapping> mappings) {
            }

            @Override
            public void recordRootSet(Path recordRootPath) {
                deleteSourceFile();
                clearValidations();
            }

            @Override
            public void uniqueElementSet(Path uniqueElementPath) {
                deleteSourceFile();
                clearValidations();
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
    }

    private void clearValidation() {
        try {
            dataSetModel.deleteValidation();
        }
        catch (StorageException e) {
            LOG.warn(String.format("Error while deleting validation file %s", e));
        }
    }

    private void clearValidations() {
        try {
            dataSetModel.getDataSet().deleteAllValidations();
        }
        catch (StorageException e) {
            LOG.warn(String.format("Error while deleting validation files %s", e));
        }
    }

    public void addParseListener(ParseListener parseListener) {
        parseListeners.add(parseListener);
    }

    public WorkModel getWorkModel() {
        return workModel;
    }

    public Preferences getPreferences() {
        return preferences;
    }

    public Storage getStorage() {
        return storage;
    }

    public MappingSaveTimer getMappingSaveTimer() {
        return mappingSaveTimer;
    }

    public FactModel getDataSetFacts() {
        return dataSetFacts;
    }

    public DataSetModel getDataSetModel() {
        return dataSetModel;
    }

    public MappingModel getMappingModel() {
        return dataSetModel.getMappingModel();
    }

    public MappingHintsModel getMappingHintsModel() {
        return mappingHintsModel;
    }

    public CreateModel getCreateModel() {
        return createModel;
    }

    public NodeTransferHandler getNodeTransferHandler() {
        return nodeTransferHandler;
    }

    public StatsModel getStatsModel() {
        return statsModel;
    }

    public ReportFileModel getReportFileModel() {
        return reportFileModel;
    }

    public Feedback getFeedback() {
        return feedback;
    }

    public FunctionCompileModel getFunctionCompileModel() {
        return functionCompileModel;
    }

    public MappingCompileModel getRecordCompileModel() {
        return recordCompileModel;
    }

    public MappingCompileModel getFieldCompileModel() {
        return fieldCompileModel;
    }

    public void setDataSetPrefix(DataSet dataSet, String prefix, Swing success) {
        exec(new DataSetPrefixLoader(dataSet, prefix, success));
    }

    private class DataSetPrefixLoader implements Work.DataSetPrefixWork, Work.LongTermWork {
        private DataSet dataSet;
        private String prefix;
        private ProgressListener progressListener;
        private Swing success;

        private DataSetPrefixLoader(DataSet dataSet, String prefix, Swing success) {
            this.dataSet = dataSet;
            this.prefix = prefix;
            this.success = success;
        }

        @Override
        public Job getJob() {
            return Job.SET_DATASET;
        }

        @Override
        public String getPrefix() {
            return prefix;
        }

        @Override
        public DataSet getDataSet() {
            return dataSet;
        }

        @Override
        public void setProgressListener(ProgressListener progressListener) {
            this.progressListener = progressListener;
        }

        @Override
        public void run() {
            try {
                final Stats stats = dataSet.getLatestStats();
                final Map<String, String> facts = dataSet.getDataSetFacts();
                final Map<String, String> hints = dataSet.getHints();
                final String prefixToUse = prefix.isEmpty() ? dataSet.getLatestPrefix() : prefix;
                dataSetModel.setDataSet(dataSet, prefixToUse);
                final RecMapping recMapping = dataSetModel.getRecMapping();
                dataSetFacts.set("spec", dataSetModel.getDataSet().getSpec());
                mappingHintsModel.initialize(prefixToUse, dataSetModel);
                dataSetModel.getMappingModel().setFacts(facts);
                recordCompileModel.setValidator(dataSetModel.newValidator());
                exec(new Swing() {
                    @Override
                    public void run() {
                        dataSetFacts.set(facts);
                        statsModel.set(hints);
                        statsModel.setStatistics(stats);
                        exec(new Work() {
                            @Override
                            public void run() {
                                mappingHintsModel.setSourceTree(statsModel.getSourceTree());
                                for (NodeMapping nodeMapping : recMapping.getRecDefTree().getNodeMappings()) {
                                    statsModel.findNodesForInputPaths(nodeMapping);
                                }
                            }

                            @Override
                            public Job getJob() {
                                return Job.SET_MAPPING_HINTS_FIND_NODES;
                            }
                        });
                        seekFirstRecord();
                    }
                });
                Swing.Exec.later(success);
            }
            catch (Exception e) {
                feedback.alert(String.format("Sorry, unable to switch to data set %s.", dataSet.getSpec()), e);
                dataSetModel.clearDataSet();
            }
        }

    }

    public void importSource(final File file, Swing finished) {
        clearValidations();
        exec(new SourceImporter(file, dataSetModel.getDataSet(), finished));
    }

    private class SourceImporter implements Work.DataSetWork, Work.LongTermWork {
        private File file;
        private DataSet dataSet;
        private ProgressListener progressListener;
        private Swing finished;

        private SourceImporter(File file, DataSet dataSet, Swing finished) {
            this.file = file;
            this.dataSet = dataSet;
            this.finished = finished;
        }

        @Override
        public DataSet getDataSet() {
            return dataSet;
        }

        @Override
        public Job getJob() {
            return Job.IMPORT_SOURCE;
        }

        @Override
        public void run() {
            try {
                dataSet.externalToImported(file, progressListener);
                Swing.Exec.later(finished);
            }
            catch (StorageException e) {
                feedback.alert(String.format("Couldn't create Data Set from %s: %s", file.getAbsolutePath(), e.getMessage()), e);
            }
        }

        @Override
        public void setProgressListener(ProgressListener progressListener) {
            this.progressListener = progressListener;
            progressListener.setProgressMessage(String.format("Storing data for %s", dataSet.getSpec()));
        }
    }

    public void analyzeFields() {
        exec(new AnalysisParser(dataSetModel, statsModel.getMaxUniqueValueLength(), new AnalysisParser.Listener() {
            @Override
            public void success(final Stats stats) {
                try {
                    dataSetModel.getDataSet().setStats(stats, stats.sourceFormat, null);
                    exec(new Swing() {
                        @Override
                        public void run() {
                            statsModel.setStatistics(stats);
                            if (stats.sourceFormat) {
                                seekFirstRecord();
                            }
                            else {
                                seekReset();
                            }
                        }
                    });
                }
                catch (StorageException e) {
                    feedback.alert("Problem storing statistics", e);
                }
            }

            @Override
            public void failure(String message, Exception exception) {
                if (message == null) {
                    message = "Analysis failed";
                }
                if (exception != null) {
                    feedback.alert(message, exception);
                }
            }
        }));
    }

    public void convertSource() {
        clearValidations();
        exec(new ConversionRunner(dataSetModel.getDataSet()));
    }

    private class ConversionRunner implements Work.DataSetWork, Work.LongTermWork {
        private DataSet dataSet;
        private ProgressListener progressListener;

        private ConversionRunner(DataSet dataSet) {
            this.dataSet = dataSet;
        }

        @Override
        public DataSet getDataSet() {
            return dataSet;
        }

        @Override
        public Job getJob() {
            return Job.CONVERT_SOURCE;
        }

        @Override
        public void run() {
            try {
                dataSetModel.getDataSet().importedToSource(feedback, progressListener);
                exec(new Swing() {
                    @Override
                    public void run() {
                        seekFirstRecord();
                    }
                });
            }
            catch (StorageException e) {
                feedback.alert("Conversion failed: " + e.getMessage(), e);
            }
        }

        @Override
        public void setProgressListener(ProgressListener progressListener) {
            this.progressListener = progressListener;
            progressListener.setProgressMessage(String.format(
                    "Converting source data of '%s' to standard form",
                    dataSet.getSpec()
            ));
        }
    }

    public void processFile(FileProcessor.Listener listener) {
        File outputDirectory = null;
        String directoryString = getPreferences().get(FileProcessor.OUTPUT_FILE_PREF, "").trim();
        if (!directoryString.isEmpty()) {
            outputDirectory = new File(directoryString);
            if (!outputDirectory.exists()) outputDirectory = null;
        }
        exec(new FileProcessor(
                this,
                statsModel.getMaxUniqueValueLength(),
                statsModel.getRecordCount(),
                outputDirectory,
                groovyCodeResource,
                listener
        ));
    }

    public void seekReset() {
        exec(new Work.DataSetWork() {
            @Override
            public void run() {
                if (metadataParser != null) {
                    metadataParser.close();
                    metadataParser = null;
                    for (ParseListener parseListener : parseListeners) parseListener.updatedRecord(null);
                }
            }

            @Override
            public Job getJob() {
                return Job.SEEK_RESET;
            }

            @Override
            public DataSet getDataSet() {
                return dataSetModel.getDataSet();
            }
        });
    }

    public void seekFirstRecord() {
        seekRecordNumber(0);
    }

    public void seekRecordNumber(final int recordNumber) {
        seekReset();
        ScanPredicate numberScan = new ScanPredicate() {
            @Override
            public boolean accept(MetadataRecord record) {
                return record.getRecordNumber() == recordNumber;
            }
        };
        seekRecord(numberScan, null);
    }

    public void seekRecord(ScanPredicate scanPredicate, Swing finished) {
        exec(new RecordScanner(scanPredicate, finished));
    }

    // === privates

    private class RecordScanner implements Work.DataSetWork, Work.LongTermWork {
        private ScanPredicate scanPredicate;
        private ProgressListener progressListener;
        private Swing finished;

        private RecordScanner(ScanPredicate scanPredicate, Swing finished) {
            this.scanPredicate = scanPredicate;
            this.finished = finished;
        }

        @Override
        public void run() {
            try {
                boolean noRecordRoot = !statsModel.hasRecordRoot();
                DataSetState state = dataSetModel.getDataSetState();
                if (noRecordRoot || !state.atLeast(ANALYZED_SOURCE)) {
                    for (ParseListener parseListener : parseListeners) parseListener.updatedRecord(null);
                    return;
                }
                if (metadataParser == null) {
                    metadataParser = new MetadataParser(dataSetModel.getDataSet().openSourceInputStream(), statsModel.getRecordCount());
                }
                metadataParser.setProgressListener(progressListener);
                MetadataRecord metadataRecord;
                try {
                    while ((metadataRecord = metadataParser.nextRecord()) != null) {
                        if (scanPredicate == null || scanPredicate.accept(metadataRecord)) {
                            for (ParseListener parseListener : parseListeners) {
                                parseListener.updatedRecord(metadataRecord);
                            }
                            break;
                        }
                    }
                }
                catch (MetadataParser.AbortException e) {
                    // do nothing
                }
            }
            catch (Exception e) {
                feedback.alert("Unable to fetch the next record", e);
                metadataParser = null;
            }
            finally {
                if (finished != null) exec(finished);
            }
        }

        @Override
        public Job getJob() {
            return Job.SCAN_RECORDS;
        }

        @Override
        public DataSet getDataSet() {
            return dataSetModel.getDataSet(); // todo: should hold it, i suppose
        }

        @Override
        public void setProgressListener(ProgressListener progressListener) {
            this.progressListener = progressListener;
            progressListener.setProgressMessage("Scanning input records");
        }
    }

    private MappingModel mappingModel() {
        return dataSetModel.getMappingModel();
    }

    public void exec(Swing swing) {
        Swing.Exec.later(swing);
    }

    public void exec(Work work) {
        workModel.exec(work);
    }
}
