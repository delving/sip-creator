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
            recMapping = RecMapping.create(recDefModel().createRecDefTree(new SchemaVersion("lido", "1.0.0")));
            recMapping.getRecDefTree().setListener(new RecDefNodeListener() {
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
            });
        }
        catch (MetadataException e) {
            throw new RuntimeException("Problem!");
        }
    }

    @Test
    public void cornucopia() throws MappingException {
        recMapping.setFact("dogExists", "true");
        RecDefNode sortOrder = node("/lido/@sortorder");
        sortOrder.addNodeMapping(mapping("/input/leadup/@orderofsort"));
        RecDefNode subjectSet = node("/lido/descriptiveMetadata/objectRelationWrap/subjectWrap/subjectSet");
        subjectSet.addNodeMapping(mapping("/input/leadup/record/list/member"));
        RecDefNode subjectSetSortOrder = node("/lido/descriptiveMetadata/objectRelationWrap/subjectWrap/subjectSet/@sortorder");
        subjectSetSortOrder.addNodeMapping(mapping("/input/leadup/record/list/member/@index"));
        RecDefNode termNode = node("/lido/descriptiveMetadata/objectRelationWrap/subjectWrap/subjectSet/subject/subjectConcept/term");
        NodeMapping concept = mapping("/input/leadup/record/list/member/concept");
        concept.operator = Operator.FIRST;
        NodeMapping term = termNode.addNodeMapping(concept);
        term.dictionary = new TreeMap<String, String>();
        term.dictionary.put("superhero", "Clay Man");
        term.dictionary.put("savior", "Jesus!");
        term.dictionary.put("sidekick", "Clay Horse");
        RecDefNode prefNode = node("/lido/descriptiveMetadata/objectRelationWrap/subjectWrap/subjectSet/subject/subjectConcept/term/@pref");
        NodeMapping attr2attr = prefNode.addNodeMapping(mapping("/input/leadup/record/list/member/@index"));
        System.out.println("attr=" + attr2attr);

        RecDefNode optionsNode = node("/lido/administrativeMetadata/recordWrap/recordID");
        NodeMapping dictionaryMapping = optionsNode.addNodeMapping(mapping("/input/leadup/@orderofsort"));
        dictionaryMapping.dictionary = new TreeMap<String, String>();
        dictionaryMapping.dictionary.put("thisaway", "reverse reverse");
        dictionaryMapping.dictionary.put("thataway", "forward forward");

        RecDefNode actorNode = node("/lido/descriptiveMetadata/objectRelationWrap/subjectWrap/subjectSet/subject/subjectActor/displayActor");
        NodeMapping mapping = actorNode.addNodeMapping(mapping("/input/leadup/record/list/member/name"));
        mapping.setGroovyCode(
                "if (_name.contains(' ')) {\n" +
                "   return _name.split(' ');\n" +
                "} else {\n" +
                "   return _name.text();\n" +
                "}"
        );

        RecDefNode oneTwoTarget = node("/lido/descriptiveMetadata/objectClassificationWrap/classificationWrap/classification/conceptID");
        oneTwoTarget.addNodeMapping(mapping("/input/leadup/record/fromOne"));
        oneTwoTarget.addNodeMapping(mapping("/input/leadup/record/fromTwo"));

        GroovyCodeResource resource = new GroovyCodeResource(getClass().getClassLoader());
        MappingRunner mappingRunner = new MappingRunner(resource, recMapping, null, true);
        printWithLineNumbers(mappingRunner.getCode());
        Node node = mappingRunner.runMapping(createInputRecord());

        String xml = new XmlSerializer().toXml(node, true);
        System.out.println(xml);
        Assert.assertEquals("Unexpected xml", EXPECT, xml);
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
        n(record, "fromOne", "http://uri.com/but with spaces/whichis[weird\\stupid]/");
        n(record, "fromTwo", "2");

        Map<String, String> ns = new TreeMap<String, String>();
        ns.put("lido", "http://lidoland");
        return new MetadataRecordFactory(ns).fromGroovyNode(input, -1, 1);
    }

    private static final String EXPECT =
            "<?xml version='1.0' encoding='UTF-8'?>\n" +
                    "<lido:lido xmlns:lido=\"http://www.lido-schema.org\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" lido:sortorder=\"thisaway\" xsi:schemaLocation=\"http://www.lido-schema.org http://www.lido-schema.org/fakelido.xsd\">\n" +
