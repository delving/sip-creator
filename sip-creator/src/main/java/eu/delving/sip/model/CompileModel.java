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

import eu.delving.groovy.DiscardRecordException;
import eu.delving.groovy.GroovyCodeResource;
import eu.delving.groovy.MappingRunner;
import eu.delving.groovy.MetadataRecord;
import eu.delving.groovy.XmlNodePrinter;
import eu.delving.metadata.FieldMapping;
import eu.delving.metadata.MappingModel;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecordMapping;
import eu.delving.metadata.RecordValidator;
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
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class CompileModel implements SipModel.ParseListener, MappingModel.Listener {
    public final static int COMPILE_DELAY = 500;
    private RecordMapping recordMapping;
    private FieldMapping selectedFieldMapping;
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

    public enum Type {
        RECORD("record mapping"),
        FIELD("field mapping");

        String s;
        Type(String s) {this.s = s;}
        public String toString() {return s;}
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

    @Override
    public void factChanged() {
        compileSoon();
    }

    @Override
    public void select(FieldMapping fieldMapping) {
        if (fieldMapping == selectedFieldMapping) {
            if (null != fieldMapping) {
                notifyStateChange(State.REGENERATED);
            }
        }
        else {
            selectedFieldMapping = fieldMapping;
            notifyStateChange(State.ORIGINAL);
        }
        Exec.swing(new DocumentSetter(codeDocument, getDisplayCode()));
        compileSoon();
    }

    @Override
    public void fieldMappingChanged() {
        compileSoon();
    }

    @Override
    public void recordMappingChanged(RecordMapping recordMapping) {
        compileSoon();
    }

    @Override
    public void recordMappingSelected(RecordMapping recordMapping) {
        this.recordMapping = recordMapping;
        this.editedCode = null;
        Exec.swing(new DocumentSetter(codeDocument, getDisplayCode()));
        notifyStateChange(State.ORIGINAL);
        compileSoon();
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
        if (selectedFieldMapping != null) {
            if (!selectedFieldMapping.codeLooksLike(code)) {
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

    @Override
    public void updatedRecord(MetadataRecord metadataRecord) {
        this.metadataRecord = metadataRecord;
        if (metadataRecord != null) {
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

    private Path getSelectedPath() {
        switch (type) {
            case RECORD:
                return null;
            case FIELD:
                return selectedFieldMapping == null ? null : selectedFieldMapping.getDefinition().path;
            default:
                throw new RuntimeException();
        }
    }

    private String getDisplayCode() {
        switch (type) {
            case RECORD:
                if (recordMapping != null) {
                    return recordMapping.toDisplayCode();
                }
                else {
                    return "// no mapping";
                }
            case FIELD:
                if (selectedFieldMapping == null) {
                    return "// no code";
                }
                else {
                    return recordMapping.toDisplayCode(getSelectedPath());
                }
            default:
                throw new RuntimeException();
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
                MappingRunner mappingRunner = new MappingRunner(groovyCodeResource, recordMapping, getSelectedPath(), editedCode);
                try {
                    Node outputNode = mappingRunner.runMapping(metadataRecord);
                    feedback.say("Compiled code for "+type);
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
                            if (selectedFieldMapping != null) {
                                selectedFieldMapping.setCode(editedCode);
                                notifyStateChange(State.COMMITTED);
                                editedCode = null;
                                notifyStateChange(State.SAVED);
                            }
                            else {
                                if (selectedFieldMapping != null) {
                                    selectedFieldMapping.setCode(editedCode);
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
                }
                catch (DiscardRecordException e) {
                    compilationComplete(e.getMessage());
                    if (selectedFieldMapping != null) {
                        if (editedCode != null) {
                            selectedFieldMapping.setCode(editedCode);
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
            if (selectedFieldMapping == null && type == Type.FIELD) {
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
        for (Listener listener : listeners) {
            listener.stateChanged(state);
        }
    }

    public interface Listener {
        void stateChanged(State state);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    private List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
}