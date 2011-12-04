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
import junit.framework.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Make sure the right code is being generated
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestCodeGeneration {

    @Test
    public void simple() {
        RecMapping recMapping = RecMapping.create("lido", recDefModel());
        recMapping.getRecDefTree().setListener(new RecDefNode.Listener() {
            @Override
            public void nodeMappingSet(RecDefNode recDefNode) {
                System.out.println("Mapping set: "+recDefNode);
            }
        });
        RecDefNode recDefNode = recMapping.getRecDefTree().getRecDefNode(new Path("/lidoWrap/lido/@sortorder"));
        Assert.assertNotNull(recDefNode);
        recDefNode.setNodeMapping(new NodeMapping());
        recMapping.setFact("dogExists", "true");
        String code = recMapping.toCode(null, null);
        System.out.println(code);
    }

    private RecDefModel recDefModel() {
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
                    InputStream inputStream = getClass().getResource("/lido-recdef.xml").openStream();
                    return RecDefTree.create(RecDef.read(inputStream));
                }
                catch (Exception e) {
                    throw new RuntimeException("Unable to load recdef", e);
                }
            }
        };
    }
}
