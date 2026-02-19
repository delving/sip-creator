/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.sip.model;

import eu.delving.groovy.DiscardRecordException;
import eu.delving.groovy.GroovyCodeResource;
import eu.delving.groovy.LanguageTagException;
import eu.delving.groovy.MappingRunner;
import eu.delving.groovy.AppMappingRunner;
import eu.delving.groovy.MetadataRecord;
import eu.delving.groovy.XmlSerializer;
import eu.delving.metadata.AssertionTest;
import eu.delving.metadata.CodeGenerator;
import eu.delving.metadata.EditPath;
import eu.delving.metadata.JenaHelper;
import eu.delving.metadata.MappingFunction;
import eu.delving.metadata.MappingResult;
import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.NodeMappingChange;
import eu.delving.metadata.RecDefNode;
import eu.delving.metadata.RecMapping;
import eu.delving.metadata.StructureTest;
import eu.delving.sip.Application;
import eu.delving.sip.base.CompileState;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import org.apache.jena.riot.RDFFormat;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactoryConfigurationException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static eu.delving.metadata.StringUtil.documentToString;
import static eu.delving.metadata.StringUtil.isSimilarCode;
import static eu.delving.sip.files.Storage.XSD_VALIDATION;
import static eu.delving.sip.model.MappingCompileModel.Type.RECORD;

/**
 * Hold the code/documentation/output Swing documents and generate, compile, and
 * run the Groovy code of either an
 * entire mapping, or one stripped to show only one NodeMapping at a time.
 * Compilation is triggered automatically
 * when editing action in the input document stops for a time.
 *
 *
 */

