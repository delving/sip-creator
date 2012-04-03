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

import eu.delving.groovy.*;
import eu.delving.metadata.*;
import eu.delving.sip.base.Exec;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.swing.Timer;
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
    private NodeMapping selectedNodeMapping;
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

    public enum State {
        ORIGINAL,
        SAVED,
        EDITED,
        ERROR,
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
        this.docDocument.addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                pushIt();
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
                pushIt();
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
                pushIt();
            }

            private void pushIt() {
                if (selectedNodeMapping != null) selectedNodeMapping.setDocumentation(StringUtil.documentToString(docDocument));
            }
        });
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) triggerCompile();
    }

    public void setNodeMapping(NodeMapping nodeMapping) {
        this.selectedNodeMapping = nodeMapping;
        Exec.swing(new DocumentSetter(docDocument, nodeMapping != null ? nodeMapping.getDocumentation() : "", false));
        Exec.swing(new DocumentSetter(codeDocument, getGeneratedCode(), false));
        notifyStateChange(State.ORIGINAL);
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

    private String getGeneratedCode() {
        switch (type) {
            case RECORD:
                if (recMapping == null) {
                    return "// no mapping";
                }
                else {
                    return recMapping.toCode(null);
                }
            case FIELD:
                if (selectedNodeMapping == null || recMapping == null) {
                    return "// no code";
                }
                else {
                    return selectedNodeMapping.getGeneratedCode();
                }
            default:
                throw new RuntimeException();
        }
    }

    private EditPath getEditPath() {
        if (type == RECORD || selectedNodeMapping == null) return null;
        return new EditPath() {
            @Override
            public Path getPath() {
                return selectedNodeMapping.outputPath;
            }

            @Override
            public String getEditedCode() {
                return StringUtil.documentToString(codeDocument);
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
            if (type == RECORD) compileSoon();
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
            else if (selectedNodeMapping == nodeMapping) {
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
                    feedback.say("Compiling code for " + type);
                    mappingRunner = new MappingRunner(groovyCodeResource, recMapping, getEditPath());
                }
                try {
                    Node node = mappingRunner.runMapping(metadataRecord);
                    if (node == null) return;
                    String output = XmlSerializer.toXml(node);
                    if (validator != null) {
                        try {
                            validator.validate(new DOMSource(node));
                            compilationComplete(output, null);
                        }
                        catch (SAXException e) {
                            compilationComplete(output, e.getMessage());
                        }
                    }
                    else {
                        compilationComplete(output, null);
                        setMappingCode();
                    }
                }
                catch (DiscardRecordException e) {
                    compilationComplete("No output", e.getMessage());
                    setMappingCode();
                }
            }
            catch (Exception e) {
                compilationComplete("No output available", e.getMessage());
                notifyStateChange(State.ERROR);
            }
            finally {
                compiling = false;
            }
        }

        private void setMappingCode() {
            if (selectedNodeMapping != null && selectedNodeMapping.isUserCodeEditable()) {
                String editedCode = StringUtil.documentToString(codeDocument);
                if (!selectedNodeMapping.codeLooksLike(editedCode)) {
                    selectedNodeMapping.setGroovyCode(editedCode);
                }
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
            if (selectedNodeMapping == null && type == Type.FIELD) {
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


    private void notifyStateChange(final State state) {
        for (Listener listener : listeners) listener.stateChanged(state);
    }

    public interface Listener {
        void stateChanged(State state);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    private List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
}