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

package eu.delving.sip.model;

import eu.delving.groovy.GroovyCodeResource;
import eu.delving.groovy.MappingException;
import eu.delving.groovy.MetadataRecord;
import eu.delving.groovy.XmlSerializer;
import eu.delving.metadata.*;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.NodeTransferHandler;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.files.*;
import eu.delving.sip.xml.AnalysisParser;
import eu.delving.sip.xml.FileValidator;
import eu.delving.sip.xml.MetadataParser;
import org.apache.log4j.Logger;
import org.w3c.dom.Node;

import javax.swing.ListModel;
import java.io.File;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.Preferences;

/**
 * This model is behind the whole sip creator, as a facade for all the models related to a data set
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class SipModel {

    private static final Logger LOG = Logger.getLogger(SipModel.class);
    private Storage storage;
    private GroovyCodeResource groovyCodeResource;
    private Preferences preferences = Preferences.userNodeForPackage(getClass());
    private Feedback feedback;
    private FunctionCompileModel functionCompileModel;
    private MappingCompileModel recordCompileModel;
    private MappingCompileModel fieldCompileModel;
    private MetadataParser metadataParser;
    private StatsModel statsModel;
    private DataSetModel dataSetModel = new DataSetModel();
    private FactModel dataSetFacts = new FactModel();
    private MappingModel mappingModel = new MappingModel();
    private CreateModel createModel = new CreateModel(this);
    private ReportFileModel reportFileModel = new ReportFileModel(this);
    private List<ParseListener> parseListeners = new CopyOnWriteArrayList<ParseListener>();
    private NodeTransferHandler nodeTransferHandler = new NodeTransferHandler(this);
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

    public SipModel(Storage storage, GroovyCodeResource groovyCodeResource, final Feedback feedback) throws StorageException {
        this.storage = storage;
        this.groovyCodeResource = groovyCodeResource;
        this.feedback = feedback;
        functionCompileModel = new FunctionCompileModel(mappingModel, feedback, groovyCodeResource);
        recordCompileModel = new MappingCompileModel(MappingCompileModel.Type.RECORD, feedback, groovyCodeResource);
        fieldCompileModel = new MappingCompileModel(MappingCompileModel.Type.FIELD, feedback, groovyCodeResource);
        parseListeners.add(recordCompileModel.getParseEar());
        parseListeners.add(fieldCompileModel.getParseEar());
        mappingModel.addSetListener(reportFileModel);
        mappingModel.addSetListener(recordCompileModel.getMappingModelSetListener());
        mappingModel.addChangeListener(recordCompileModel.getMappingModelChangeListener());
        mappingModel.addSetListener(fieldCompileModel.getMappingModelSetListener());
        mappingModel.addChangeListener(fieldCompileModel.getMappingModelChangeListener());
        MappingSaveTimer saveTimer = new MappingSaveTimer(this);
        mappingModel.addSetListener(saveTimer);
        mappingModel.addChangeListener(saveTimer);
        mappingModel.addSetListener(new MappingModel.SetListener() {
            @Override
            public void recMappingSet(MappingModel mappingModel) {
                clearValidation(mappingModel.getRecMapping());
            }
        });
        mappingModel.addChangeListener(new MappingModel.ChangeListener() {
            @Override
            public void functionChanged(MappingModel mappingModel, MappingFunction function) {
                clearValidation(mappingModel.getRecMapping());
            }

            @Override
            public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
                clearValidation(mappingModel.getRecMapping());
            }

            @Override
            public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
                clearValidation(mappingModel.getRecMapping());
            }

            @Override
            public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
                clearValidation(mappingModel.getRecMapping());
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
                clearValidation(mappingModel.getRecMapping());
            }

            @Override
            public void uniqueElementSet(Path uniqueElementPath) {
                deleteSourceFile();
                clearValidation(mappingModel.getRecMapping());
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

    private void clearValidation(RecMapping recMapping) {
        try {
            if (dataSetModel.hasDataSet() && recMapping != null) {
                if (dataSetModel.getDataSet().deleteValidation(recMapping.getPrefix())) {
                    feedback.say(String.format("Validation cleared for %s", recMapping.getPrefix()));
                }
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

    public CreateModel getCreateModel() {
        return createModel;
    }

    public NodeTransferHandler getNodeTransferHandler() {
        return nodeTransferHandler;
    }

    public boolean hasDataSet() {
        return dataSetModel.hasDataSet();
    }

    public boolean hasPrefix() {
        return mappingModel.getRecMapping() != null;
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
        Exec.work(new Runnable() {
            @Override
            public void run() {
                try {
                    final Statistics statistics = dataSet.getLatestStatistics();
                    final Map<String, String> facts = dataSet.getDataSetFacts();
                    final Map<String, String> hints = dataSet.getHints();
                    final String prefix = requestedPrefix.isEmpty() ? dataSet.getLatestPrefix() : requestedPrefix;
                    dataSetModel.setDataSet(dataSet);
                    final RecMapping recMapping = dataSetModel.getRecMapping(prefix);
                    dataSetFacts.set("spec", dataSetModel.getDataSet().getSpec());
                    mappingModel.setRecMapping(recMapping);
                    mappingModel.setFacts(facts);
                    recordCompileModel.setValidator(dataSetModel.getValidator(prefix));
                    feedback.say(String.format("Loaded dataset '%s' and '%s' mapping", dataSet.getSpec(), prefix));
                    Exec.swing(new Runnable() {
                        @Override
                        public void run() {
                            dataSetFacts.set(facts);
                            statsModel.setPrefix(requestedPrefix, dataSetModel);
                            statsModel.set(hints);
                            statsModel.setStatistics(statistics);
                            for (NodeMapping nodeMapping : recMapping.getRecDefTree().getNodeMappings()) {
                                statsModel.findNodesForInputPaths(nodeMapping);
                            }
                            seekFirstRecord();
                        }
                    });
                    completion.complete(true);
                }
                catch (final Exception e) {
                    completion.complete(false);
                    feedback.alert(String.format("Unable to switch to data set %s", dataSet.getSpec()), e);
                    // todo: find out how this can repeat endlessly!
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
            Exec.work(new Runnable() {
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
            Exec.work(new AnalysisParser(dataSetModel.getDataSet(), new AnalysisParser.Listener() {
                @Override
                public void success(final Statistics statistics) {
                    analyzing = false;
                    try {
                        dataSetModel.getDataSet().setStatistics(statistics);
                        Exec.swing(new Runnable() {
                            @Override
                            public void run() {
                                statsModel.setStatistics(statistics);
                                if (statistics.isSourceFormat()) {
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
            Exec.work(new Runnable() {
                @Override
                public void run() {
                    try {
                        dataSetModel.getDataSet().importedToSource(progressListener);
                        Exec.swing(new Runnable() {
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
                    mappingModel.getRecMapping().getPrefix(),
                    dataSetModel.getDataSet().getSpec(),
                    allowInvalidRecords ? "allowing invalid records" : "expecting valid records"
            ));
            Exec.work(new FileValidator(
                    this,
                    allowInvalidRecords,
                    groovyCodeResource,
                    progressListener,
                    new FileValidator.Listener() {
                        @Override
                        public void mappingFailed(final MappingException exception) {
                            String xml = XmlSerializer.toXml(exception.getMetadataRecord().getRootNode());
                            validationListener.failed(exception.getMetadataRecord().getRecordNumber(), xml, exception.getMessage());
                        }

                        @Override
                        public void outputInvalid(int recordNumber, Node outputNode, String message) {
                            String xml = XmlSerializer.toXml(outputNode);
                            validationListener.failed(recordNumber, xml, message);
                        }

                        @Override
                        public void finished(final BitSet valid, int recordCount) {
                            try {
                                dataSetModel.getDataSet().setValidation(getMappingModel().getRecMapping().getPrefix(), valid, recordCount);
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
        Exec.work(new Runnable() {
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
        Exec.work(new RecordScanner(scanPredicate, progressListener));
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
                if (!hasDataSet() || !statsModel.hasRecordRoot() || dataSetModel.getDataSet().getState().ordinal() < DataSetState.ANALYZED_SOURCE.ordinal()) {
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
}
