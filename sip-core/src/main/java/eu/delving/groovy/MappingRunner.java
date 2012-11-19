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

import eu.delving.metadata.CodeGenerator;
import eu.delving.metadata.EditPath;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import groovy.lang.Binding;
import groovy.lang.MissingPropertyException;
import groovy.lang.Script;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MappingRunner {
    private Script script;
    private GroovyCodeResource groovyCodeResource;
    private RecMapping recMapping;
    private GroovyNode factsNode = new GroovyNode(null, "facts");
    private String code;
    private int counter = 0;

    public MappingRunner(GroovyCodeResource groovyCodeResource, RecMapping recMapping, EditPath editPath) {
        this.groovyCodeResource = groovyCodeResource;
        this.recMapping = recMapping;
        this.code = new CodeGenerator(recMapping).withEditPath(editPath).toString();
        this.script = groovyCodeResource.createMappingScript(code);
        for (Map.Entry<String, String> entry : recMapping.getFacts().entrySet()) {
            new GroovyNode(factsNode, entry.getKey(), entry.getValue());
        }
    }

    public RecDefTree getRecDefTree() {
        return recMapping.getRecDefTree();
    }

    public String getCode() {
        return code;
    }

    public Node runMapping(MetadataRecord metadataRecord) throws MappingException  {
        if ((counter % 100) == 0) groovyCodeResource.flush();
        if (metadataRecord == null) throw new RuntimeException("Null input metadata record");
        counter += 1;
        try {
            Binding binding = new Binding();
            DOMBuilder builder = DOMBuilder.newInstance(recMapping.getRecDefTree().getRecDef());
            binding.setVariable("_optLookup", recMapping.getRecDefTree().getRecDef().optLookup);
            binding.setVariable("output", builder);
            binding.setVariable("input", wrap(metadataRecord.getRootNode()));
            binding.setVariable("_facts", wrap(factsNode));
            script.setBinding(binding);
            return stripEmptyElements(script.run());
        }
        catch (DiscardRecordException e) {
            throw e;
        }
        catch (MissingPropertyException e) {
            throw new MappingException(metadataRecord, "Missing Property " + e.getProperty() + "\n" + code, e);
        }
        catch (MultipleCompilationErrorsException e) {
            StringBuilder out = new StringBuilder();
            for (Object o : e.getErrorCollector().getErrors()) {
                SyntaxErrorMessage message = (SyntaxErrorMessage) o;
                @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"}) SyntaxException se = message.getCause();
                // line numbers will not match
                out.append(String.format("Problem: %s\n", se.getOriginalMessage()));
            }
            throw new MappingException(metadataRecord, out.toString(), e);
        }
        catch (AssertionError e) {
            throw new MappingException(metadataRecord, "The keyword 'assert' should not be used", e);
        }
        catch (Exception e) {
            String codeLines = fetchCodeLines(e);
            if (codeLines != null) {
                throw new MappingException(metadataRecord, "Script Exception:\n" + codeLines, e);
            }
            else {
                throw new MappingException(metadataRecord, "Unexpected: " + e.toString(), e);
            }
        }
    }

    private Node stripEmptyElements(Object nodeObject) {
        Node node = (Node) nodeObject;
        stripEmpty(node);
        return node;
    }

    private void stripEmpty(Node node) {
        NodeList kids = node.getChildNodes();
        List<Node> dead = new ArrayList<Node>();
        for (int walk = 0; walk < kids.getLength(); walk++) {
            Node kid = kids.item(walk);
            switch (kid.getNodeType()) {
                case Node.ATTRIBUTE_NODE:
                    break;
                case Node.TEXT_NODE:
                case Node.CDATA_SECTION_NODE:
                    if (kid.getTextContent().trim().isEmpty()) dead.add(kid);
                    break;
                case Node.ELEMENT_NODE:
                    stripEmpty(kid);
                    if (!(kid.hasChildNodes() || kid.hasAttributes())) dead.add(kid);
                    break;
                default:
                    throw new RuntimeException("Node type not implemented: " + kid.getNodeType());
            }
        }
        for (Node kill : dead) node.removeChild(kill);
    }

    private List<GroovyNode> wrap(GroovyNode node) {
        List<GroovyNode> array = new ArrayList<GroovyNode>(1);
        array.add(node);
        return array;
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
}
