/*
 * Copyright 2010 DELVING BV
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
import java.util.*;

/**
 * A groovy mapping based on a model.
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
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
    RecDefNode recDefRoot;

    public RecMapping(String prefix) {
        this.prefix = prefix;
    }

    public void resolve() {
        Iterator<NodeMapping> walk = nodeMappings.iterator();
        while (walk.hasNext()) {
            NodeMapping nodeMapping = walk.next();
            RecDefNode node = recDefRoot.getNode(new Path(), nodeMapping.outputPath);
            if (node != null) {
                node.setNodeMapping(nodeMapping);
            }
            else {
                walk.remove();
            }
        }
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

    public String toString() {
        return stream().toXML(this);
    }

    public static RecMapping read(InputStream is, RecDefModel recDefModel) throws MetadataException {
        try {
            Reader isReader = new InputStreamReader(is, "UTF-8");
            RecMapping recMapping = (RecMapping) stream().fromXML(isReader);
            recMapping.recDefRoot = recDefModel.createRecDef(recMapping.prefix);
            recMapping.resolve();
            return recMapping;
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static void write(OutputStream os, RecMapping recMapping) {
        try {
            Writer osWriter = new OutputStreamWriter(os, "UTF-8");
            recMapping.nodeMappings = new ArrayList<NodeMapping>();
            recMapping.recDefRoot.collect(new Path(), recMapping.nodeMappings);
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
