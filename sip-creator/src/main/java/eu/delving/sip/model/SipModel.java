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
import eu.delving.metadata.AnalysisTree;
import eu.delving.metadata.FieldDefinition;
import eu.delving.metadata.FieldMapping;
import eu.delving.metadata.FieldStatistics;
import eu.delving.metadata.MappingModel;
import eu.delving.metadata.MetadataModel;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecordDefinition;
import eu.delving.metadata.RecordMapping;
import eu.delving.metadata.RecordValidator;
import eu.delving.metadata.SourceVariable;
import eu.delving.metadata.ValidationException;
import eu.delving.sip.ProgressListener;
import eu.delving.sip.files.FileStore;
import eu.delving.sip.files.FileStoreException;
import eu.delving.sip.xml.AnalysisParser;
import eu.delving.sip.xml.FileValidator;
import eu.delving.sip.xml.MetadataParser;

import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;

/**
 * This model is behind the whole sip creator, as a facade for all the models related to a data set
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class SipModel {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private FileStore fileStore;
    private MetadataModel metadataModel;
    private GroovyCodeResource groovyCodeResource;
    private Preferences preferences = Preferences.userNodeForPackage(getClass());
    private FileStore.DataSetStore dataSetStore;
    private UserNotifier userNotifier;
    private List<FieldStatistics> fieldStatisticsList;
    private AnalysisTree analysisTree;
    private DefaultTreeModel analysisTreeModel;
    private FieldListModel fieldListModel;
    private CompileModel recordCompileModel;
    private CompileModel fieldCompileModel;
    private MetadataParser metadataParser;
    private MetadataRecord metadataRecord;
    private FactModel dataSetFacts = new FactModel();
    private FactModel hints = new FactModel();
    private FieldMappingListModel fieldMappingListModel;
    private MappingModel mappingModel = new MappingModel();
    private VariableListModel variableListModel = new VariableListModel();
    private List<UpdateListener> updateListeners = new CopyOnWriteArrayList<UpdateListener>();
    private List<ParseListener> parseListeners = new CopyOnWriteArrayList<ParseListener>();

    public interface UpdateListener {

        void updatedDataSetStore(FileStore.DataSetStore dataSetStore);

        void updatedStatistics(FieldStatistics fieldStatistics);

        void updatedRecordRoot(Path recordRoot);
    }

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

    public SipModel(FileStore fileStore, MetadataModel metadataModel, GroovyCodeResource groovyCodeResource, UserNotifier userNotifier) throws FileStoreException {
        this.fileStore = fileStore;
        this.metadataModel = metadataModel;
        this.groovyCodeResource = groovyCodeResource;
        this.userNotifier = userNotifier;
        analysisTree = AnalysisTree.create("Select a Data Set from the File menu");
        analysisTreeModel = new DefaultTreeModel(analysisTree.getRoot());
        fieldListModel = new FieldListModel(metadataModel);
        recordCompileModel = new CompileModel(CompileModel.Type.RECORD, metadataModel, groovyCodeResource);
        fieldCompileModel = new CompileModel(CompileModel.Type.FIELD, metadataModel, groovyCodeResource);
        parseListeners.add(recordCompileModel);
        parseListeners.add(fieldCompileModel);
        fieldMappingListModel = new FieldMappingListModel();
        mappingModel.addListener(fieldMappingListModel);
        mappingModel.addListener(recordCompileModel);
        mappingModel.addListener(fieldCompileModel);
        mappingModel.addListener(new MappingSaveTimer());
        hints.addListener(new HintSaveTimer());
        fieldCompileModel.addListener(new CompileModel.Listener() {
            @Override
            public void stateChanged(CompileModel.State state) {
                switch (state) {
                    case COMMITTED:
                    case REGENERATED:
                        mappingModel.changeSelected();
                }
            }
        });
    }

    public void addUpdateListener(UpdateListener updateListener) {
        updateListeners.add(updateListener);
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

    public void importSource(final File file, final ProgressListener progressListener) {
        if (dataSetStore == null) throw new RuntimeException("Import requires that dataset store is set");
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    dataSetStore.importSource(file, progressListener);
                }
                catch (FileStoreException e) {
                    userNotifier.tellUser("Couldn't create Data Set from " + file.getAbsolutePath(), e);
                }
            }
        });
    }

    public FileStore.DataSetStore getDataSetStore() {
        return dataSetStore;
    }

    public MetadataModel getMetadataModel() {
        return metadataModel;
    }

    public MappingModel getMappingModel() {
        return mappingModel;
    }

    public UserNotifier getUserNotifier() {
        return userNotifier;
    }

    public void setDataSetStore(final FileStore.DataSetStore dataSetStore) {
        checkSwingThread();
        this.dataSetStore = dataSetStore;
        if (dataSetStore != null) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    final List<FieldStatistics> statistics = dataSetStore.getStatistics();
                    final Map<String, String> dataSetFacts = dataSetStore.getDataSetFacts();
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            SipModel.this.dataSetFacts.set(dataSetFacts);
                            setStatisticsList(statistics);
                            seekFirstRecord();
                            if (mappingModel.getRecordMapping() != null) {
                                setMetadataPrefix(mappingModel.getRecordMapping().getPrefix());
                            }
                            variableListModel.clear();
                            AnalysisTree.setUniqueElement(analysisTreeModel, getUniqueElement());
                            for (UpdateListener updateListener : updateListeners) {
                                updateListener.updatedDataSetStore(dataSetStore);
                            }
                        }
                    });
                }
            });
        }
        else {
            for (UpdateListener updateListener : updateListeners) {
                updateListener.updatedDataSetStore(this.dataSetStore);
            }
        }
    }

    public void setMetadataPrefix(final String metadataPrefix) {
        checkSwingThread();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final RecordMapping recordMapping = dataSetStore.getRecordMapping(metadataPrefix);
                    final Map<String,String> hints = dataSetStore.getHints();
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            mappingModel.setRecordMapping(recordMapping);
                            recordCompileModel.setRecordValidator(new RecordValidator(groovyCodeResource, getRecordDefinition()));
                            hints.putAll(hints);
                            setRecordRootInternal(new Path(hints.get(FileStore.RECORD_ROOT_PATH)));
                            setUniqueElement(new Path(hints.get(FileStore.UNIQUE_ELEMENT_PATH)));
                        }
                    });
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
        return metadataModel.getRecordDefinition(recordMapping.getPrefix());
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

    @SuppressWarnings("unchecked")
    public void analyzeFields(final AnalysisListener listener) {
        checkSwingThread();
        executor.execute(new AnalysisParser(dataSetStore, new AnalysisParser.Listener() {

            @Override
            public void success(final Object list) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        setStatisticsList((List<FieldStatistics>) list);
                    }
                });
                listener.finished(true);
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

    public interface ValidationListener {
        void validationMessage(boolean aborted, int validCount, int invalidCount);
    }

    public void validateFile(boolean saveInvalidRecords, final ProgressListener progressListener, final ValidationListener validationListener) {
        checkSwingThread();
        executor.execute(new FileValidator(
                this,
                saveInvalidRecords,
                groovyCodeResource,
                progressListener,
                new FileValidator.Listener() {
                    @Override
                    public void invalidInput(final MappingException exception) {
                        userNotifier.tellUser("Problem normalizing " + exception.getMetadataRecord().toString(), exception);
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                seekRecordNumber(exception.getMetadataRecord().getRecordNumber(), progressListener);
                            }
                        });
                    }

                    @Override
                    public void invalidOutput(final ValidationException exception) {
                        userNotifier.tellUser("Invalid output record", exception);
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                seekRecordNumber(exception.getRecordNumber(), progressListener);
                            }
                        });
                    }

                    @Override
                    public void finished(final boolean aborted, final int validCount, final int invalidCount) {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                validationListener.validationMessage(aborted, validCount,  invalidCount);
                            }
                        });
                    }
                }
        ));
    }

    public TreeModel getAnalysisTreeModel() {
        return analysisTreeModel;
    }

    public Path getUniqueElement() {
        return new Path(hints.get(FileStore.UNIQUE_ELEMENT_PATH));
    }

    public void setUniqueElement(Path uniqueElement) {
        hints.set(FileStore.UNIQUE_ELEMENT_PATH, uniqueElement.toString());
        AnalysisTree.setUniqueElement(analysisTreeModel, uniqueElement);
    }

    public Path getRecordRoot() {
        return new Path(hints.get(FileStore.RECORD_ROOT_PATH));
    }

    public int getRecordCount() {
        String recordCount = hints.get(FileStore.RECORD_COUNT);
        return recordCount == null ? 0 : Integer.parseInt(recordCount);
    }

    public void setRecordRoot(Path recordRoot, int recordCount) {
        checkSwingThread();
        setRecordRootInternal(recordRoot);
        seekFirstRecord();
        hints.set(FileStore.RECORD_ROOT_PATH, recordRoot.toString());
        hints.set(FileStore.RECORD_COUNT, String.valueOf(recordCount));
    }

    public long getElementCount() {
        if (fieldStatisticsList != null) {
            long total = 0L;
            for (FieldStatistics stats : fieldStatisticsList) {
                total += stats.getTotal();
            }
            return total;
        }
        else {
            return 0L;
        }
    }

    public ListModel getUnmappedFieldListModel() {
        return fieldListModel.getUnmapped(getMappingModel());
    }

    public List<FieldDefinition> getUnmappedFields() {
        List<FieldDefinition> fields = new ArrayList<FieldDefinition>();
        ListModel listModel = getUnmappedFieldListModel();
        for (int walkField = 0; walkField < listModel.getSize(); walkField++) {
            fields.add((FieldDefinition) listModel.getElementAt(walkField));
        }
        return fields;
    }

    public ListModel getVariablesListModel() {
        return variableListModel;
    }

    public List<SourceVariable> getVariables() {
        List<SourceVariable> list = new ArrayList<SourceVariable>();
        for (int walkVar = 0; walkVar < variableListModel.getSize(); walkVar++) {
            list.add((SourceVariable) variableListModel.getElementAt(walkVar));
        }
        return list;
    }

    public ListModel getVariablesListWithCountsModel() {
        return variableListModel.getWithCounts(getMappingModel());
    }

    public void addFieldMapping(FieldMapping fieldMapping) {
        checkSwingThread();
        getMappingModel().setMapping(fieldMapping.getDefinition().path.toString(), fieldMapping);
    }

    public void removeFieldMapping(FieldMapping fieldMapping) {
        checkSwingThread();
        getMappingModel().setMapping(fieldMapping.getDefinition().path.toString(), null);
    }

    public ListModel getFieldMappingListModel() {
        return fieldMappingListModel;
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
        checkSwingThread();
        if (getRecordRoot() != null) {
            executor.execute(new RecordScanner(scanPredicate, progressListener));
        }
    }

    public CompileModel getRecordCompileModel() {
        return recordCompileModel;
    }

    public CompileModel getFieldCompileModel() {
        return fieldCompileModel;
    }

    // === privates

    private void setRecordRootInternal(Path recordRoot) {
        checkSwingThread();
        List<AnalysisTree.Node> variables = new ArrayList<AnalysisTree.Node>();
        if (recordRoot != null) {
            AnalysisTree.setRecordRoot(analysisTreeModel, recordRoot);
            analysisTree.getVariables(variables);
            variableListModel.setVariableList(variables);
        }
        else {
            variableListModel.clear();
        }
        for (UpdateListener updateListener : updateListeners) {
            updateListener.updatedRecordRoot(recordRoot);
        }
    }

    public void setStatistics(FieldStatistics fieldStatistics) {
        for (UpdateListener updateListener : updateListeners) {
            updateListener.updatedStatistics(fieldStatistics);
        }
    }

    private void setStatisticsList(List<FieldStatistics> fieldStatisticsList) {
        checkSwingThread();
        this.fieldStatisticsList = fieldStatisticsList;
        if (fieldStatisticsList != null) {
            analysisTree = AnalysisTree.create(fieldStatisticsList);
        }
        else {
            analysisTree = AnalysisTree.create("Analysis not yet performed");
        }
        analysisTreeModel.setRoot(analysisTree.getRoot());
        if (getRecordRoot() != null) {
            AnalysisTree.setRecordRoot(analysisTreeModel, getRecordRoot());
        }
        if (getUniqueElement() != null) {
            AnalysisTree.setUniqueElement(analysisTreeModel, getUniqueElement());
        }
        setStatistics(null);
    }

    private class RecordScanner implements Runnable {
        private ScanPredicate scanPredicate;
        private ProgressListener progressListener;

        private RecordScanner(ScanPredicate scanPredicate, ProgressListener progressListener) {
            this.scanPredicate = scanPredicate;
            this.progressListener = progressListener;
        }

        @Override
        public void run() {
            Path recordRoot = getRecordRoot();
            if (recordRoot == null) {
                return;
            }
            try {
                if (metadataParser == null) {
                    metadataParser = new MetadataParser(dataSetStore.getSourceInputStream(), recordRoot, getRecordCount());
                }
                metadataParser.setProgressListener(progressListener);
                while ((metadataRecord = metadataParser.nextRecord()) != null) {
                    if (scanPredicate == null || scanPredicate.accept(metadataRecord)) {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                for (ParseListener parseListener : parseListeners) {
                                    parseListener.updatedRecord(metadataRecord);
                                }
                                if (progressListener != null) {
                                    progressListener.finished(true);
                                }
                            }
                        });
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

    private class HintSaveTimer implements FactModel.Listener, ActionListener, Runnable {
        private Timer timer = new Timer(200, this);

        private HintSaveTimer() {
            timer.setRepeats(false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            executor.execute(this);
        }

        @Override
        public void run() {
            try {
                dataSetStore.setHints(hints.getFacts());
            }
            catch (FileStoreException e) {
                userNotifier.tellUser("Unable to save mapping", e);
            }
        }

        @Override
        public void factUpdated(String name, String value) {
            timer.restart();
        }

        @Override
        public void allFactsUpdated() {
            timer.restart();
        }
    }

    private class MappingSaveTimer implements MappingModel.Listener, ActionListener, Runnable {
        private Timer timer = new Timer(200, this);

        private MappingSaveTimer() {
            timer.setRepeats(false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            executor.execute(this);
        }

        @Override
        public void run() {
            try {
                RecordMapping recordMapping = mappingModel.getRecordMapping();
                if (recordMapping != null) {
                    dataSetFacts.copyToRecordMapping(recordMapping);
                    dataSetStore.setRecordMapping(recordMapping);
                }
            }
            catch (FileStoreException e) {
                userNotifier.tellUser("Unable to save mapping", e);
            }
        }

        @Override
        public void factChanged() {
            timer.restart();
        }

        @Override
        public void select(FieldMapping fieldMapping) {
            timer.restart();
        }

        @Override
        public void selectedChanged() {
            timer.restart();
        }

        @Override
        public void mappingChanged(RecordMapping recordMapping) {
            timer.restart();
        }
    }

    private static void checkWorkerThread() {
        if (SwingUtilities.isEventDispatchThread()) {
            throw new RuntimeException("Expected Worker thread");
        }
    }

    private static void checkSwingThread() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new RuntimeException("Expected Swing thread");
        }
    }
}
