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

package eu.delving.test;

import eu.delving.groovy.*;
import eu.delving.metadata.*;
import eu.delving.schema.SchemaVersion;
import junit.framework.Assert;
import org.apache.commons.lang.StringUtils;
import org.junit.Test;
import org.w3c.dom.Node;

import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.TreeMap;

/**
 * Make sure the right code is being generated
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestCodeGeneration {

    static RecMapping recMapping;

    static {
        try {
            recMapping = RecMapping.create(recDefModel().createRecDefTree(new SchemaVersion("test", "0.0.0")));
            recMapping.getRecDefTree().setListener(new ChattyListener());
        }
        catch (MetadataException e) {
            throw new RuntimeException("Problem!");
        }
    }

    @Test
    public void cornucopia() throws MappingException {
        recMapping.setFact("dogExists", "true");

        node("/test/@sortorder")
                .addNodeMapping(mapping("/input/leadup/@orderofsort"));

        node("/test/sub-one/events/event")
                .addNodeMapping(mapping("/input/leadup/record/list/member"));

        NodeMapping eventDict = node("/test/sub-one/events/event/@thevalue")
                .addNodeMapping(mapping("/input/leadup/record/list/member/concept"));

        eventDict.operator = Operator.ALL;
        eventDict.dictionary = new TreeMap<String, String>();
        eventDict.dictionary.put("superhero", "Clay Man");
        eventDict.dictionary.put("savior", "Jesus!");
        eventDict.dictionary.put("sidekick", "Clay Horse");

        node("/test/sub-one/events/event/abc-link/@a")
                .addNodeMapping(mapping("/input/leadup/record/list/member/@index"));

        NodeMapping dictB = node("/test/id")
                .addNodeMapping(mapping("/input/leadup/@orderofsort"));
        dictB.dictionary = new TreeMap<String, String>();
        dictB.dictionary.put("thisaway", "reverse reverse");
        dictB.dictionary.put("thataway", "forward forward");

        NodeMapping mapping = node("/test/sub-one/events/event/actor")
                .addNodeMapping(mapping("/input/leadup/record/list/member/name"));
        mapping.setGroovyCode(
                "if (_name.contains(' ')) {\n" +
                        "return _name.split(' ');\n" +
                        "} else {\n" +
                        "return _name.text();\n" +
                        "}"
        );

        RecDefNode oneTwoTarget = node("/test/sub-one/sub-two");
        NodeMapping oneTwoMapping = oneTwoTarget.addNodeMapping(mapping("/input/leadup/record/fromOne"));
        oneTwoMapping.dictionary = new TreeMap<String, String>();
        oneTwoMapping.dictionary.put("look this up", "forward forward");
//        RecDefNode abcTarget = node("/test/sub-one/sub-two/abc-link");
//        abcTarget.addNodeMapping(mapping("/input/leadup/record/fromOne"));

//        oneTwoTarget.addNodeMapping(mapping("/input/leadup/record/fromTwo"));

        GroovyCodeResource resource = new GroovyCodeResource(getClass().getClassLoader());
        MappingRunner mappingRunner = new MappingRunner(resource, recMapping, null, true);
        printWithLineNumbers(mappingRunner.getCode());
        Node node = mappingRunner.runMapping(createInputRecord());

        String xml = new XmlSerializer().toXml(node, true);
        System.out.println(xml);
        xml = xml.replaceAll("\n[^<]*", "\n").trim();
        String expect = StringUtils.join(EXPECT, '\n');
        Assert.assertEquals("Unexpected xml", expect, xml);
    }

    private void printWithLineNumbers(String code) {
        int lineNumber = 1;
        for (String line : code.split("\n")) {
            System.out.println(String.format("%3d: %s", lineNumber++, line));
        }
    }

    private MetadataRecord createInputRecord() {
        GroovyNode input, leadup, record, list, member1, member2, fromOne, fromTwo;
        input = n(null, "input");
        leadup = n(input, "leadup");
        leadup.attributes().put("orderofsort", "thisaway");
        record = n(leadup, "record");
        list = n(record, "list");
        member1 = n(list, "member");
        member1.attributes().put("index", "23");
        n(member1, "name", "Gumby Dammit");
        n(member1, "concept", "superhero");
        n(member1, "concept", "savior");
        member2 = n(list, "member");
        member2.attributes().put("index", "45");
        n(member2, "name", "<![CDATA[<]]>O'Po<![CDATA[k]]>ey<![CDATA[>]]>");
        n(member2, "name", "McPokey");
        n(member2, "concept", "sidekick");
        n(record, "fromOne", "look this up");
//        n(record, "fromOne", "http://uri.com/but with spaces/whichis[weird\\stupid]/");
//        n(record, "fromTwo", "2");

        Map<String, String> ns = new TreeMap<String, String>();
        ns.put("test", "http://testicles.org");
        return new MetadataRecordFactory(ns).fromGroovyNode(input, -1, 1);
    }

    private static final String[] EXPECT = {
            "<?xml version='1.0' encoding='UTF-8'?>",
            "<test:test xmlns:test=\"http://www.testicles.org\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" test:sortorder=\"thisaway\" xsi:schemaLocation=\"http://www.testicles.org http://www.testicles.org/hang.xsd\">",
            "<test:id>thisaway</test:id>",
            "<test:sub-one>",
            "<test:sub-two>",
            "<test:abc-link test:a=\"fw\">forward forward</test:abc-link>",
            "</test:sub-two>",
            "<test:events>",
            "<test:event test:thekey=\"man\" test:theschema=\"http://gumby-and-pokey.com/definitions\" test:thevalue=\"Clay Man\">",
            "<test:abc-link test:a=\"23\"/>",
            "<test:actor>Gumby</test:actor>",
            "<test:actor>Dammit</test:actor>",
            "</test:event>",
            "<test:event test:thekey=\"horse\" test:theschema=\"http://gumby-and-pokey.com/definitions\" test:thevalue=\"Clay Horse\">",
            "<test:abc-link test:a=\"45\"/>",
            "<test:actor><![CDATA[<]]>O'Po<![CDATA[k]]>ey<![CDATA[>]]></test:actor>",
            "<test:actor>McPokey</test:actor>",
            "</test:event>",
            "</test:events>",
            "</test:sub-one>",
            "</test:test>",
    };

    private static GroovyNode n(GroovyNode parent, String name) {
        return new GroovyNode(parent, name);
    }

    private static GroovyNode n(GroovyNode parent, String name, String value) {
        return new GroovyNode(parent, name, value);
    }

    private static NodeMapping mapping(String path) {
        return new NodeMapping().setInputPath(Path.create(path));
    }

    private static RecDefNode node(String pathString) {
        Path path = Path.create(pathString).withDefaultPrefix("test");
        RecDefNode node = recMapping.getRecDefTree().getRecDefNode(path);
        Assert.assertNotNull("Unable to find node: " + path, node);
        return node;
    }

    private static RecDefModel recDefModel() {
        return new RecDefModel() {

            @Override
            public RecDefTree createRecDefTree(SchemaVersion schemaVersion) {
                if (!"test".equals(schemaVersion.getPrefix())) throw new RuntimeException();
                try {
                    URL url = TestCodeGeneration.class.getResource("/codegen/test-code-generation-recdef.xml");
                    InputStream inputStream = url.openStream();
                    return RecDefTree.create(RecDef.read(inputStream));
                }
                catch (Exception e) {
                    throw new RuntimeException("Unable to load recdef", e);
                }
            }
        };
    }

    private static class ChattyListener implements RecDefNodeListener {
        @Override
        public void nodeMappingChanged(RecDefNode recDefNode, NodeMapping nodeMapping, NodeMappingChange change) {
            System.out.println("Mapping changed: " + recDefNode);
        }

        @Override
        public void nodeMappingAdded(RecDefNode recDefNode, NodeMapping nodeMapping) {
            System.out.println("Mapping added: " + recDefNode);
        }

        @Override
        public void nodeMappingRemoved(RecDefNode recDefNode, NodeMapping nodeMapping) {
            System.out.println("Mapping removed: " + recDefNode);
        }

        @Override
        public void populationChanged(RecDefNode recDefNode) {
            System.out.println("Population changed: " + recDefNode);
        }
    }
}
