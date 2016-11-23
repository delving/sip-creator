/*
 * Copyright 2011, 2012 Delving BV
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

package eu.delving.groovy;

import eu.delving.metadata.OptList;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import groovy.lang.MissingPropertyException;
import groovy.lang.Script;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.script.CompiledScript;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 */
public class MappingRunner {

    private MappingRunner() {
    }

    private static ScriptIO initScriptIO( final Map<String, String> facts,
                                          final Map<String, Map<String, OptList.Opt>> valueOptLookup,
                                          final RecDef recDef){
        final ScriptIO scriptIO = new ScriptIO();
        GroovyNode factsNode = factsNodes(facts);
        scriptIO._facts = Collections.singletonList(factsNode);

        scriptIO._optLookup = valueOptLookup;
        scriptIO.output = DOMBuilder.createFor(recDef);
        return scriptIO;
    }
    private static GroovyNode factsNodes(Map<String, String> facts){
        GroovyNode factsNode = new GroovyNode(null, "facts");
        for (Map.Entry<String, String> entry : facts.entrySet()) {
            new GroovyNode(factsNode, entry.getKey(), entry.getValue());
        }
        return factsNode;
    }

    public static Node runMapping(final MappingRun arguments) throws MappingException  {

        Objects.requireNonNull(arguments);
        Objects.requireNonNull(arguments.metadataRecord);

        final ScriptIO scriptIO = initScriptIO(arguments.facts, arguments.valueOptLookup, arguments.recDef);
        scriptIO.input = Collections.singletonList(arguments.metadataRecord.getRootNode());

        CompiledScript script = arguments.groovyCodeResource.createMappingScript(arguments.code);

        try {
            Object result = script.eval();
            return stripEmptyElements(result);
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
                out.append(String.format("Problem: %s\n", se.getOriginalMessage()));
            }
            throw new MappingException(out.toString(), e);
        }
        catch (AssertionError e) {
            throw new MappingException("The keyword 'assert' should not be used", e);
        }
        catch (Exception e) {
            String codeLines = fetchCodeLines(arguments.code, e);
            if (codeLines != null) {
                throw new MappingException("Script Exception:\n" + codeLines, e);
            }
            else {
                throw new MappingException("Unexpected: " + e.toString(), e);
            }
        }
    }

    private static Node stripEmptyElements(Object nodeObject) {
        Node node = (Node) nodeObject;
        stripEmpty(node);
        return node;
    }

    private static void stripEmpty(Node node) {
        NodeList kids = node.getChildNodes();
        List<Node> dead = new ArrayList<>();
        for (int walk = 0; walk < kids.getLength(); walk++) {
            Node kid = kids.item(walk);
            switch (kid.getNodeType()) {
                case Node.ATTRIBUTE_NODE:
                    break;
                case Node.TEXT_NODE:
                case Node.CDATA_SECTION_NODE:
                    if (kid.getTextContent().trim().isEmpty()) {
                        dead.add(kid);
                    }
                    break;
                case Node.ELEMENT_NODE:
                    stripEmpty(kid);
                    if (!(kid.hasChildNodes() || kid.hasAttributes())) {
                        dead.add(kid);
                    }
                    break;
                default:
                    throw new RuntimeException("Node type not implemented: " + kid.getNodeType());
            }
        }
        for (Node kill : dead) {
            node.removeChild(kill);
        }
    }

    // a dirty hack which parses the exception's stack trace.  any better strategy welcome, but it works.
    private static String fetchCodeLines(String code, Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        e.printStackTrace(out);
        String trace = sw.toString();
        Pattern pattern = Pattern.compile("Script1.groovy:([0-9]*)");
        Matcher matcher = pattern.matcher(trace);
        if (matcher.find()) {
            StringBuilder sb = new StringBuilder();
            int lineNumber = Integer.parseInt(matcher.group(1));
            for (String line : code.split("\n")) {
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

    private static class ScriptIO {
        Object _optLookup;
        Object output;
        Object input;
        Object _facts;
    }

    public static class MappingRun {

        public MappingRun(MetadataRecord metadataRecord, GroovyCodeResource groovyCodeResource,
                          String code, Map<String, String> facts,
                          Map<String, Map<String, OptList.Opt>> valueOptLookup, RecDef recDef,
                          RecDefTree recDefTree) {
            this.metadataRecord = metadataRecord;
            this.groovyCodeResource = groovyCodeResource;
            this.code = code;
            this.facts = facts;
            this.valueOptLookup = valueOptLookup;
            this.recDef = recDef;
            this.recDefTree = recDefTree;
        }

        public final MetadataRecord metadataRecord;
        public final GroovyCodeResource groovyCodeResource;
        public final String code;
        public final Map<String, String> facts;
        public final Map<String, Map<String, OptList.Opt>> valueOptLookup;
        public final RecDef recDef;
        public final RecDefTree recDefTree;

        public static final class MappingRunBuilder {
            public MetadataRecord metadataRecord;
            public GroovyCodeResource groovyCodeResource;
            public String code;
            public Map<String, String> facts;
            public Map<String, Map<String, OptList.Opt>> valueOptLookup;
            public RecDef recDef;
            public RecDefTree recDefTree;

            private MappingRunBuilder() {
            }

            public static MappingRunBuilder aMappingRun() {
                return new MappingRunBuilder();
            }

            public MappingRunBuilder withMetadataRecord(MetadataRecord metadataRecord) {
                this.metadataRecord = metadataRecord;
                return this;
            }

            public MappingRunBuilder withGroovyCodeResource(GroovyCodeResource groovyCodeResource) {
                this.groovyCodeResource = groovyCodeResource;
                return this;
            }

            public MappingRunBuilder withCode(String code) {
                this.code = code;
                return this;
            }

            public MappingRunBuilder withFacts(Map<String, String> facts) {
                this.facts = facts;
                return this;
            }

            public MappingRunBuilder withValueOptLookup(Map<String, Map<String, OptList.Opt>> valueOptLookup) {
                this.valueOptLookup = valueOptLookup;
                return this;
            }

            public MappingRunBuilder withRecDef(RecDef recDef) {
                this.recDef = recDef;
                return this;
            }

            public MappingRunBuilder withRecDefTree(RecDefTree recDefTree) {
                this.recDefTree = recDefTree;
                return this;
            }

            public MappingRun build() {
                MappingRun mappingRun = new MappingRun(metadataRecord, groovyCodeResource,
                    code, facts, valueOptLookup, recDef, recDefTree);
                return mappingRun;
            }
        }
    }
}
