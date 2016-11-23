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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Test required and singular for an xpath
 *
 */
public class StructureTest {
    private Path path;
    private boolean required;
    private boolean singular;
    private XPathExpression parent;
    private XPathExpression test;

    public static List<StructureTest> listFrom(RecDef recDef) {
        StructureTest.Factory factory = new StructureTest.Factory(new RecDefNamespaceContext(recDef.namespaces));
        List<StructureTest> tests = new ArrayList<>();
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
        NodeList parentList = (NodeList) parent.evaluate(root, XPathConstants.NODESET);
        for (int walk = 0; walk < parentList.getLength(); walk++) {
            Node node = parentList.item(walk);
            NodeList testList = (NodeList) test.evaluate(node, XPathConstants.NODESET);
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
        String pattern = "what? %s %s";
        if (required) {
            if (singular) {
                pattern = "The %s tag should appear exactly once within %s.";
            }
            else {
                pattern = "The %s tag should appear at least once within %s.";
            }
        }
        else if (singular) {
            pattern = "The %s tag should not appear more than once within %s.";
        }
        return String.format(pattern, path.peek(), path.parent());
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
        private NamespaceContext namespaceContext;

        public Factory(NamespaceContext namespaceContext) {
            this.namespaceContext = namespaceContext;
        }

        public StructureTest create(Path path, boolean required, boolean singular) throws XPathExpressionException {
            return new StructureTest(path, required, singular, this);
        }

        private XPath createPath() {
            return XMLToolFactory.xpath(namespaceContext);
        }
    }

    private static void collectStructureTests(RecDef.Attr attr, Path path, List<StructureTest> tests, StructureTest.Factory factory) {
        try {
            path = path.child(attr.tag);
            if (attr.required) {
                tests.add(factory.create(path, true, false));
            }
        }
        catch (XPathExpressionException e) {
            throw new RuntimeException("XPath problem: " + path);
        }
    }

    private static void collectStructureTests(RecDef.Elem elem, Path path, List<StructureTest> tests, StructureTest.Factory factory) {
        path = path.child(elem.tag);
        if (path.size() > 1 && elem.required || elem.singular) {
            try {
                tests.add(factory.create(path, elem.required, elem.singular));
            }
            catch (XPathExpressionException e) {
                throw new RuntimeException("XPath problem: " + path);
            }
        }
        for (RecDef.Attr sub : elem.attrList) {
            collectStructureTests(sub, path, tests, factory);
        }
        for (RecDef.Elem sub : elem.elemList) {
            collectStructureTests(sub, path, tests, factory);
        }

    }

}