//                    "<lido:lido xmlns:lido=\"http://www.lido-schema.org\" lido:sortorder=\"thisaway\">\n" +
                    "    <lido:descriptiveMetadata>\n" +
                    "        <lido:objectClassificationWrap>\n" +
                    "            <lido:classificationWrap>\n" +
                    "                <lido:classification>\n" +
                    "                    <lido:conceptID>http://uri.com/but%20with%20spaces/whichis%5Bweird%5Cstupid%5D/</lido:conceptID>\n" +
                    "                    <lido:conceptID>2</lido:conceptID>\n" +
                    "                </lido:classification>\n" +
                    "            </lido:classificationWrap>\n" +
                    "        </lido:objectClassificationWrap>\n" +
                    "        <lido:objectRelationWrap>\n" +
                    "            <lido:subjectWrap>\n" +
                    "                <lido:subjectSet lido:sortorder=\"23\">\n" +
                    "                    <lido:subject>\n" +
                    "                        <lido:subjectConcept>\n" +
                    "                            <lido:term lido:addedSearchTerm=\"man\" lido:pref=\"23\">Clay Man</lido:term>\n" +
                    "                        </lido:subjectConcept>\n" +
                    "                        <lido:subjectActor>\n" +
                    "                            <lido:displayActor>Gumby</lido:displayActor>\n" +
                    "                            <lido:displayActor>Dammit</lido:displayActor>\n" +
                    "                        </lido:subjectActor>\n" +
                    "                    </lido:subject>\n" +
                    "                </lido:subjectSet>\n" +
                    "                <lido:subjectSet lido:sortorder=\"45\">\n" +
                    "                    <lido:subject>\n" +
                    "                        <lido:subjectConcept>\n" +
                    "                            <lido:term lido:addedSearchTerm=\"horse\" lido:pref=\"45\">Clay Horse</lido:term>\n" +
                    "                        </lido:subjectConcept>\n" +
                    "                        <lido:subjectActor>\n" +
                    "                            <lido:displayActor><![CDATA[<]]>O'Po<![CDATA[k]]>ey<![CDATA[>]]></lido:displayActor>\n" +
                    "                            <lido:displayActor>McPokey</lido:displayActor>\n" +
                    "                        </lido:subjectActor>\n" +
                    "                    </lido:subject>\n" +
                    "                </lido:subjectSet>\n" +
                    "            </lido:subjectWrap>\n" +
                    "        </lido:objectRelationWrap>\n" +
                    "    </lido:descriptiveMetadata>\n" +
                    "    <lido:administrativeMetadata>\n" +
                    "        <lido:recordWrap>\n" +
                    "            <lido:recordID lido:type=\"rv\">reverse reverse</lido:recordID>\n" +
                    "        </lido:recordWrap>\n" +
                    "    </lido:administrativeMetadata>\n" +
                    "</lido:lido>\n";

    private static GroovyNode n(GroovyNode parent, String name) {
        return new GroovyNode(parent, name);
    }

    private static GroovyNode n(GroovyNode parent, String name, String value) {
        return new GroovyNode(parent, name, value);
    }

    private static NodeMapping mapping(String path) {
        return new NodeMapping().setInputPath(Path.create(path));
    }

    private static RecDefNode node(String path) {
        Path prefixed = Path.create(path).withDefaultPrefix("lido");
        RecDefNode node = recMapping.getRecDefTree().getRecDefNode(prefixed);
        Assert.assertNotNull(node);
        return node;
    }

    private static RecDefModel recDefModel() {
        return new RecDefModel() {

            @Override
            public RecDefTree createRecDefTree(SchemaVersion schemaVersion) {
                if (!"lido".equals(schemaVersion.getPrefix())) throw new RuntimeException();
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
}
