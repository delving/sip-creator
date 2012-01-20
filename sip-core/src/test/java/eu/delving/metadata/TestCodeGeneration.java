/*
 * Copyright 2011 DELVING BV
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

import com.thoughtworks.xstream.XStream;
import eu.delving.groovy.*;
import groovy.util.Node;
import junit.framework.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URL;
import java.util.*;

/**
 * Make sure the right code is being generated
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestCodeGeneration {

    static RecMapping recMapping;

    static {
        try {
            recMapping = RecMapping.create("lido", recDefModel());
            recMapping.getRecDefTree().setListener(new RecDefNode.Listener() {
                @Override
                public void nodeMappingAdded(RecDefNode recDefNode, NodeMapping nodeMapping) {
                    System.out.println("Mapping added: " + recDefNode);
                }

                @Override
                public void nodeMappingRemoved(RecDefNode recDefNode, NodeMapping nodeMapping) {
                    System.out.println("Mapping removed: " + recDefNode);
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

        node("/lido/@sortorder").addNodeMapping(mapping("/input/leadup/@orderofsort"));
        node("/lido/descriptiveMetadata/objectRelationWrap/subjectWrap/subjectSet").addNodeMapping(mapping("/input/leadup/record/list/member"));
        node("/lido/descriptiveMetadata/objectRelationWrap/subjectWrap/subjectSet/@sortorder").addNodeMapping(mapping("/input/leadup/record/list/member/@index"));

        RecDefNode termNode = node("/lido/descriptiveMetadata/objectRelationWrap/subjectWrap/subjectSet/subject/subjectConcept/term");
        NodeMapping term = termNode.addNodeMapping(mapping("/input/leadup/record/list/member/concept"));
        term.dictionary = new TreeMap<String, String>();
        term.dictionary.put("superhero", "Clay Man");
        term.dictionary.put("sidekick", "Clay Horse");

        RecDefNode actorNode = node("/lido/descriptiveMetadata/objectRelationWrap/subjectWrap/subjectSet/subject/subjectActor/displayActor");
        NodeMapping mapping = actorNode.addNodeMapping(mapping("/input/leadup/record/list/member/name"));
        mapping.addCodeLine("_member.name * { _name ->");
        mapping.addCodeLine("if (_name.contains(' ')) { return _name.split(' '); } else { return _name.text(); }");
        mapping.addCodeLine("}");
        String code = recMapping.toCode(null, null);
        System.out.println(code);

        GroovyCodeResource resource = new GroovyCodeResource(getClass().getClassLoader());
        MappingRunner mappingRunner = new MappingRunner(resource, recMapping);
        Node node = mappingRunner.runMapping(createInputRecord());
        StringWriter writer = new StringWriter();
        XmlNodePrinter printer = new XmlNodePrinter(writer);
        printer.print(node);
        System.out.println(writer.toString());
    }

    private MetadataRecord createInputRecord() {
        GroovyNode input, leadup, record, list, member1, member2;
        input = n(null, "input");
        leadup = n(input, "leadup");
        leadup.attributes().put("orderofsort", "backward");
        record = n(leadup, "record");
        list = n(record, "list");
        member1 = n(list, "member");
        member1.attributes().put("index", "23");
        n(member1, "name", "Gumby Dammit");
        n(member1, "concept", "superhero");
        member2 = n(list, "member");
        member2.attributes().put("index", "45");
        n(member2, "name", "O'Pokey");
        n(member2, "name", "McPokey");
        n(member2, "concept", "sidekick");
        Map<String, String> ns = new TreeMap<String, String>();
        ns.put("lido", "http://lidoland");
        return new MetadataRecordFactory(ns).fromGroovyNode(input, -1, 1);
    }

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
        RecDefNode node = recMapping.getRecDefTree().getRecDefNode(Path.create(path).defaultPrefix("lido"));
        Assert.assertNotNull(node);
        return node;
    }

    private static RecDefModel recDefModel() {
        return new RecDefModel() {
            @Override
            public List<FactDefinition> getFactDefinitions() {
                try {
                    InputStream inputStream = getClass().getResource("/facts-definition-list.xml").openStream();
                    XStream stream = new XStream();
                    stream.processAnnotations(FactDefinition.class);
                    Reader reader = new InputStreamReader(inputStream, "UTF-8");
                    FactDefinition.List factDefinitions = (FactDefinition.List) stream.fromXML(reader);
                    return factDefinitions.factDefinitions;
                }
                catch (Exception e) {
                    throw new RuntimeException("Unable to load fact definitions", e);
                }
            }

            @Override
            public Set<String> getPrefixes() throws MetadataException {
                Set<String> prefixes = new TreeSet<String>();
                prefixes.add("lido");
                return prefixes;
            }

            @Override
            public RecDefTree createRecDef(String prefix) {
                if (!"lido".equals(prefix)) throw new RuntimeException();
                try {
                    URL url = getClass().getResource("/lido-recdef.xml");
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
