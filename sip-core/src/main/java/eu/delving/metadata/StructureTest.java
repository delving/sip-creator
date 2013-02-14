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
import net.sf.saxon.dom.DOMNodeList;
import org.w3c.dom.Node;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Test required and singular for an xpath
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class StructureTest {
    private Path path;
    private boolean required;
    private boolean singular;
    private XPathExpression parent;
    private XPathExpression test;

    public static List<StructureTest> listFrom(RecDef recDef) throws XPathFactoryConfigurationException, XPathExpressionException {
        StructureTest.Factory factory = new StructureTest.Factory(XMLToolFactory.xpathFactory(), new XPathContext(recDef.namespaces));
        List<StructureTest> tests = new ArrayList<StructureTest>();
        collectStructureTests(recDef.root, Path.create(), tests, factory);
        return tests;
    }

    public enum Violation {
        NONE,
        REQUIRED,
        SINGULAR
    }

    private StructureTest(Path path, boolean required, boolean singular, Factory factory) throws XPathExpressionException {
        this.path = path;
        this.required = required;
        this.singular = singular;
        String parentPath = path.parent().toString();
        this.parent = factory.createPath().compile(parentPath);
        String testPath = getTestPath();
        this.test = factory.createPath().compile(testPath);
    }

    public Violation getViolation(Node root) throws XPathExpressionException {
        DOMNodeList parentList = (DOMNodeList) parent.evaluate(root, XPathConstants.NODESET);
        for (int walk = 0; walk < parentList.getLength(); walk++) {
            Node node = parentList.item(walk);
            DOMNodeList testList = (DOMNodeList) test.evaluate(node, XPathConstants.NODESET);
            if (required && testList.getLength() == 0) {
                return Violation.REQUIRED;
            }
            if (singular && testList.getLength() > 1) {
                return Violation.SINGULAR;
            }
        }
        return Violation.NONE;
    }

    public String toString() {
        String pattern = "ILLEGAL!";
        if (required) {
            if (singular) {
                pattern = "ExactlyOne(\"%s\")";
            }
            else {
                pattern = "OneOrMore(\"%s\")";
            }
        }
        else if (singular) {
            pattern = "ZeroOrOne(\"%s\")";
        }
        return String.format(pattern, path);
    }

    private String getTestPath() {
        Tag tag = path.peek();
        if (tag.isAttribute()) {
            return "./@" + tag.toString();
        }
        else {
            return "./" + tag.toString();
        }
    }

    public static class Factory {
        private XPathFactory pathFactory;
        private NamespaceContext namespaceContext;

        public Factory(XPathFactory pathFactory, NamespaceContext namespaceContext) {
            this.pathFactory = pathFactory;
            this.namespaceContext = namespaceContext;
        }

        public StructureTest create(Path path, boolean required, boolean singular) throws XPathExpressionException {
            return new StructureTest(path, required, singular, this);
        }

        private XPath createPath() {
            XPath path = pathFactory.newXPath();
            path.setNamespaceContext(namespaceContext);
            return path;
        }
    }

    private static void collectStructureTests(RecDef.Attr attr, Path path, List<StructureTest> tests, StructureTest.Factory factory) throws XPathExpressionException {
        path = path.child(attr.tag);
        if (attr.required) tests.add(factory.create(path, attr.required, false));
    }

    private static void collectStructureTests(RecDef.Elem elem, Path path, List<StructureTest> tests, StructureTest.Factory factory) throws XPathExpressionException {
        path = path.child(elem.tag);
        if (elem.required || elem.singular) tests.add(factory.create(path, elem.required, elem.singular));
        for (RecDef.Attr sub : elem.attrList) collectStructureTests(sub, path, tests, factory);
        for (RecDef.Elem sub : elem.elemList) collectStructureTests(sub, path, tests, factory);
    }

}
