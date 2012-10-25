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

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import eu.delving.schema.SchemaVersion;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.*;

/**
 * A record mapping describes how an input format is transformed into an output format in the form of a Groovy builder,
 * which is dynamically generated.
 * <p/>
 * There are some givens, recorded in the facts, and then a set of node mappings, each identified with a path,
 * and containing mapping information such as a dictionary or a Groovy code snippet to do more elaborate
 * transformations.
 * <p/>
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

    @XStreamAsAttribute
    String schemaVersion;

    @XStreamAsAttribute
    boolean locked;

    @XStreamAlias("facts")
    Map<String, String> facts = new HashMap<String, String>();

    @XStreamAlias("functions")
    SortedSet<MappingFunction> functions = new TreeSet<MappingFunction>();

    @XStreamAlias("node-mappings")
    List<NodeMapping> nodeMappings = new ArrayList<NodeMapping>();

    @XStreamOmitField
    RecDefTree recDefTree;

    public RecMapping() {
    }

    private RecMapping(RecDefTree recDefTree) {
        this.prefix = recDefTree.getRecDef().prefix;
        this.schemaVersion = recDefTree.getRecDef().version;
        this.recDefTree = recDefTree;
    }

    public String getPrefix() {
        return prefix;
    }

    public SchemaVersion getSchemaVersion() {
        if (prefix == null) throw new IllegalArgumentException("Mapping lacks prefix");
        if (schemaVersion == null) {
            for (String [] hint : HACK_VERSION_HINTS) if (hint[0].equals(prefix)) schemaVersion = hint[1];
        }
        if (schemaVersion == null) throw new IllegalStateException("Mapping lacks schemaVersion missing");
        return new SchemaVersion(prefix, schemaVersion);
    }

    public void setSchemaVersion(SchemaVersion schemaVersion) {
        if (!schemaVersion.getPrefix().equals(prefix)) throw new IllegalArgumentException("Trying to set version for the wrong schema");
        this.schemaVersion = schemaVersion.getVersion();
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
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

    public SortedSet<MappingFunction> getFunctions() {
        return functions;
    }

    public boolean hasFunction(String name) {
        return fetch(name) != null;
    }

    public MappingFunction createFunction(String name) {
        if (hasFunction(name)) throw new RuntimeException("Function already exists: " + name);
        MappingFunction function = new MappingFunction(name);
        functions.add(function);
        return function;
    }
    
    public void addFunction(MappingFunction function) {
        functions.add(function);
    }

    public void removeFunction(MappingFunction function) {
        functions.remove(function);
    }

    public List<NodeMapping> getNodeMappings() {
        return nodeMappings;
    }

    public RecDefTree getRecDefTree() {
        return recDefTree;
    }

    public String toCode() {
        CodeOut codeOut = CodeOut.create();
        toCode(codeOut, null);
        return codeOut.toString();
    }

    public void toCode(CodeOut codeOut, EditPath editPath) {
        recDefTree.toCode(codeOut, functions, facts, editPath);
    }

    public String toString() {
        return stream().toXML(this);
    }

    private MappingFunction fetch(String name) {
        for (MappingFunction existing : functions) if (existing.name.equals(name)) return existing;
        return null;
    }

    private void resolve() {
        if (nodeMappings.isEmpty()) {
            List<RecDef.FieldMarker> fieldMarkers = recDefTree.getRecDef().fieldMarkers;
            if (fieldMarkers != null) for (RecDef.FieldMarker marker : fieldMarkers) {
                if (!"fact".equals(marker.type)) continue;
                Path path = marker.path.withDefaultPrefix(recDefTree.getRecDef().prefix);
                RecDefNode recDefNode = recDefTree.getRecDefNode(path);
                if (recDefNode != null) {
                    NodeMapping nodeMapping = new NodeMapping().setInputPath(Path.create().child(Tag.create("facts")).child(Tag.create(marker.name)));
                    recDefNode.addNodeMapping(nodeMapping);
                    nodeMappings.add(nodeMapping);
                }
            }
        }
        else {
            Iterator<NodeMapping> walk = nodeMappings.iterator();
            while (walk.hasNext()) {
                NodeMapping nodeMapping = walk.next();
                RecDefNode node = recDefTree.getRoot().getNode(nodeMapping.outputPath);
                if (node != null) {
                    node.addNodeMapping(nodeMapping);
                }
                else {
                    walk.remove();
                }
            }
        }
    }

    public static RecMapping create(RecDefTree recDefTree) throws MetadataException {
        RecMapping recMapping = new RecMapping(recDefTree);
        recMapping.resolve();
        return recMapping;
    }

    public static RecMapping read(File file, RecDefModel recDefModel) throws MetadataException {
        InputStream is = null;
        try {
            is = new FileInputStream(file);
            return read(is, recDefModel);
        }
        catch (FileNotFoundException e) {
            throw new MetadataException(e);
        }
        finally {
            IOUtils.closeQuietly(is);
        }
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
        recMapping.recDefTree = recDefModel.createRecDefTree(recMapping.getSchemaVersion());
        recMapping.resolve();
        return recMapping;
    }

    public static RecMapping upgrade(RecMapping previousVersion, String version, RecDefModel recDefModel) throws MetadataException {
        SchemaVersion schemaVersion = new SchemaVersion(previousVersion.getPrefix(), version);
        RecDefTree recDefTree =  recDefModel.createRecDefTree(schemaVersion);
        RecMapping recMapping = new RecMapping(recDefTree);
        // todo: copy stuff carefully
        return recMapping;
    }

    public static void write(File file, RecMapping recMapping) {
        OutputStream os = null;
        try {
            os = new FileOutputStream(file);
            write(os, recMapping);
        }
        catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        finally {
            IOUtils.closeQuietly(os);
        }
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

    public static final String[][] HACK_VERSION_HINTS = {
            {"icn", "1.0.0"},
            {"abm", "1.0.0"},
            {"tib", "1.0.1"},
            {"ese", "3.4.0"},
            {"aff", "0.1.0"},
            {"itin", "1.0.0"},
            {"lido", "1.0.0"},
    };


}
