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

import eu.delving.groovy.DiscardRecordException;
import eu.delving.groovy.GroovyCodeResource;
import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.RecDefNode;
import eu.delving.sip.base.Exec;
import groovy.lang.Binding;
import groovy.lang.MissingPropertyException;
import groovy.lang.Script;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;

import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * todo
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class FunctionCompileModel {
    public final static int COMPILE_DELAY = 500;
    private MappingModel mappingModel;
    private Document inputDocument = new PlainDocument();
    private Document codeDocument = new PlainDocument();
    private Document outputDocument = new PlainDocument();
    private CompileTimer compileTimer = new CompileTimer();
    private GroovyCodeResource groovyCodeResource;
    private Feedback feedback;
    private volatile boolean compiling;
    private MappingModelEar mappingModelEar = new MappingModelEar();
    private String functionName;

    public enum State {
        ORIGINAL,
        UNCOMPILED,
        SAVED,
        EDITED,
        ERROR,
        COMMITTED,
        REGENERATED
    }

    public FunctionCompileModel(MappingModel mappingModel, Feedback feedback, GroovyCodeResource groovyCodeResource) {
        this.mappingModel = mappingModel;
        this.feedback = feedback;
        this.groovyCodeResource = groovyCodeResource;
        DocumentListener documentTrigger = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                compileSoon();
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
                compileSoon();
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
                compileSoon();
            }
        };
        this.inputDocument.addDocumentListener(documentTrigger);
        this.codeDocument.addDocumentListener(documentTrigger);
    }

    public MappingModel.Listener getMappingModelEar() {
        return mappingModelEar;
    }

    public void compileSoon() {
        compileTimer.triggerSoon();
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
        if (functionName != null && mappingModel.hasRecMapping()) {
            String code = mappingModel.getRecMapping().getFunctions().get(functionName);
            if (code == null) {
                mappingModel.setFunction(functionName, getOriginalCode());
            }
        }
        Exec.swing(new DocumentSetter(codeDocument, getOriginalCode()));
        notifyStateChange(State.ORIGINAL);
        compileSoon();
    }

    public Document getInputDocument() {
        return inputDocument;
    }

    public Document getCodeDocument() {
        return codeDocument;
    }

    public Document getOutputDocument() {
        return outputDocument;
    }

    // === privates

    private String getOriginalCode() {
        if (functionName == null) return "// function not chosen";
        return mappingModel.getFunctionCode(functionName);
    }

    private String getEditedCode() {
        return toString(codeDocument);
    }

    private boolean isCodeChanged() {
        List<String> functionLines = toLines(getOriginalCode());
        List<String> editedLines = toLines(inputDocument);
        if (functionLines.size() != editedLines.size()) return true;
        for (int walk = 0; walk < functionLines.size(); walk++) {
            if (!functionLines.get(walk).equals(editedLines.get(walk))) return false;
        }
        return true;
    }

    private static List<String> toLines(String code) {
        List<String> lines = new ArrayList<String>();
        for (String line : code.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            lines.add(line);
        }
        return lines;
    }

    private static String toString(Document document) {
        try {
            int length = document.getLength();
            return document.getText(0, length);
        }
        catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> toLines(Document document) {
        return toLines(toString(document));
    }

    private class MappingModelEar implements MappingModel.Listener {

        @Override
        public void recMappingSet(MappingModel mappingModel) {
            compileSoon();
        }

        @Override
        public void factChanged(MappingModel mappingModel, String name) {
            compileSoon();
        }

        @Override
        public void functionChanged(MappingModel mappingModel, String name) {
            // todo: implement?
        }

        @Override
        public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
        }

        @Override
        public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
        }
    }

    private class CompilationRunner implements Runnable {

        @Override
        public void run() {
            if (functionName == null) return;
            compiling = true;
            try {
                FunctionRunner functionRunner = new FunctionRunner(getEditedCode());
                List<String> outputLines = new ArrayList<String>();
                boolean problems = false;
                for (String line : toLines(inputDocument)) {
                    try {
                        Object output = functionRunner.runFunction(line);
                        addToOutput(outputLines, output);
                    }
                    catch (Problem p) {
                        outputLines.add(p.getMessage());
                        problems = true;
                    }
                }
                Exec.swing(new DocumentSetter(outputDocument, outputLines));
                if (problems) {
                    notifyStateChange(State.ERROR);
                }
                else {
                    mappingModel.setFunction(functionName, getEditedCode());
                    notifyStateChange(State.COMMITTED);
                    notifyStateChange(State.SAVED);
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

        private void addToOutput(List<String> outputLines, Object output) {
            if (output instanceof List) {
                for (Object member : (List) output) addToOutput(outputLines, member);
            }
            else {
                outputLines.add(output.toString());
            }
        }

        private void compilationComplete(final String result) {
            Exec.swing(new DocumentSetter(outputDocument, result));
        }

        public String toString() {
            return "FunctionCompileModel";
        }
    }

    private class FunctionRunner {
        private Script script;

        public FunctionRunner(String code) {
            this.script = groovyCodeResource.createFunctionScript(functionName, code);
        }

        public Object runFunction(Object argument) throws Problem {
            if (argument == null) throw new RuntimeException("Null input");
            try {
                Binding binding = new Binding();
                binding.setVariable("param", argument);
                script.setBinding(binding);
                Object result = script.run();
                if (result == null) return "";
                return result;
            }
            catch (DiscardRecordException e) {
                throw e;
            }
            catch (MissingPropertyException e) {
                throw new Problem("Missing Property " + e.getProperty(), e);
            }
            catch (MultipleCompilationErrorsException e) {
                StringBuilder out = new StringBuilder();
                for (Object o : e.getErrorCollector().getErrors()) {
                    SyntaxErrorMessage message = (SyntaxErrorMessage) o;
                    @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"}) SyntaxException se = message.getCause();
                    // line numbers will not match
                    out.append(String.format("Problem: %s\n", se.getOriginalMessage()));
                }
                throw new Problem(out.toString(), e);
            }
            catch (Exception e) {
                String codeLines = fetchCodeLines(e);
                if (codeLines != null) {
                    throw new Problem("Script Exception:\n" + codeLines, e);
                }
                else {
                    throw new Problem("Unexpected: " + e.toString(), e);
                }
            }
        }

        // a dirty hack which parses the exception's stack trace.  any better strategy welcome, but it works.
        private String fetchCodeLines(Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter out = new PrintWriter(sw);
            e.printStackTrace(out);
            String trace = sw.toString();
            Pattern pattern = Pattern.compile("Script1.groovy:([0-9]*)");
            Matcher matcher = pattern.matcher(trace);
            if (matcher.find()) {
                StringBuilder sb = new StringBuilder();
                int lineNumber = Integer.parseInt(matcher.group(1));
                for (String line : toLines(getEditedCode())) {
                    lineNumber--;
                    if (Math.abs(lineNumber) <= 2) {
                        sb.append(lineNumber == 0 ? ">>>" : "   ");
                        sb.append(line).append('\n');
                    }
                }
                sb.append("----------- What happened ------------\n");
                sb.append(e.toString());
                return sb.toString();
            }
            return null;
        }
    }

    private class DocumentSetter implements Runnable {

        private Document document;
        private String content;

        private DocumentSetter(Document document, String content) {
            this.document = document;
            this.content = content;
        }

        private DocumentSetter(Document document, List<String> content) {
            this.document = document;
            StringBuilder out = new StringBuilder();
            for (String line : content) out.append(line).append("\n");
            this.content = out.toString();
        }

        @Override
        public void run() {
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

    private class CompileTimer implements ActionListener {
        private Timer timer = new Timer(COMPILE_DELAY, this);

        private CompileTimer() {
            timer.setRepeats(false);
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            if (compiling) return;
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


    public static class Problem extends Exception {
        public Problem(String message, Throwable throwable) {
            super(message, throwable);
        }
    }
}