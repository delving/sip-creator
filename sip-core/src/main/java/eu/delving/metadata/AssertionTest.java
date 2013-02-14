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

package eu.delving.metadata;

import eu.delving.XMLToolFactory;
import eu.delving.groovy.GroovyCodeResource;
import groovy.lang.Binding;
import groovy.lang.Script;
import net.sf.saxon.dom.DOMNodeList;
import org.w3c.dom.Node;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Test assertions
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class AssertionTest {
    private Assertion assertion;
    private XPathExpression path;
    private Binding binding = new Binding();
    private Script script;

    public static List<AssertionTest> listFrom(RecDef recDef, GroovyCodeResource groovy) throws XPathFactoryConfigurationException, XPathExpressionException {
        List<AssertionTest> tests = new ArrayList<AssertionTest>();
        if (recDef.assertionList == null) return tests;
        Factory factory = new Factory(XMLToolFactory.xpathFactory(), new XPathContext(recDef.namespaces), groovy);
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
        DOMNodeList nodeList = (DOMNodeList) path.evaluate(root, XPathConstants.NODESET);
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
        return null;
    }

    public String toString() {
        return String.format("Assertion(\"%s\")", assertion.xpath);
    }

    public static class Factory {
        private XPathFactory pathFactory;
        private NamespaceContext namespaceContext;
        private GroovyCodeResource groovy;

        public Factory(XPathFactory pathFactory, NamespaceContext namespaceContext, GroovyCodeResource groovy) {
            this.pathFactory = pathFactory;
            this.namespaceContext = namespaceContext;
            this.groovy = groovy;
        }

        public AssertionTest create(Assertion assertion) throws XPathExpressionException {
            return new AssertionTest(assertion, this);
        }

        private XPath createPath() {
            XPath path = pathFactory.newXPath();
            path.setNamespaceContext(namespaceContext);
            return path;
        }
    }

}
