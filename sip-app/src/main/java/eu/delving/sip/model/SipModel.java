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

import eu.delving.groovy.*;
import eu.delving.metadata.*;
import eu.delving.sip.base.*;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.xml.AnalysisParser;
import eu.delving.sip.xml.FileProcessor;
import eu.delving.sip.xml.MetadataParser;
import eu.delving.stats.Stats;
import org.apache.log4j.Logger;
import org.w3c.dom.Node;

import javax.swing.*;
import java.io.File;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.Preferences;

import static eu.delving.sip.files.DataSetState.ANALYZED_SOURCE;

/**
 * This model is behind the whole sip creator, as a facade for all the models related to a data set
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class SipModel {
    private static final Logger LOG = Logger.getLogger(SipModel.class);
    private XmlSerializer serializer = new XmlSerializer();
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
    private FactModel dataSetFacts = new FactModel();
    private DataSetModel dataSetModel = new DataSetModel();
    private CreateModel createModel = new CreateModel(this);
    private ReportFileModel reportFileModel = new ReportFileModel(this);
    private List<ParseListener> parseListeners = new CopyOnWriteArrayList<ParseListener>();
    private NodeTransferHandler nodeTransferHandler = new NodeTransferHandler(this);
    private MappingSaveTimer mappingSaveTimer = new MappingSaveTimer(this);
    private volatile boolean converting, validating, analyzing, importing;

    public interface AnalysisListener {
        boolean analysisProgress(long elementCount);

        void analysisComplete();
    }

    public interface ValidationListener {

        void failed(int recordNumber, String record, String message);
    }

    public interface ParseListener {
        void updatedRecord(MetadataRecord metadataRecord);
    }

    public interface ScanPredicate {
        boolean accept(MetadataRecord record);
    }

    public SipModel(Storage storage, GroovyCodeResource groovyCodeResource, final Feedback feedback, String instance) throws StorageException {
        this.preferences  = Preferences.userNodeForPackage(getClass()).node(instance);
        this.storage = storage;
        this.groovyCodeResource = groovyCodeResource;
        this.feedback = feedback;
        MappingModel mappingModel = dataSetModel.getMappingModel();
        functionCompileModel = new FunctionCompileModel(mappingModel, feedback, groovyCodeResource);
        recordCompileModel = new MappingCompileModel(MappingCompileModel.Type.RECORD, feedback, groovyCodeResource);
        fieldCompileModel = new MappingCompileModel(MappingCompileModel.Type.FIELD, feedback, groovyCodeResource);
        parseListeners.add(recordCompileModel.getParseEar());
        parseListeners.add(fieldCompileModel.getParseEar());
        mappingHintsModel = new MappingHintsModel(this);
        mappingModel.addSetListener(reportFileModel);
        mappingModel.addSetListener(recordCompileModel.getMappingModelSetListener());
        mappingModel.addChangeListener(recordCompileModel.getMappingModelChangeListener());
        mappingModel.addSetListener(fieldCompileModel.getMappingModelSetListener());
        mappingModel.addChangeListener(fieldCompileModel.getMappingModelChangeListener());
        mappingModel.addChangeListener(mappingHintsModel);
        mappingModel.addSetListener(mappingSaveTimer);
        mappingModel.addChangeListener(mappingSaveTimer);
        mappingModel.addChangeListener(new MappingModel.ChangeListener() {
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
            feedback.say("Validation cleared for this mappings");
        }
        catch (StorageException e) {
            LOG.warn(String.format("Error while deleting validation file %s", e));
        }
    }

    private void clearValidations() {
        try {
            dataSetModel.getDataSet().deleteAllValidations();
            feedback.say("Validation cleared for all mappings");
        }
        catch (StorageException e) {
            LOG.warn(String.format("Error while deleting validation files %s", e));
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

    public ListModel getReportFileModel() {
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

    public interface DataSetCompletion {
        void complete(boolean success);
    }

    public void setDataSet(final DataSet dataSet, final String requestedPrefix, final DataSetCompletion completion) {
        Exec.run(new Work() {
            @Override
            public void run() {
                try {
                    final Stats stats = dataSet.getLatestStats();
                    final Map<String, String> facts = dataSet.getDataSetFacts();
                    final Map<String, String> hints = dataSet.getHints();
                    final String prefix = requestedPrefix.isEmpty() ? dataSet.getLatestPrefix() : requestedPrefix;
                    dataSetModel.setDataSet(dataSet, prefix);
                    final RecMapping recMapping = dataSetModel.getRecMapping();
                    dataSetFacts.set("spec", dataSetModel.getDataSet().getSpec());
                    mappingHintsModel.initialize(requestedPrefix, dataSetModel);
                    dataSetModel.getMappingModel().setFacts(facts);
                    recordCompileModel.setValidator(dataSetModel.newValidator());
                    feedback.say(String.format("Loaded dataset '%s' and '%s' mapping", dataSet.getSpec(), dataSetModel.getPrefix()));
                    Exec.run(new Swing() {
                        @Override
                        public void run() {
                            dataSetFacts.set(facts);
                            statsModel.set(hints);
                            statsModel.setStatistics(stats);
                            Exec.run(new Work() {
                                @Override
                                public void run() {
                                    mappingHintsModel.setSourceTree(statsModel.getSourceTree());
                                    for (NodeMapping nodeMapping : recMapping.getRecDefTree().getNodeMappings()) {
                                        statsModel.findNodesForInputPaths(nodeMapping);
                                    }
                                }
                            });
                            seekFirstRecord();
                        }
                    });
                    completion.complete(true);
                }
                catch (Exception e) {
                    completion.complete(false);
                    feedback.alert(String.format("Sorry, unable to switch to data set %s.", dataSet.getSpec()), e);
                    dataSetModel.clearDataSet();
                }
            }
        });
    }

    public void importSource(final File file, final ProgressListener progressListener) {
        if (importing) {
            feedback.say("Busy importing");
        }
        else {
            importing = true;
            clearValidations();
            feedback.say("Importing metadata from " + file.getAbsolutePath());
            Exec.run(new Work() {
                @Override
                public void run() {
                    try {
                        dataSetModel.getDataSet().externalToImported(file, progressListener);
                        feedback.say("Finished importing metadata");
                        importing = false;
                    }
                    catch (StorageException e) {
                        feedback.alert(String.format("Couldn't create Data Set from %s: %s", file.getAbsolutePath(), e.getMessage()), e);
                    }
                }
            });
        }
    }

    public void analyzeFields(final AnalysisListener listener) {
        if (analyzing) {
            feedback.say("Busy analyzing");
        }
        else {
            analyzing = true;
            feedback.say("Analyzing data from " + dataSetModel.getDataSet().getSpec());
            Exec.run(new AnalysisParser(dataSetModel, statsModel.getMaxUniqueValueLength(), new AnalysisParser.Listener() {
                @Override
                public void success(final Stats stats) {
                    analyzing = false;
                    try {
                        dataSetModel.getDataSet().setStats(stats, stats.sourceFormat, null);
                        Exec.run(new Swing() {
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
                        feedback.say("Import analyzed");
                    }
                    catch (StorageException e) {
                        feedback.alert("Problem storing statistics", e);
                    }
                    finally {
                        listener.analysisComplete();
                    }
                }

                @Override
                public void failure(String message, Exception exception) {
                    analyzing = false;
                    listener.analysisComplete();
                    if (message == null) {
                        message = "Analysis failed";
                    }
                    if (exception != null) {
                        feedback.alert(message, exception);
                    }
                    else {
                        feedback.say("Analysis aborted");
                    }
                }

                @Override
                public boolean progress(long elementCount) {
                    return listener.analysisProgress(elementCount);
                }
            }));
        }
    }

    public void convertSource(final ProgressListener progressListener) {
        clearValidations();
        if (converting) {
            feedback.say("Busy converting to source for " + dataSetModel.getDataSet().getSpec());
        }
        else {
            converting = true;
            feedback.say("Converting to source for " + dataSetModel.getDataSet().getSpec());
            Exec.run(new Work() {
                @Override
                public void run() {
                    try {
                        dataSetModel.getDataSet().importedToSource(feedback, progressListener);
                        Exec.run(new Swing() {
                            @Override
                            public void run() {
                                seekFirstRecord();
                            }
                        });
                        feedback.say("Source conversion complete");
                    }
                    catch (StorageException e) {
                        feedback.alert("Conversion failed: " + e.getMessage(), e);
                    }
                    finally {
                        converting = false;
                    }
                }
            });
        }
    }

    public void validateFile(boolean allowInvalidRecords, final ProgressListener progressListener, final ValidationListener validationListener) {
        if (validating) {
            feedback.say("Busy validating");
        }
        else {
            validating = true;
            feedback.say(String.format(
                    "Validating mapping %s for data set %s, %s",
                    mappingModel().getRecMapping().getPrefix(),
                    dataSetModel.getDataSet().getSpec(),
                    allowInvalidRecords ? "allowing invalid records" : "expecting valid records"
            ));
            File outputDirectory = null;
            String directoryString = getPreferences().get(FileProcessor.OUTPUT_FILE_PREF, "").trim();
            if (!directoryString.isEmpty()) {
                outputDirectory = new File(directoryString);
                if (!outputDirectory.exists()) outputDirectory = null;
            }
            Exec.run(new FileProcessor(
                    this,
                    allowInvalidRecords,
                    outputDirectory,
                    groovyCodeResource,
                    progressListener,
                    new FileProcessor.Listener() {
                        @Override
                        public void mappingFailed(final MappingException exception) {
                            String xml = XmlNodePrinter.toXml(exception.getMetadataRecord().getRootNode());
                            validationListener.failed(exception.getMetadataRecord().getRecordNumber(), xml, exception.getMessage());
                        }

                        @Override
                        public void outputInvalid(int recordNumber, Node outputNode, String message) {
                            String xml = serializer.toXml(outputNode);
                            validationListener.failed(recordNumber, xml, message);
                        }

                        @Override
                        public void finished(final Stats stats, final BitSet valid, int recordCount) {
                            try {
                                DataSet dataSet = dataSetModel.getDataSet();
                                dataSet.setStats(stats, false, mappingModel().getRecMapping().getPrefix());
                                dataSet.setValidation(getMappingModel().getRecMapping().getPrefix(), valid, recordCount);
                            }
                            catch (StorageException e) {
                                feedback.alert("Unable to store validation results", e);
                            }
                            reportFileModel.kick();
                            feedback.say("Validation complete, report available");
                            validating = false;
                        }
                    }
            ));
        }
    }

    public void seekReset() {
        Exec.run(new Work() {
            @Override
            public void run() {
                if (metadataParser != null) {
                    metadataParser.close();
                    metadataParser = null;
                    for (ParseListener parseListener : parseListeners) parseListener.updatedRecord(null);
                }
            }
        });
    }

    public void seekFirstRecord() {
        seekRecordNumber(0, null);
    }

    public void seekRecordNumber(final int recordNumber, ProgressListener progressListener) {
        seekReset();
        ScanPredicate numberScan = new ScanPredicate() {
            @Override
            public boolean accept(MetadataRecord record) {
                return record.getRecordNumber() == recordNumber;
            }
        };
        seekRecord(numberScan, progressListener);
    }

    public void seekRecord(ScanPredicate scanPredicate, ProgressListener progressListener) {
        Exec.run(new RecordScanner(scanPredicate, progressListener));
    }

    // === privates

    private class RecordScanner implements Work {
        private ScanPredicate scanPredicate;
        private ProgressListener progressListener;

        private RecordScanner(ScanPredicate scanPredicate, ProgressListener progressListener) {
            this.scanPredicate = scanPredicate;
            this.progressListener = progressListener;
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
                            if (progressListener != null) progressListener.finished(true);
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
        }
    }

    private MappingModel mappingModel() {
        return dataSetModel.getMappingModel();
    }
}
