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

package eu.delving.metadata;

import eu.delving.XMLToolFactory;
import eu.delving.groovy.GroovyCodeResource;
import groovy.lang.Binding;
import groovy.lang.Script;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.*;
import java.util.ArrayList;
import java.util.List;

public class AssertionTest {
    private Assertion assertion;
    private XPathExpression path;
    private Binding binding = new Binding();
    private Script script;

    public static List<AssertionTest> listFrom(RecDef recDef, GroovyCodeResource groovy) throws XPathFactoryConfigurationException, XPathExpressionException {
        List<AssertionTest> tests = new ArrayList<>();
        if (recDef.assertionList == null) return tests;
        Factory factory = new Factory(new RecDefNamespaceContext(recDef.namespaces), groovy);
        for (Assertion assertion : recDef.assertionList.assertions) {
            tests.add(factory.create(assertion));
        }
        return tests;
    }

    private AssertionTest(Assertion assertion, Factory factory) throws XPathExpressionException {
        this.assertion = assertion;
        this.path = factory.createPath().compile(assertion.xpath);
        this.script = factory.groovy.createValidationScript(assertion);
        this.script.setBinding(binding);
    }

    public String getViolation(Node root) throws XPathExpressionException {
        NodeList nodeList = (NodeList) path.evaluate(root, XPathConstants.NODESET);
        if (assertion.hasCondition()) {
            for (int walk = 0; walk < nodeList.getLength(); walk++) {
                Node node = nodeList.item(walk);
                switch (node.getNodeType()) {
                    case Node.ATTRIBUTE_NODE:
                    case Node.CDATA_SECTION_NODE:
                    case Node.TEXT_NODE:
                        binding.setProperty("it", node.getNodeValue());
                        break;
                    default:
                        throw new RuntimeException("Node type not handled: " + node.getNodeType());
                }
                Object result = script.run();
                if (result != null) {
                    String string = result.toString().trim();
                    if (!string.isEmpty()) return string;
                }
            }
        }
        else {
            if (nodeList.getLength() == 0) return assertion.onFail;
            for (int walk = 0; walk < nodeList.getLength(); walk++) {
                Node node = nodeList.item(walk);
                switch (node.getNodeType()) {
                    case Node.ATTRIBUTE_NODE:
                    case Node.CDATA_SECTION_NODE:
                    case Node.TEXT_NODE:
                        if (node.getNodeValue().trim().isEmpty()) {
                            return assertion.onFail;
                        }
                        break;
                    default:
                        throw new RuntimeException("Node type not handled: " + node.getNodeType());
                }
            }
        }
        return null;
    }

    public String toString() {
        return String.format("Assertion(\"%s\")", assertion.xpath);
    }

    public static class Factory {
        private NamespaceContext namespaceContext;
        private GroovyCodeResource groovy;

        public Factory(NamespaceContext namespaceContext, GroovyCodeResource groovy) {
            this.namespaceContext = namespaceContext;
            this.groovy = groovy;
        }

        public AssertionTest create(Assertion assertion) throws XPathExpressionException {
            return new AssertionTest(assertion, this);
        }

        private XPath createPath() {
            return XMLToolFactory.xpath(namespaceContext);
        }
    }

}
