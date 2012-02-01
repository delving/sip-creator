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
import groovy.util.Node;

import javax.swing.Timer;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This model is behind the scenario with input data, groovy code, and output record
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class CompileModel {
    public final static int COMPILE_DELAY = 500;
    private RecMapping recMapping;
    private NodeMapping selectedNodeMapping;
    private MetadataRecord metadataRecord;
    private Document codeDocument = new PlainDocument();
    private Document outputDocument = new PlainDocument();
    private CompileTimer compileTimer = new CompileTimer();
    private Type type;
    private RecordValidator recordValidator;
    private String editedCode;
    private GroovyCodeResource groovyCodeResource;
    private Feedback feedback;
    private boolean enabled;
    private volatile boolean compiling;
    private ParseEar parseEar = new ParseEar();
    private MappingModelEar mappingModelEar = new MappingModelEar();
    private CreateModelEar createModelEar = new CreateModelEar();

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
        UNCOMPILED,
        SAVED,
        EDITED,
        ERROR,
        COMMITTED,
        REGENERATED
    }

    public CompileModel(Type type, Feedback feedback, GroovyCodeResource groovyCodeResource) {
        this.type = type;
        this.feedback = feedback;
        this.groovyCodeResource = groovyCodeResource;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) compileSoon();
    }

    public MappingModel.ChangeListener getMappingModelChangeListener() {
        return mappingModelEar;
    }
    
    public MappingModel.SetListener getMappingModelSetListener() {
        return mappingModelEar;
    }

    public CreateModel.Listener getCreateModelEar() {
        return createModelEar;
    }

    public SipModel.ParseListener getParseEar() {
        return parseEar;
    }

    public void setRecordValidator(RecordValidator recordValidator) {
        this.recordValidator = recordValidator;
    }

    public void refreshCode() {
        Exec.swing(new DocumentSetter(codeDocument, getDisplayCode()));
        compileSoon();
    }

    public void compileSoon() {
        if (enabled) {
            compileTimer.triggerSoon();
        }
    }

    public void setCode(String code) {
        if (selectedNodeMapping != null) {
            if (!selectedNodeMapping.codeLooksLike(code)) {
                editedCode = code;
                notifyStateChange(State.EDITED);
            }
            else {
                editedCode = null;
                notifyStateChange(State.SAVED);
            }
            compileSoon();
        }
    }

    public Document getCodeDocument() {
        return codeDocument;
    }

    public Document getOutputDocument() {
        return outputDocument;
    }

    public String toString() {
        return type.toString();
    }

    // === privates

    private Path getSelectedOutputPath() {
        switch (type) {
            case RECORD:
                return null;
            case FIELD:
                return selectedNodeMapping == null ? null : selectedNodeMapping.outputPath;
            default:
                throw new RuntimeException();
        }
    }

    private String getDisplayCode() {
        switch (type) {
            case RECORD:
                if (recMapping != null) {
                    return recMapping.toCode(null, null);
                }
                else {
                    return "// no mapping";
                }
            case FIELD:
                if (selectedNodeMapping == null || recMapping == null) {
                    return "// no code";
                }
                else {
                    return selectedNodeMapping.getUserCode(editedCode);
                }
            default:
                throw new RuntimeException();
        }
    }

    private class ParseEar implements SipModel.ParseListener {

        @Override
        public void updatedRecord(MetadataRecord updated) {
            metadataRecord = updated;
            if (metadataRecord != null) {
                compileSoon();
            }
            else {
                Exec.swing(new DocumentSetter(outputDocument, ""));
            }
        }
    }

    private class CreateModelEar implements CreateModel.Listener {

        @Override
        public void statsTreeNodeSet(CreateModel createModel) {
        }

        @Override
        public void recDefTreeNodeSet(CreateModel createModel) {
        }

        @Override
        public void nodeMappingSet(CreateModel createModel) {
            if (createModel.getNodeMapping() == selectedNodeMapping) {
                if (selectedNodeMapping != null) notifyStateChange(State.REGENERATED);
            }
            else {
                selectedNodeMapping = createModel.getNodeMapping();
                notifyStateChange(State.ORIGINAL);
            }
            Exec.swing(new DocumentSetter(codeDocument, getDisplayCode()));
            compileSoon();
        }

        @Override
        public void nodeMappingChanged(CreateModel createModel) {
            // todo: may need to create dictionary lookup code or freshly generate user code
        }
    }

    private class MappingModelEar implements MappingModel.ChangeListener, MappingModel.SetListener {

        @Override
        public void recMappingSet(MappingModel mappingModel) {
            recMapping = mappingModel.getRecMapping();
            compileSoon();
        }

        @Override
        public void factChanged(MappingModel mappingModel, String name) {
            compileSoon();
        }

        @Override
        public void functionChanged(MappingModel mappingModel, MappingFunction function) {
            compileSoon();
        }

        @Override
        public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            compileSoon();
        }

        @Override
        public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            editedCode = null;
            selectedNodeMapping = nodeMapping;
            Exec.swing(new DocumentSetter(codeDocument, getDisplayCode()));
            notifyStateChange(State.ORIGINAL);
            compileSoon();
        }

        @Override
        public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            editedCode = null;
            selectedNodeMapping = null; // todo: test whether it's the same one??
            Exec.swing(new DocumentSetter(codeDocument, getDisplayCode()));
            notifyStateChange(State.ORIGINAL);
            compileSoon();
        }
    }

    private class CompilationRunner implements Runnable {

        @Override
        public void run() {
            if (metadataRecord == null) {
                return;
            }
            compiling = true;
            try {
                MappingRunner mappingRunner = new MappingRunner(groovyCodeResource, recMapping, getSelectedOutputPath(), editedCode);
                try {
                    Node outputNode = mappingRunner.runMapping(metadataRecord);
                    feedback.say("Compiled code for " + type);
                    if (null == outputNode) {
                        return;
                    }
                    if (recordValidator != null) {
                        recordValidator.validateRecord(outputNode, metadataRecord.getRecordNumber());
                        String output = XmlNodePrinter.serialize(outputNode);
                        compilationComplete(output);
                    }
                    else {
                        String output = XmlNodePrinter.serialize(outputNode);
                        compilationComplete(output);
                        if (editedCode == null) {
                            notifyStateChange(State.SAVED);
                        }
                        else {
                            if (selectedNodeMapping != null) {
                                selectedNodeMapping.setGroovyCode(editedCode);
                                notifyStateChange(State.COMMITTED);
                                editedCode = null;
                                notifyStateChange(State.SAVED);
                            }
                            else {
                                notifyStateChange(State.EDITED);
                            }
                        }
                    }
                }
                catch (DiscardRecordException e) {
                    compilationComplete(e.getMessage());
                    if (selectedNodeMapping != null) {
                        if (editedCode != null) {
                            selectedNodeMapping.setGroovyCode(editedCode);
                            notifyStateChange(State.COMMITTED);
                            editedCode = null;
                        }
                        notifyStateChange(State.SAVED);
                    }
                    else {
                        notifyStateChange(State.EDITED);
                    }
                }
            }
            catch (Exception e) {
                compilationComplete(e.getMessage());
                notifyStateChange(State.ERROR);
            }
            finally {
                compiling = false;
            }
        }

        private void compilationComplete(final String result) {
            Exec.swing(new DocumentSetter(outputDocument, result));
        }

        public String toString() {
            return type.toString();
        }
    }

    private class DocumentSetter implements Runnable {

        private Document document;
        private String content;

        private DocumentSetter(Document document, String content) {
            this.document = document;
            this.content = content;
        }

        @Override
        public void run() {
            if (document instanceof HTMLDocument) {
                HTMLDocument htmlDocument = (HTMLDocument) document;
                int docLength = document.getLength();
                try {
                    document.remove(0, docLength);
                    HTMLEditorKit.ParserCallback callback = htmlDocument.getReader(0);
                    htmlDocument.getParser().parse(new StringReader(content), callback, true);
                    callback.flush();
                }
                catch (BadLocationException e) {
                    throw new RuntimeException(e);
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            else {
                int docLength = document.getLength();
                try {
                    document.remove(0, docLength);
                    document.insertString(0, content, null);
                }
                catch (BadLocationException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private class CompileTimer implements ActionListener {
        private Timer timer = new Timer(COMPILE_DELAY, this);

        private CompileTimer() {
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
            Exec.work(new CompilationRunner());
        }

        public void triggerSoon() {
            timer.restart();
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