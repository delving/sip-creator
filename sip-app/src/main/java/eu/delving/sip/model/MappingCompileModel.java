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
import eu.delving.groovy.MappingRunner;
import eu.delving.groovy.MetadataRecord;
import eu.delving.groovy.XmlSerializer;
import eu.delving.metadata.*;
import eu.delving.sip.base.CompileState;
import eu.delving.sip.base.Exec;
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
 * This model is behind the scenario with input data, groovy code, and output record
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MappingCompileModel {
    public final static int RUN_DELAY = 100;
    public final static int COMPILE_DELAY = 500;
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
    private Feedback feedback;
    private boolean enabled;
    private volatile boolean compiling;
    private ParseEar parseEar = new ParseEar();
    private boolean ignoreDocChanges;
    private MappingRunner mappingRunner;
    private MappingModelEar mappingModelEar = new MappingModelEar();

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

    public MappingCompileModel(Type type, Feedback feedback, GroovyCodeResource groovyCodeResource) {
        this.type = type;
        this.feedback = feedback;
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
        if (enabled) triggerCompile();
    }

    public void setNodeMapping(NodeMapping nodeMapping) {
        this.nodeMapping = nodeMapping;
        Exec.swing(new DocumentSetter(docDocument, this.nodeMapping != null ? this.nodeMapping.getDocumentation() : "", false));
        Exec.swing(new DocumentSetter(codeDocument, getCode(getEditPath(false)), false));
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
                if (recMapping == null) {
                    return "// no mapping";
                }
                else {
                    return recMapping.toCode(null);
                }
            case FIELD:
                if (nodeMapping == null || recMapping == null) {
                    return "// no code";
                }
                else {
                    return nodeMapping.getCode(editPath, recMapping);
                }
            default:
                throw new RuntimeException();
        }
    }

    private EditPath getEditPath(final boolean fromCodeDocument) {
        if (nodeMapping == null) return null;
        return new EditPath() {
            @Override
            public NodeMapping getNodeMapping() {
                return nodeMapping;
            }

            @Override
            public String getEditedCode() {
                if (fromCodeDocument) {
                    return StringUtil.documentToString(codeDocument);
                }
                else if (nodeMapping.groovyCode != null) {
                    return StringUtil.linesToString(nodeMapping.groovyCode);
                }
                else {
                    return null;
                }
            }
        };
    }

    private class ParseEar implements SipModel.ParseListener {

        @Override
        public void updatedRecord(MetadataRecord updated) {
            metadataRecord = updated;
            if (metadataRecord != null) {
                triggerRun();
            }
            else {
                Exec.swing(new DocumentSetter(outputDocument, "No input", false));
            }
        }
    }

    private class MappingModelEar implements MappingModel.ChangeListener, MappingModel.SetListener {

        @Override
        public void recMappingSet(MappingModel mappingModel) {
            recMapping = mappingModel.getRecMapping();
            compileSoon();
        }

        @Override
        public void functionChanged(MappingModel mappingModel, MappingFunction function) {
            compileSoon();
        }

        @Override
        public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            if (!nodeMapping.codeLooksLike(StringUtil.documentToString(codeDocument))) {
                compileSoon();
            }
        }

        @Override
        public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            if (type == RECORD) compileSoon();
        }

        @Override
        public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            if (type == RECORD) {
                compileSoon();
            }
            else if (MappingCompileModel.this.nodeMapping != null && MappingCompileModel.this.nodeMapping == nodeMapping) {
                setNodeMapping(null);
            }
        }

        private void compileSoon() {
            triggerCompile();
        }
    }

    private class MappingJob implements Runnable {

        @Override
        public void run() {
            if (metadataRecord == null) return;
            compiling = true;
            try {
                if (mappingRunner == null) {
                    feedback.say("Compiling " + type);
                    mappingRunner = new MappingRunner(groovyCodeResource, recMapping, getEditPath(true));
                    System.out.println(mappingRunner.getCode()); // todo: remove
                }
                try {
                    Node node = mappingRunner.runMapping(metadataRecord);
                    if (node == null) return;
                    String output = XmlSerializer.toXml(node);
                    if (validator != null) {
                        ForgivingErrorHandler handler = new ForgivingErrorHandler();
                        validator.setErrorHandler(handler);
                        try {
                            validator.validate(new DOMSource(node));
                            compilationComplete(output, handler.getError());
                        }
                        catch (SAXException e) {
                            compilationComplete(output, handler.getError());
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
                catch (AssertionError e) {
                    compilationComplete("Discarded explicitly with 'assert'", e.getMessage());
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
            if (nodeMapping != null && nodeMapping.isUserCodeEditable()) {
                String editedCode = StringUtil.documentToString(codeDocument);
                nodeMapping.setGroovyCode(editedCode, recMapping);
                notifyStateChange(nodeMapping.groovyCode == null ? CompileState.ORIGINAL : CompileState.SAVED);
            }
        }

        private void compilationComplete(String result, String error) {
            if (error != null) result = String.format("## VALIDATION ERROR! ##\n%s\n\n## OUTPUT ##\n%s", error, result);
            Exec.swing(new DocumentSetter(outputDocument, result, false));
        }

        public String toString() {
            return type.toString();
        }
    }

    private class DocumentSetter implements Runnable {

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
            Exec.work(new MappingJob());
        }

        public void triggerSoon(int delay) {
            timer.setInitialDelay(delay);
            timer.restart();
        }
    }

    private abstract class DocChangeListener implements DocumentListener, Runnable {

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
            if (!ignoreDocChanges) Exec.work(this);
        }
    }

    private abstract class DocChangeDelayedListener extends DocChangeListener implements ActionListener {
        private Timer laterTimer = new Timer(200, this);

        DocChangeDelayedListener() {
            laterTimer.setRepeats(false);
        }

        @Override
        public void run() {
            laterTimer.restart();
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            Exec.work(new Runnable() {
                @Override
                public void run() {
                    later();
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

    public interface Listener {
        void stateChanged(CompileState state);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    private List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
}