public class MappingCompileModel {
    public final static int RUN_DELAY = 100;
    public final static int COMPILE_DELAY = 500;
    private XmlSerializer serializer = new XmlSerializer();
    private RecMapping recMapping;
    private NodeMapping nodeMapping;
    private MetadataRecord metadataRecord;
    private RSyntaxDocument codeDocument = new RSyntaxDocument(SyntaxConstants.SYNTAX_STYLE_GROOVY);
    private RSyntaxDocument docDocument = new RSyntaxDocument(SyntaxConstants.SYNTAX_STYLE_XML);
    private RSyntaxDocument outputDocument = new RSyntaxDocument(SyntaxConstants.SYNTAX_STYLE_XML);
    private RSyntaxTextArea outputArea;
    private TriggerTimer triggerTimer = new TriggerTimer();
    private Type type;
    private Validator validator;
    private GroovyCodeResource groovyCodeResource;
    private boolean enabled;
    private volatile boolean compiling;
    private ParseEar parseEar = new ParseEar();
    private boolean ignoreDocChanges;
    private MappingRunner MappingRunner;
    private MappingModelEar mappingModelEar = new MappingModelEar();
    private SipModel sipModel;
    private boolean trace;
    private List<AssertionTest> assertions;
    private RDFFormat rdfFormat = RDFFormat.RDFXML;

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
                if (nodeMapping == null)
                    return;
                nodeMapping.setDocumentation(documentToString(docDocument));
            }
        });
    }

    public Document setOutputDocument(String style, RSyntaxTextArea outputArea, RDFFormat rdfFormat) {
        outputDocument = new RSyntaxDocument(style);
        this.outputArea = outputArea;
        this.rdfFormat = rdfFormat;
        return outputDocument;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        triggerCompile();
    }

    public void setTrace(boolean trace) {
        this.trace = trace;
        triggerCompile();
    }

    public void setNodeMapping(final NodeMapping nodeMapping) {
        sipModel.exec(new DocumentSetter(docDocument, "", true));
        sipModel.exec(new DocumentSetter(codeDocument, "", true));
        sipModel.exec(new DocumentSetter(outputDocument, "", true));
        if (recMapping != null && (this.nodeMapping = nodeMapping) != null) {
            sipModel.exec(new DocumentSetter(docDocument, nodeMapping.getDocumentation(), false));
            sipModel.exec(new Swing() {
                @Override
                public void run() {
                    String code;
                    switch (type) {
                        case RECORD:
                            code = new CodeGenerator(recMapping).withTrace(trace).toRecordMappingCode();
                            break;
                        case FIELD:
                            EditPath editPath = new EditPath(
                                    nodeMapping,
                                    nodeMapping.getGroovyCode());
                            code = new CodeGenerator(recMapping).withTrace(trace).withEditPath(editPath)
                                    .toNodeMappingCode();
                            break;
                        default:
                            throw new RuntimeException();
                    }
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

    public void setAssertions(List<AssertionTest> assertions) {
        this.assertions = assertions;
    }

    public RSyntaxDocument getCodeDocument() {
        return codeDocument;
    }

    public RSyntaxDocument getDocDocument() {
        return docDocument;
    }

    public RSyntaxDocument getOutputDocument() {
        return outputDocument;
    }

    public String toString() {
        return type.toString();
    }

    // === privates

    public void triggerCompile() {
        if (!enabled)
            return;
        MappingRunner = null;
        triggerTimer.triggerSoon(COMPILE_DELAY);
    }

    private void triggerRun() {
        if (!enabled)
            return;
        triggerTimer.triggerSoon(RUN_DELAY);
    }

    private class ParseEar implements SipModel.ParseListener {

        @Override
        public void updatedRecord(MetadataRecord updated) {
            metadataRecord = updated;
            if (metadataRecord != null) {
                triggerRun();
            } else {
                sipModel.exec(new DocumentSetter(outputDocument, "", false));
            }
        }
    }

    private class MappingModelEar implements MappingModel.ChangeListener, MappingModel.SetListener {

        @Override
        public void recMappingSet(MappingModel mappingModel) {
            recMapping = mappingModel.getRecMapping();
            groovyCodeResource.resetClassLoader();
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
        public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping,
                NodeMappingChange change) {
            switch (change) {
                case CODE:
                    if (nodeMapping.codeLooksLike(documentToString(codeDocument)))
                        break;
                case OPERATOR:
                case DICTIONARY:
                    triggerCompile();
                    break;
            }
        }

        @Override
        public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            if (type == RECORD)
                triggerCompile();
        }

        @Override
        public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            if (type == RECORD) {
                triggerCompile();
            } else if (MappingCompileModel.this.nodeMapping != null
                    && MappingCompileModel.this.nodeMapping == nodeMapping) {
                setNodeMapping(null);
            }
        }

        @Override
        public void populationChanged(MappingModel mappingModel, RecDefNode node) {
        }
    }

    private enum Completion {
        JUST_FINE,
        UNVALIDATED,
        STRUCTURE_VIOLATION,
        SCHEMA_VIOLATION,
        CONTENT_VIOLATION,
        MISSING_FIELD,
        DISCARDED_RECORD,
        UNEXPECTED,
        RDF_VIOLATION
    }

    private class MappingJob implements Work.DataSetPrefixWork {

        private EditPath editPath;

        private MappingJob(EditPath editPath) {
            this.editPath = editPath;
        }

        @Override
        public void run() {
            groovyCodeResource.clearMappingScripts();
            if (metadataRecord == null)
                return;
            compiling = true;
            try {
                if (MappingRunner == null) {
                    MappingRunner = new AppMappingRunner(groovyCodeResource, recMapping, editPath, trace);
                    notifyCodeCompiled(MappingRunner.getCode());
                }
                try {
                    Node node = MappingRunner.runMapping(metadataRecord);
                    if (node == null)
                        return;
                    boolean enableXSDValidation = sipModel.getPreferences().getProperty(XSD_VALIDATION, "false")
                            .contentEquals("true");
                    if (validator != null && enableXSDValidation) {
                        ForgivingErrorHandler handler = new ForgivingErrorHandler();
                        validator.setErrorHandler(handler);
                        try {
                            validator.validate(new DOMSource(node));
                            handler.checkErrors();
                            MappingResult result = new MappingResult(serializer, metadataRecord.getId(), node,
                                    recMapping.getRecDefTree());
                            List<String> uriErrors = result.getUriErrors();
                            if (!uriErrors.isEmpty()) {
                                StringBuilder out = new StringBuilder();
                                for (String uriError : uriErrors) {
                                    out.append(uriError).append("\n");
                                }
                                compilationComplete(Completion.CONTENT_VIOLATION, node, out.toString());
                            } else {
                                StringBuilder out = new StringBuilder();
                                for (AssertionTest test : assertions) {
                                    String violation = test.getViolation(node);
                                    if (violation != null)
                                        out.append(test).append(" : ").append(violation).append('\n');
                                }
                                if (out.length() > 0) {
                                    compilationComplete(Completion.CONTENT_VIOLATION, node, out.toString());
                                } else {
                                    notifyMappingComplete(result);
                                    compilationComplete(Completion.JUST_FINE, node, null, result);
                                }
                            }
                        } catch (SAXException e) {
                            structureViolation(node, handler.getError());
                        } finally {
                            handler.reset();
                        }
                    } else {
                        MappingResult result = new MappingResult(serializer, metadataRecord.getId(), node,
                                recMapping.getRecDefTree());
                        List<String> uriErrors = result.getUriErrors();
                        List<String> rdfErrors = result.getRDFErrors();
                        if (!rdfErrors.isEmpty()) {
                            StringBuilder out = new StringBuilder();
                            // TODO we can probably reuse uriErrors instead of doing result.getUriErrors()
                            for (String rdfError : result.getRDFErrors()) {
                                out.append(rdfError).append("\n");
                            }
                            compilationComplete(Completion.RDF_VIOLATION, node, out.toString());

                        } else if (!uriErrors.isEmpty()) {
                            StringBuilder out = new StringBuilder();
                            for (String uriError : uriErrors) {
                                out.append(uriError).append("\n");
                            }
                            compilationComplete(Completion.CONTENT_VIOLATION, node, out.toString());
                        } else {
                            notifyMappingComplete(result);
                            compilationComplete(Completion.JUST_FINE, node, null, result);
                        }
                    }
                    // else {
                    // compilationComplete(Completion.UNVALIDATED, node, null);
                    // }
                    setMappingCode();
                } catch (DiscardRecordException e) {
                    compilationComplete(Completion.DISCARDED_RECORD, null, e.getMessage());
                    setMappingCode();
                } catch (LanguageTagException e) {
                    // Show user-friendly message for xml:lang validation errors
                    compilationComplete(Completion.CONTENT_VIOLATION, null, e.getUserFriendlyMessage());
                    notifyStateChange(CompileState.ERROR);
                }
            } catch (Exception e) {
                compilationComplete(Completion.UNEXPECTED, null, e.getMessage());
                notifyStateChange(CompileState.ERROR);
            } finally {
                compiling = false;
            }
        }

        private void setMappingCode() {
            if (nodeMapping != null && nodeMapping.isUserCodeEditable() && !recMapping.isLocked()) {
                String editedCode = documentToString(codeDocument);
                CodeGenerator codeGenerator = new CodeGenerator(recMapping)
                        .withEditPath(new EditPath(nodeMapping, null));
                String generatedCode = codeGenerator.toNodeMappingCode();
                if (isSimilarCode(editedCode, generatedCode) && !nodeMapping.isConstant()) {
                    nodeMapping.setGroovyCode(null);
                    notifyStateChange(CompileState.ORIGINAL);
                } else {
                    nodeMapping.setGroovyCode(editedCode);
                    notifyStateChange(CompileState.SAVED);
                }
            }
        }

        private void structureViolation(Node node, String handlerError)
                throws XPathFactoryConfigurationException, XPathExpressionException {
            StringBuilder out = new StringBuilder();
            for (StructureTest test : StructureTest.listFrom(recMapping.getRecDefTree().getRecDef())) {
                StructureTest.Violation violation = test.getViolation(node);
                switch (violation) {
                    case REQUIRED:
                        out.append("\nRequired piece was missing:\n\t").append(test.toString()).append('\n');
                        break;
                    case SINGULAR:
                        out.append("\nToo many sub-elements:\n\t").append(test.toString()).append('\n');
                        break;
                }
            }
            if (out.length() > 0) {
                compilationComplete(Completion.STRUCTURE_VIOLATION, node, String.format(
                        "Record definition structure violations:\n%s\n" +
                                "Message from XSD validation:\n%s\n",
                        out, handlerError));
            } else {
                compilationComplete(Completion.SCHEMA_VIOLATION, node, String.format(
                        "Message from XSD validation:\n%s\n",
                        handlerError));
            }
        }

        private void compilationComplete(Completion completion, Node node, String error) {
            compilationComplete(completion, node, error, null);
        }

        private void compilationComplete(Completion completion, Node node, String error, MappingResult result) {
            // Let MappingResult manage XML serialization, including possibly appending
            // comment with hash, etc.
            // String output = node == null ? "No XML" : serializer.toXml(node, true);
            String output = result == null ? "No XML" : result.toXml(getDataSet().getDataSetFacts());

            String syntaxStyle = outputDocument.getSyntaxStyle();
            if (error == null) {
                try {
                    output = JenaHelper.convertRDF(recMapping.getDefaultPrefix(), output, rdfFormat);
                } catch (Throwable t) {
                    ByteArrayOutputStream errorBuffer = new ByteArrayOutputStream();
                    try (PrintWriter writer = new PrintWriter(errorBuffer)) {
                        t.printStackTrace(writer);
                        writer.flush();
                        error = new String(errorBuffer.toByteArray(), StandardCharsets.UTF_8);
                    }
                }
            }
            if (error != null)
                output = String.format("## %s ##\n\n%s\n## OUTPUT ##\n%s", completion, error, output);
            if (error == null && outputArea != null) {
                outputArea.setDocument(outputDocument);
            }
            sipModel.exec(new DocumentSetter(outputDocument, output, false));
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
            } catch (BadLocationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private class TriggerTimer implements ActionListener {
        private Timer timer = new Timer(COMPILE_DELAY, this);
        private RSyntaxTextArea outputArea;

        private TriggerTimer() {
            timer.setRepeats(false);
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            if (compiling)
                return;
            if (nodeMapping == null && type == Type.FIELD) {
                try {
                    outputDocument.remove(0, outputDocument.getLength() - 1);
                    return;
                } catch (BadLocationException e) {
                    throw new RuntimeException(e);
                }
            }
            EditPath editPath = null;
            if (nodeMapping != null) {
                editPath = new EditPath(nodeMapping, documentToString(codeDocument));
            }
            sipModel.exec(new MappingJob(editPath));
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
            if (!ignoreDocChanges)
                sipModel.exec(this);
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
            if (error.length() == 0)
                return null;
            return error.toString();
        }

        public void checkErrors() throws SAXException {
            if (error.length() > 0)
                throw new SAXException(error.toString());
        }

        public void reset() {
            error.setLength(0);
        }
    }

    private void notifyStateChange(final CompileState state) {
        for (Listener listener : listeners)
            listener.stateChanged(state);
    }

    private void notifyCodeCompiled(String code) {
        for (Listener listener : listeners) {
            listener.codeCompiled(type, code);
        }
    }

    private void notifyMappingComplete(MappingResult mappingResult) {
        if (type == Type.FIELD)
            return;
        for (Listener listener : listeners)
            listener.mappingComplete(mappingResult);
    }

    public interface Listener {
        void stateChanged(CompileState state);

        void codeCompiled(Type type, String code);

        void mappingComplete(MappingResult mappingResult);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    private List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
}
