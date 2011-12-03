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
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;

import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A record mapping describes how an input format is transformed into an output format in the form of a Groovy builder,
 * which is dynamically generated.
 *
 * There are some givens, recorded in the facts, and then a set of node mappings, each identified with a path,
 * and containing mapping information such as a dictionary or a Groovy code snippet to do more elaborate
 * transformations.
 *
 * The recDefTree is an instance of the output record definition, which is not shared with any other record
 * mappings.  It is used to generate the Groovy builder code based on its hierarchical structure, which
 * is "decorated" with the various NodeMapping instances from this mapping.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

@XStreamAlias("rec-mapping")
public class RecMapping {

    @XStreamAsAttribute
    String prefix;

    @XStreamAlias("facts")
    Map<String, String> facts = new HashMap<String, String>();

    @XStreamAlias("node-mappings")
    List<NodeMapping> nodeMappings;

    @XStreamOmitField
    RecDefTree recDefTree;

    private RecMapping(String prefix, RecDefTree recDefTree) {
        this.prefix = prefix;
        this.recDefTree = recDefTree;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getFact(String fieldName) {
        String value = facts.get(fieldName);
        if (value == null) {
            facts.put(fieldName, value = "");
        }
        return value;
    }

    public boolean setFact(String fieldName, String value) {
        String existing = facts.get(fieldName);
        if (existing == null || !value.equals(existing)) {
            facts.put(fieldName, value);
            return true;
        }
        else {
            return false;
        }
    }

    public Map<String, String> getFacts() {
        return facts;
    }

    public RecDefTree getRecDefTree() {
        return recDefTree;
    }

    public String toCode(Path selectedPath, String editedCode) {
        return recDefTree.toCode(facts, selectedPath, editedCode);
    }

    public String toString() {
        return stream().toXML(this);
    }

    private void resolve() {
        Iterator<NodeMapping> walk = nodeMappings.iterator();
        while (walk.hasNext()) {
            NodeMapping nodeMapping = walk.next();
            RecDefNode node = recDefTree.getRoot().getNode(new Path(), nodeMapping.outputPath);
            if (node != null) {
                node.setNodeMapping(nodeMapping);
            }
            else {
                walk.remove();
            }
        }
    }

    public static RecMapping create(String prefix, RecDefModel recDefModel) {
        return new RecMapping(prefix, recDefModel.createRecDef(prefix));
    }

    public static RecMapping read(InputStream is, RecDefModel recDefModel) throws MetadataException {
        try {
            Reader reader = new InputStreamReader(is, "UTF-8");
            return read(reader, recDefModel);
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static RecMapping read(Reader reader, RecDefModel recDefModel) throws MetadataException {
        RecMapping recMapping = (RecMapping) stream().fromXML(reader);
        recMapping.recDefTree = recDefModel.createRecDef(recMapping.prefix);
        recMapping.resolve();
        return recMapping;
    }

    public static void write(OutputStream os, RecMapping recMapping) {
        try {
            Writer osWriter = new OutputStreamWriter(os, "UTF-8");
            recMapping.nodeMappings = recMapping.getRecDefTree().getNodeMappings();
            stream().toXML(recMapping, osWriter);
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static XStream stream() {
        XStream stream = new XStream(new PureJavaReflectionProvider());
        stream.registerConverter(new Tag.Converter());
        stream.registerConverter(new Path.Converter());
        stream.processAnnotations(RecMapping.class);
        return stream;
    }
}
