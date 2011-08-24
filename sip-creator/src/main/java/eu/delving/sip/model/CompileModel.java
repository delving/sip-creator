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
import eu.delving.groovy.MappingException;
import eu.delving.groovy.MappingRunner;
import eu.delving.groovy.MetadataRecord;
import eu.delving.groovy.XmlNodePrinter;
import eu.delving.metadata.FieldMapping;
import eu.delving.metadata.MappingModel;
import eu.delving.metadata.MetadataModel;
import eu.delving.metadata.RecordMapping;
import eu.delving.metadata.RecordValidator;
import eu.delving.metadata.ValidationException;
import groovy.util.Node;

import javax.swing.SwingUtilities;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This model is behind the scenario with input data, groovy code, and output record
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class CompileModel implements SipModel.ParseListener, MappingModel.Listener {
    public final static int COMPILE_DELAY = 500;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private RecordMapping recordMapping;
    private FieldMapping selectedFieldMapping;
    private MetadataRecord metadataRecord;
    private Document codeDocument = new PlainDocument();
    private Document outputDocument = new PlainDocument();
    private CompileTimer compileTimer = new CompileTimer();
    private MetadataModel metadataModel;
    private Type type;
    private RecordValidator recordValidator;
    private String editedCode;
    private GroovyCodeResource groovyCodeResource;

    public enum Type {
        RECORD,
        FIELD
    }

    public enum State {
        UNCOMPILED,
        PRISTINE,
        EDITED,
        ERROR,
        COMMITTED,
        REGENERATED
    }

    public CompileModel(Type type, MetadataModel metadataModel, GroovyCodeResource groovyCodeResource) {
        this.type = type;
        this.metadataModel = metadataModel;
        this.groovyCodeResource = groovyCodeResource;
    }

    @Override
    public void factChanged() {
        compileSoon();
    }

    @Override
    public void select(FieldMapping fieldMapping) {
        if (fieldMapping == selectedFieldMapping) {
            notifyStateChange(State.REGENERATED);
        }
        else {
            selectedFieldMapping = fieldMapping;
            notifyStateChange(State.PRISTINE);
        }
        SwingUtilities.invokeLater(new DocumentSetter(codeDocument, getDisplayCode()));
        compileSoon();
    }

    @Override
    public void selectedChanged() {
        compileSoon();
    }

    @Override
    public void mappingChanged(RecordMapping recordMapping) {
        this.recordMapping = recordMapping;
        this.editedCode = null;
        SwingUtilities.invokeLater(new DocumentSetter(codeDocument, getDisplayCode()));
        notifyStateChange(State.PRISTINE);
        compileSoon();
    }

    public void setRecordValidator(RecordValidator recordValidator) {
        this.recordValidator = recordValidator;
    }

    public void refreshCode() {
        SwingUtilities.invokeLater(new DocumentSetter(codeDocument, getDisplayCode()));
        compileSoon();
    }

    public void compileSoon() {
        compileTimer.triggerSoon();
    }

    public void setCode(String code) {
        if (selectedFieldMapping != null) {
            if (!selectedFieldMapping.codeLooksLike(code)) {
                editedCode = code;
                notifyStateChange(State.EDITED);
            }
            else {
                editedCode = null;
                notifyStateChange(State.PRISTINE);
            }
        }
        compileSoon();
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

    private String getSelectedPath() {
        return selectedFieldMapping == null ? null : selectedFieldMapping.getDefinition().path.toString();
    }

    private String getDisplayCode() {
        switch (type) {
            case RECORD:
                if (recordMapping != null) {
                    return recordMapping.toDisplayCode(metadataModel);
                }
                else {
                    return "// no mapping";
                }
            case FIELD:
                if (selectedFieldMapping == null) {
                    return "// no code";
                }
                else {
                    return recordMapping.toDisplayCode(metadataModel, getSelectedPath());
                }
            default:
                throw new RuntimeException();
        }
    }

    private String getCompileCode() {
        switch (type) {
            case RECORD:
                return recordMapping != null ? recordMapping.toCompileCode(metadataModel) : "";
            case FIELD:
                return selectedFieldMapping != null ? recordMapping.toCompileCode(metadataModel, getSelectedPath()) : "";
            default:
                throw new RuntimeException();
        }
    }

    private String getCompileCode(String editedCode) {
        if (type == Type.RECORD) {
            throw new RuntimeException();
        }
        if (selectedFieldMapping == null) {
            return "print 'nothing selected'";
        }
        else {
            return recordMapping.toCompileCode(metadataModel, getSelectedPath(), editedCode);
        }
    }

    private class CompilationRunner implements Runnable {

        @Override
        public void run() {
            if (metadataRecord == null) {
                return;
            }
            String mappingCode = editedCode == null ? getCompileCode() : getCompileCode(editedCode);
            MappingRunner mappingRunner = new MappingRunner(groovyCodeResource, mappingCode);
            try {
                try {
                    Node outputNode = mappingRunner.runMapping(metadataRecord);
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
                            notifyStateChange(State.PRISTINE);
                        }
                        else {
                            if (selectedFieldMapping != null) {
                                selectedFieldMapping.setCode(editedCode);
                                notifyStateChange(State.COMMITTED);
                                editedCode = null;
                                notifyStateChange(State.PRISTINE);
                            }
                            else {
                                notifyStateChange(State.EDITED);
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
                        notifyStateChange(State.PRISTINE);
                    }
                    else {
                        notifyStateChange(State.EDITED);
                    }
                }
            }
            catch (MappingException e) {
                compilationComplete(e.getMessage());
                notifyStateChange(State.ERROR);
            }
            catch (ValidationException e) {
                compilationComplete(e.toString());
                notifyStateChange(State.ERROR);
            }
        }

        private void compilationComplete(final String result) {
            SwingUtilities.invokeLater(new DocumentSetter(outputDocument, result));
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

        @Override
        public void actionPerformed(ActionEvent e) {
            timer.stop();
            executor.execute(new CompilationRunner());
        }

        public void triggerSoon() {
            timer.restart();
        }
    }

    private void notifyStateChange(State state) {
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