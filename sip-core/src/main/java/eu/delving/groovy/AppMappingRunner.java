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

package eu.delving.groovy;

import eu.delving.metadata.CodeGenerator;
import eu.delving.metadata.EditPath;
import eu.delving.metadata.RecMapping;
import groovy.lang.MissingPropertyException;
import groovy.lang.Script;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;
import org.w3c.dom.Node;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static eu.delving.groovy.Utils.*;

/**
 * This core class takes a RecMapping and execute the code that it can generate to
 * transform an input to output in the form of a tree of Groovy Nodes using a custom
 * DOMBuilder class.
 * <p/>
 * It wraps the mapping code in the MappingCategory for DSL features, and before
 * executing the mapping it binds the input and output to the script to be run.
 * <p/>
 * There is a special case in which a specific selected path of the record definition
 * is being compiled, potentially even with edited code.  In this case the whole builder
 * is not created, but only the necessary code to render the given path.  This is for
 * showing results while the user is adjusting the code of a single snippet, and only
 * gives that part of the record output.
 *
 *
 */

public class AppMappingRunner extends AbstractMappingRunner {

    private final ScriptBinding binding = new ScriptBinding();
    private Script script;

    /**
     *
     * @param groovyCodeResource A factory for Groovy-scripts
     * @param recMapping represents to mapping to be applied
     * @param editPath represents an (optional) addendum to the recMapping
     * @param trace if true, inserts a stacktrace-comment into the generated code
     */
    public AppMappingRunner(
        final GroovyCodeResource groovyCodeResource,
        final RecMapping recMapping,
        final EditPath editPath, final boolean trace) {
        super(recMapping, new CodeGenerator(recMapping).withEditPath(editPath).withTrace(trace).toRecordMappingCode());
        this.script = groovyCodeResource.createMappingScript(code);
        this.script.getBinding().setVariable("WORLD", binding);
        GroovyNode factsNode = new GroovyNode(null, "facts");
        for (Map.Entry<String, String> entry : recMapping.getFacts().entrySet()) {
            new GroovyNode(factsNode, entry.getKey(), entry.getValue());
        }
        this.binding._facts = initFactsNode(recMapping.getFacts());
        this.binding._optLookup = recMapping.getRecDefTree().getRecDef().valueOptLookup;
    }


    @Override
    public Node runMapping(MetadataRecord metadataRecord) throws MappingException  {
        if (metadataRecord == null) throw new RuntimeException("Null input metadata record");
        try {
            binding.output = DOMBuilder.createFor(recMapping.getRecDefTree().getRecDef());
            binding.input = Collections.singletonList(metadataRecord.getRootNode());
            return stripEmptyElements(script.run());
        }
        catch (DiscardRecordException e) {
            throw e;
        }
        catch (MissingPropertyException e) {
            throw new MappingException("Missing Property " + e.getProperty(), e);
        }
        catch (MultipleCompilationErrorsException e) {
            StringBuilder out = new StringBuilder();
            for (Object o : e.getErrorCollector().getErrors()) {
                SyntaxErrorMessage message = (SyntaxErrorMessage) o;
                @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"}) SyntaxException se = message.getCause();
                // line numbers will not match
                out.append(String.format("Problem: %s%n", se.getOriginalMessage()));
            }
            throw new MappingException(out.toString(), e);
        }
        catch (AssertionError e) {
            throw new MappingException("The keyword 'assert' should not be used", e);
        }
        catch (Exception e) {
            String codeLines = fetchCodeLines(e, metadataRecord);
            if (codeLines != null) {
                throw new MappingException("Script Exception:%n" + codeLines + metadataRecord.getId(), e);
            }
            else {
                throw new MappingException("Unexpected: " + e.toString(), e);
            }
        }
    }

    // a dirty hack which parses the exception's stack trace.  any better strategy welcome, but it works.
    private String fetchCodeLines(Exception e, MetadataRecord metadataRecord) {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        e.printStackTrace(out);
        String trace = sw.toString();
        Pattern pattern = Pattern.compile("Script1.groovy:([0-9]*)");
        Matcher matcher = pattern.matcher(trace);
        if (matcher.find()) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Record number: %s\n", metadataRecord.getRecordNumber()));
            sb.append(String.format("Record identifier: %s\n", metadataRecord.getId()));
            sb.append(String.format("Source Record:\n %s\n", metadataRecord.toString()));
            sb.append("--------------- Groovy Code ---------------\n");
            int lineNumber = Integer.parseInt(matcher.group(1));
            for (String line : code.split("\n")) {
                lineNumber--;
                if (Math.abs(lineNumber) <= 2) {
                    sb.append(lineNumber == 0 ? ">>>" : "   ");
                }
                sb.append(line).append('\n');
            }

            sb.append("----------- What happened ------------\n");
            sb.append(e.toString());
            return sb.toString();
        }
        return null;
    }


}
