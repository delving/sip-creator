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

import eu.delving.groovy.DiscardRecordException;
import eu.delving.groovy.GroovyCodeResource;
import eu.delving.metadata.MappingFunction;
import eu.delving.metadata.StringUtil;
import eu.delving.sip.base.CompileState;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import groovy.lang.Binding;
import groovy.lang.MissingPropertyException;
import groovy.lang.Script;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;

import javax.swing.*;
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
 * Manage the compiling and running of functions.  Resembles the MappingCompileModel, with input/code/output/docs.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class FunctionCompileModel {
    public final static int RUN_DELAY = 100;
    public final static int COMPILE_DELAY = 500;
    private SipModel sipModel;
    private Document inputDocument = new PlainDocument();
    private Document codeDocument = new PlainDocument();
    private Document docDocument = new PlainDocument();
    private Document outputDocument = new PlainDocument();
    private TriggerTimer triggerTimer = new TriggerTimer();
    private GroovyCodeResource groovyCodeResource;
    private FunctionRunner functionRunner;
    private volatile boolean busy;
    private MappingFunction mappingFunction;
    private boolean ignoreDocChanges;

    public FunctionCompileModel(SipModel sipModel, GroovyCodeResource groovyCodeResource) {
        this.sipModel = sipModel;
        this.groovyCodeResource = groovyCodeResource;
        this.inputDocument.addDocumentListener(new DocChangeListener() {
            @Override
            public void run() {
                trigger(RUN_DELAY);
            }
        });
        this.docDocument.addDocumentListener(new DocChangeListener() {
            @Override
            public void run() {
                trigger(RUN_DELAY);
            }
        });
        this.codeDocument.addDocumentListener(new DocChangeListener() {
            @Override
            public void run() {
                functionRunner = null;
                notifyStateChange(CompileState.EDITED);
                trigger(COMPILE_DELAY);
            }
        });
    }

    public void setFunction(MappingFunction mappingFunction) {
        this.mappingFunction = mappingFunction;
        functionRunner = null;
        sipModel.exec(new DocumentSetter(inputDocument, getSampleInput(), true));
        sipModel.exec(new DocumentSetter(docDocument, getDocInput(), true));
        sipModel.exec(new DocumentSetter(codeDocument, getOriginalCode(), true));
        notifyStateChange(CompileState.SAVED);
        trigger(COMPILE_DELAY);
    }

    public Document getInputDocument() {
        return inputDocument;
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

    // === privates
    
    private String getSampleInput() {
        if (mappingFunction == null) return "no input";
        return mappingFunction.getSampleInputString();
    }

    private String getDocInput() {
        if (mappingFunction == null) return "no doc";
        return mappingFunction.getDocumentation();
    }

    private String getOriginalCode() {
        if (mappingFunction == null) return "// function not chosen";
        return mappingFunction.getUserCode();
    }

    private void trigger(int millis) {
        if (!busy) triggerTimer.triggerSoon(millis);
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

    private static List<String> toLines(Document document) {
        return toLines(StringUtil.documentToString(document));
    }

    private class RunJob implements Work.DataSetPrefixWork {

        @Override
        public void run() {
            if (mappingFunction == null) return;
            busy = true;
            try {
                if (functionRunner == null) functionRunner = new FunctionRunner();
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
                sipModel.exec(new DocumentSetter(outputDocument, outputLines));
                if (problems) {
                    notifyStateChange(CompileState.ERROR);
                }
                else {
                    mappingFunction.setSampleInput(StringUtil.documentToString(inputDocument));
                    mappingFunction.setDocumentation(StringUtil.documentToString(docDocument));
                    mappingFunction.setGroovyCode(StringUtil.documentToString(codeDocument));
                    sipModel.getMappingModel().notifyFunctionChanged(mappingFunction);
                    notifyStateChange(mappingFunction.groovyCode == null ? CompileState.ORIGINAL : CompileState.SAVED);
                }
            }
            catch (Exception e) {
                compilationComplete(e.toString());
                notifyStateChange(CompileState.ERROR);
            }
            finally {
                busy = false;
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
            sipModel.exec(new DocumentSetter(outputDocument, result, false));
        }

        public String toString() {
            return "FunctionCompileModel";
        }

        @Override
        public Job getJob() {
            return Job.COMPILE_FUNCTION;
        }

        @Override
        public String getPrefix() {
            return sipModel.getMappingModel().getPrefix();
        }

        @Override
        public DataSet getDataSet() {
            return sipModel.getDataSetModel().getDataSet();
        }
    }

    private class FunctionRunner {
        private Script script;

        public FunctionRunner() {
            this.script = groovyCodeResource.createFunctionScript(mappingFunction, sipModel.getMappingModel().getRecMapping().getFacts(), StringUtil.documentToString(codeDocument));
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
                for (String line : toLines(StringUtil.documentToString(codeDocument))) {
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

    private class DocumentSetter implements Swing {

        private Document document;
        private String content;
        private boolean ignore;

        private DocumentSetter(Document document, String content, boolean ignore) {
            this.document = document;
            this.content = content;
            this.ignore = ignore;
        }

        private DocumentSetter(Document document, List<String> content) {
            this.document = document;
            StringBuilder out = new StringBuilder();
            for (String line : content) out.append(line).append("\n");
            this.content = out.toString();
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
        private Timer timer = new Timer(0, this);

        private TriggerTimer() {
            timer.setRepeats(false);
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            if (busy) return;
            sipModel.exec(new RunJob());
        }

        public void triggerSoon(int millis) {
            timer.setInitialDelay(millis);
            timer.restart();
        }
    }
    
    private abstract class DocChangeListener implements DocumentListener, Work {

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

        @Override
        public Job getJob() {
            return Job.FUNCTION_DOC_CHANGE_LISTENER;
        }

        private void go() {
            if (!ignoreDocChanges) sipModel.exec(this);
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


    public static class Problem extends Exception {
        public Problem(String message, Throwable throwable) {
            super(message, throwable);
        }
    }
}