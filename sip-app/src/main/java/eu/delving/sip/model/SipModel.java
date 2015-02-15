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
import eu.delving.metadata.MappingFunction;
import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.NodeMappingChange;
import eu.delving.metadata.RecDefNode;
import eu.delving.metadata.RecMapping;
import eu.delving.schema.SchemaVersion;
import eu.delving.sip.base.NodeTransferHandler;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.frames.AllFrames;
import eu.delving.sip.xml.AnalysisParser;
import eu.delving.sip.xml.FileProcessor;
import eu.delving.sip.xml.MetadataParser;
import eu.delving.stats.Stats;

import javax.swing.*;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This is the grand model behind the whole SIP-Creator and it holds all of the other models.  Its state
 * corresponds that of a dataset with attention set specific record mapping within, described by its prefix.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class SipModel {
    private JDesktopPane desktop;
    private AllFrames.ViewSelector viewSelector;
    private WorkModel workModel;
    private Storage storage;
    private GroovyCodeResource groovyCodeResource;
    private Preferences preferences;
    private Feedback feedback;
    private FunctionCompileModel functionCompileModel;
    private MappingCompileModel recordCompileModel;
    private MappingCompileModel fieldCompileModel;
    private MetadataParser parser;
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

    public SipModel(JDesktopPane desktop, Storage storage, GroovyCodeResource groovyCodeResource, final Feedback feedback, Preferences preferences) throws StorageException {
        this.desktop = desktop;
        this.storage = storage;
        this.groovyCodeResource = groovyCodeResource;
        this.feedback = feedback;
        this.preferences = preferences;
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
        mm.addChangeListener(mappingHintsModel.getMappingChangeListener());
        mm.addSetListener(mappingSaveTimer);
        mm.addChangeListener(mappingSaveTimer);
        mm.addChangeListener(new MappingModel.ChangeListener() {
            @Override
            public void lockChanged(MappingModel mappingModel, boolean locked) {
            }

            @Override
            public void functionChanged(MappingModel mappingModel, MappingFunction function) {
                dataSetModel.deleteResults();
            }

            @Override
            public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping, NodeMappingChange change) {
                dataSetModel.deleteResults();
            }

            @Override
            public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
                dataSetModel.deleteResults();
            }

            @Override
            public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
                dataSetModel.deleteResults();
            }

            @Override
            public void populationChanged(MappingModel mappingModel, RecDefNode node) {
            }
        });
        // todo: on changes in create model?..  clearValidation(recordMapping);
        statsModel = new StatsModel(this);
    }

    public void shutdown() {
        dataSetModel.shutdown();
        mappingSaveTimer.shutdown();
        workModel.shutdown();
        reportFileModel.shutdown();
    }

    public JDesktopPane getDesktop() {
        return desktop;
    }

    public AllFrames.ViewSelector getViewSelector() {
        return viewSelector;
    }

    public void setViewSelector(AllFrames.ViewSelector viewSelector) {
        this.viewSelector = viewSelector;
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

    public void setDataSet(DataSet dataSet, Swing success) {
        exec(new DatasetLoader(dataSet, success));
    }

    private class DatasetLoader implements Work.DataSetWork {
        private DataSet dataSet;
        private Swing success;

        private DatasetLoader(DataSet dataSet, Swing success) {
            this.dataSet = dataSet;
            this.success = success;
        }

        @Override
        public Job getJob() {
            return Job.SET_DATASET;
        }

        @Override
        public DataSet getDataSet() {
            return dataSet;
        }

        @Override
        public void run() {
            try {
                final Stats stats = dataSet.getStats();
                final Map<String, String> facts = dataSet.getDataSetFacts();
                final Map<String, String> hints = dataSet.getHints();
                final SchemaVersion schemaVersion = dataSet.getSchemaVersion();
                String prefix = schemaVersion.getPrefix();
                dataSetModel.setDataSet(dataSet, prefix);
                final RecMapping recMapping = dataSetModel.getRecMapping();
                dataSetFacts.set("spec", dataSetModel.getDataSet().getSpec());
                mappingHintsModel.initialize(prefix, dataSetModel);
                dataSetModel.getMappingModel().setFacts(facts);
                dataSetModel.getMappingModel().setSchemaVersion(schemaVersion);
//                recordCompileModel.setValidator(dataSetModel.newValidator());
//                recordCompileModel.setAssertions(AssertionTest.listFrom(recMapping.getRecDefTree().getRecDef(), groovyCodeResource));
                reportFileModel.refresh();
                exec(new Swing() {
                    @Override
                    public void run() {
                        dataSetFacts.set(facts);
                        statsModel.getHintsModel().set(hints);
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
                if (success != null) Swing.Exec.later(success);
            }
            catch (Exception e) {
                feedback.alert(String.format("Sorry, unable to switch to data set %s.", dataSet.getSpec()), e);
                dataSetModel.clearDataSet();
            }
        }

    }

    public void analyzeFields() {
        exec(new AnalysisParser(dataSetModel, statsModel.getMaxUniqueValueLength(), new AnalysisParser.Listener() {
            @Override
            public void success(final Stats stats) {
                try {
                    dataSetModel.getDataSet().setStats(stats);
                    exec(new Swing() {
                        @Override
                        public void run() {
                            statsModel.setStatistics(stats);
                            seekFirstRecord();
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

    private class Generator implements FileProcessor.UriGenerator {
        private final String spec;
        private final String prefix;
        private final Matcher matcher;

        private Generator(String narthexUrl, String spec, String prefix) {
            this.spec = spec;
            this.prefix = prefix;
            this.matcher = Pattern.compile("(https?://[^/]+).*").matcher(narthexUrl);
            if (!matcher.matches()) {
                throw new RuntimeException("Unable to get URL base from Narthex URL: " + narthexUrl);
            }
        }

        @Override
        public String generateUri(String id) {
            try {
                return String.format(
                        "%s/resource/document/%s_%s/%s",
                        matcher.group(1), spec, prefix, URLEncoder.encode(id, "UTF-8")
                );
            }
            catch (UnsupportedEncodingException e) {
                throw new RuntimeException("Unable to encode " + id);
            }
        }
    }

    public void processFile(FileProcessor.Listener listener) {
        final DataSet dataSet = getDataSetModel().getDataSet();
        final String narthexUrl = getPreferences().get(Storage.NARTHEX_URL, "");
        dataSet.deleteResults();
        getMappingModel().setLocked(true);
        exec(new FileProcessor(
                this,
                dataSet,
                getMappingModel().getRecMapping(),
                false,
                groovyCodeResource,
                new Generator(narthexUrl, dataSet.getSpec(), getMappingModel().getPrefix()),
                listener
        ));
    }

    public void seekReset() {
        exec(new Work.DataSetWork() {
            @Override
            public void run() {
                if (parser != null) {
                    parser.close();
                    parser = null;
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
                if (parser == null) {
                    parser = new MetadataParser(dataSetModel.getDataSet().openSourceInputStream(), statsModel.getRecordCount());
                }
                parser.setProgressListener(progressListener);
                for (MetadataRecord metadataRecord = parser.nextRecord(); !metadataRecord.isPoison(); metadataRecord = parser.nextRecord()) {
                    if (scanPredicate == null || scanPredicate.accept(metadataRecord)) {
                        for (ParseListener parseListener : parseListeners) {
                            parseListener.updatedRecord(metadataRecord);
                        }
                        break;
                    }
                }
            }
            catch (Exception e) {
                feedback.alert("Unable to fetch the next record", e);
                parser = null;
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
