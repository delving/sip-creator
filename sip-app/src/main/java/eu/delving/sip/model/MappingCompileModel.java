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

import eu.delving.MappingResult;
import eu.delving.groovy.*;
import eu.delving.metadata.*;
import eu.delving.sip.base.CompileState;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Validator;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static eu.delving.sip.model.MappingCompileModel.Type.RECORD;

/**
 * Hold the code/documentation/output Swing documents and generate, compile, and run the Groovy code of either an
 * entire mapping, or one stripped to show only one NodeMapping at a time.  Compilation is triggered automatically
 * when editing action in the input document stops for a time.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MappingCompileModel {
    public final static int RUN_DELAY = 100;
    public final static int COMPILE_DELAY = 500;
    private XmlSerializer serializer = new XmlSerializer();
    private RecMapping recMapping;
    private NodeMapping nodeMapping;
    private MetadataRecord metadataRecord;
    private Document codeDocument = new PlainDocument();
    private Document docDocument = new PlainDocument();
    private Document outputDocument = new PlainDocument();
    private TriggerTimer triggerTimer = new TriggerTimer();
    private Type type;
    private Validator validator;
    private GroovyCodeResource groovyCodeResource;
    private boolean enabled;
    private volatile boolean compiling;
    private ParseEar parseEar = new ParseEar();
    private boolean ignoreDocChanges;
    private MappingRunner mappingRunner;
    private MappingModelEar mappingModelEar = new MappingModelEar();
    private SipModel sipModel;

    public enum Type {
        RECORD("record mapping"),
        FIELD("field mapping");

        String s;

        Type(String s) {
            this.s = s;
        }

        public String toString() {
            return s;
        }
    }

    public MappingCompileModel(SipModel sipModel, Type type, GroovyCodeResource groovyCodeResource) {
        this.sipModel = sipModel;
        this.type = type;
        this.groovyCodeResource = groovyCodeResource;
        this.codeDocument.addDocumentListener(new DocChangeListener() {
            @Override
            public void run() {
                triggerCompile();
            }
        });
        this.docDocument.addDocumentListener(new DocChangeDelayedListener() {
            @Override
            public void later() {
                if (nodeMapping == null) return;
                nodeMapping.setDocumentation(StringUtil.documentToString(docDocument));
            }
        });
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        triggerCompile();
    }

    public void setNodeMapping(NodeMapping nodeMapping) {
        sipModel.exec(new DocumentSetter(docDocument, "", true));
        sipModel.exec(new DocumentSetter(codeDocument, "", true));
        sipModel.exec(new DocumentSetter(outputDocument, "", true));
        if ((this.nodeMapping = nodeMapping) != null) {
            sipModel.exec(new DocumentSetter(docDocument, nodeMapping.getDocumentation(), false));
            sipModel.exec(new Swing() {
                @Override
                public void run() {
                    String code = getCode(getEditPath(false));
                    new DocumentSetter(codeDocument, code, false).run();
                }
            });
        }
        notifyStateChange(CompileState.ORIGINAL);
    }

    public MappingModel.ChangeListener getMappingModelChangeListener() {
        return mappingModelEar;
    }

    public MappingModel.SetListener getMappingModelSetListener() {
        return mappingModelEar;
    }

    public SipModel.ParseListener getParseEar() {
        return parseEar;
    }

    public void setValidator(Validator validator) {
        this.validator = validator;
    }

    public Document getCodeDocument() {
        return codeDocument;
    }

    public Document getDocDocument() {
        return docDocument;
    }

    public Document getOutputDocument() {
        return outputDocument;
    }

    public String toString() {
        return type.toString();
    }

    // === privates

    private void triggerCompile() {
        if (!enabled) return;
        mappingRunner = null;
        triggerTimer.triggerSoon(COMPILE_DELAY);
    }

    private void triggerRun() {
        if (!enabled) return;
        triggerTimer.triggerSoon(RUN_DELAY);
    }

    private String getCode(EditPath editPath) {
        switch (type) {
            case RECORD:
                return recMapping == null ? "" : recMapping.toCode();
            case FIELD:
                return nodeMapping == null || recMapping == null ? "" : nodeMapping.getCode(editPath, recMapping);
            default:
                throw new RuntimeException();
        }
    }

    private EditPath getEditPath(final boolean fromCodeDocument) {
        if (nodeMapping == null) return null;
        String editedCode = null;
        if (fromCodeDocument) {
            editedCode = StringUtil.documentToString(codeDocument);
        }
        else if (nodeMapping.groovyCode != null) {
            editedCode = StringUtil.linesToString(nodeMapping.groovyCode);
        }
        return new EditPath(nodeMapping, editedCode);
    }

    private class ParseEar implements SipModel.ParseListener {

        @Override
        public void updatedRecord(MetadataRecord updated) {
            metadataRecord = updated;
            if (metadataRecord != null) {
                triggerRun();
            }
            else {
                sipModel.exec(new DocumentSetter(outputDocument, "", false));
            }
        }
    }

    private class MappingModelEar implements MappingModel.ChangeListener, MappingModel.SetListener {

        @Override
        public void recMappingSet(MappingModel mappingModel) {
            recMapping = mappingModel.getRecMapping();
            setNodeMapping(null);
            triggerCompile();
        }

        @Override
        public void lockChanged(MappingModel mappingModel, boolean locked) {
        }

        @Override
        public void functionChanged(MappingModel mappingModel, MappingFunction function) {
            triggerCompile();
        }

        @Override
        public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping, NodeMappingChange change) {
            switch (change) {
                case CODE:
                    if (nodeMapping.codeLooksLike(StringUtil.documentToString(codeDocument))) break;
                case OPERATOR:
                case DICTIONARY:
                    triggerCompile();
                    break;
            }
        }

        @Override
        public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            if (type == RECORD) triggerCompile();
        }

        @Override
        public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            if (type == RECORD) {
                triggerCompile();
            }
            else if (MappingCompileModel.this.nodeMapping != null && MappingCompileModel.this.nodeMapping == nodeMapping) {
                setNodeMapping(null);
            }
        }

        @Override
        public void populationChanged(MappingModel mappingModel, RecDefNode node) {
        }
    }

    private class MappingJob implements Work.DataSetPrefixWork {

        private EditPath editPath;

        private MappingJob(EditPath editPath) {
            this.editPath = editPath;
        }

        @Override
        public void run() {
            if (metadataRecord == null) return;
            compiling = true;
            try {
                if (mappingRunner == null) {
                    mappingRunner = new MappingRunner(groovyCodeResource, recMapping, editPath);
                    notifyCodeCompiled(mappingRunner.getCode());
                }
                try {
                    Node node = mappingRunner.runMapping(metadataRecord);
                    if (node == null) return;
                    String output = serializer.toXml(node, true);
                    if (validator != null) {
                        ForgivingErrorHandler handler = new ForgivingErrorHandler();
                        validator.setErrorHandler(handler);
                        try {
                            validator.validate(new DOMSource(node));
                            MappingResult result = new MappingResultImpl(serializer, node, recMapping.getRecDefTree()).resolve();
                            result.checkMissingFields();
                            compilationComplete(output, handler.getError());
                        }
                        catch (SAXException e) {
                            compilationComplete(output, handler.getError());
                        }
                        catch (MappingResult.MissingFieldsException e) {
                            compilationComplete(output, e.getMessage());
                        }
                        finally {
                            handler.reset();
                        }
                    }
                    else {
                        compilationComplete(output, null);
                    }
                    setMappingCode();
                }
                catch (DiscardRecordException e) {
                    compilationComplete("Discarded explicitly", e.getMessage());
                    setMappingCode();
                }
            }
            catch (Exception e) {
                compilationComplete("No output available", e.getMessage());
                notifyStateChange(CompileState.ERROR);
            }
            finally {
                compiling = false;
            }
        }

        private void setMappingCode() {
            if (nodeMapping != null && nodeMapping.isUserCodeEditable() && !recMapping.isLocked()) {
                String editedCode = StringUtil.documentToString(codeDocument);
                nodeMapping.setGroovyCode(editedCode, recMapping);
                notifyStateChange(nodeMapping.groovyCode == null ? CompileState.ORIGINAL : CompileState.SAVED);
            }
        }

        private void compilationComplete(String result, String error) {
            if (error != null) result = String.format("## VALIDATION ERROR! ##\n%s\n\n## OUTPUT ##\n%s", error, result);
            sipModel.exec(new DocumentSetter(outputDocument, result, false));
        }

        public String toString() {
            return type.toString();
        }

        @Override
        public Job getJob() {
            return Job.COMPILE_NODE_MAPPING;
        }

        @Override
        public String getPrefix() {
            return recMapping == null ? "" : recMapping.getPrefix();
        }

        @Override
        public DataSet getDataSet() {
            return sipModel.getDataSetModel().getDataSet();
        }
    }

    private class DocumentSetter implements Swing {

        private Document document;
        private String content;
        private boolean ignore;

        private DocumentSetter(Document document, String content, boolean ignore) {
            this.document = document;
            this.content = content;
            this.ignore = ignore;
        }

        @Override
        public void run() {
            ignoreDocChanges = ignore;
            int docLength = document.getLength();
            try {
                document.remove(0, docLength);
                document.insertString(0, content, null);
                ignoreDocChanges = false;
            }
            catch (BadLocationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private class TriggerTimer implements ActionListener {
        private Timer timer = new Timer(COMPILE_DELAY, this);

        private TriggerTimer() {
            timer.setRepeats(false);
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            if (compiling) return;
            if (nodeMapping == null && type == Type.FIELD) {
                try {
                    outputDocument.remove(0, outputDocument.getLength() - 1);
                    return;
                }
                catch (BadLocationException e) {
                    throw new RuntimeException(e);
                }
            }
            sipModel.exec(new MappingJob(getEditPath(true)));
        }

        public void triggerSoon(int delay) {
            timer.setInitialDelay(delay);
            timer.restart();
        }
    }

    private abstract class DocChangeListener implements DocumentListener, Work {

        @Override
        public Job getJob() {
            return Job.MAPPING_DOCUMENT_CHANGED;
        }

        @Override
        public void insertUpdate(DocumentEvent documentEvent) {
            go();
        }

        @Override
        public void removeUpdate(DocumentEvent documentEvent) {
            go();
        }

        @Override
        public void changedUpdate(DocumentEvent documentEvent) {
            go();
        }

        private void go() {
            if (!ignoreDocChanges) sipModel.exec(this);
        }
    }

    private abstract class DocChangeDelayedListener extends DocChangeListener implements ActionListener {
        private Timer laterTimer = new Timer(200, this);

        DocChangeDelayedListener() {
            laterTimer.setRepeats(false);
        }

        @Override
        public Job getJob() {
            return Job.MAPPING_DOCUMENT_CHANGED;
        }

        @Override
        public void run() {
            laterTimer.restart();
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            sipModel.exec(new Work() {
                @Override
                public void run() {
                    later();
                }

                @Override
                public Job getJob() {
                    return Job.MAPPING_DOCUMENT_CHANGED;
                }
            });
        }

        public abstract void later();

    }

    public class ForgivingErrorHandler implements ErrorHandler {

        private StringBuilder error = new StringBuilder();

        public void warning(SAXParseException ex) {
            error.append(ex.getMessage()).append('\n');
        }

        public void error(SAXParseException ex) {
            error.append(ex.getMessage()).append('\n');
        }

        public void fatalError(SAXParseException ex) throws SAXException {
            error.append(ex.getMessage()).append('\n');
        }

        public String getError() {
            if (error.length() == 0) return null;
            return error.toString();
        }

        public void reset() {
            error.setLength(0);
        }
    }

    private void notifyStateChange(final CompileState state) {
        for (Listener listener : listeners) listener.stateChanged(state);
    }

    private void notifyCodeCompiled(String code) {
        for (Listener listener : listeners) listener.codeCompiled(type, code);
    }

    public interface Listener {
        void stateChanged(CompileState state);
        void codeCompiled(Type type, String code);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    private List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
}