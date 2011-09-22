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
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Implementing the MetadataModel inteface
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class MetadataModelImpl implements MetadataModel {
    private List<FactDefinition> factDefinitions = new ArrayList<FactDefinition>();
    private Map<String, RecordDefinition> recordDefinitions = new TreeMap<String, RecordDefinition>();

    public void setFactDefinitionsFile(File factDefinitionsFile) throws FileNotFoundException {
        FactDefinition.List definitionList = readFacts(new FileInputStream(factDefinitionsFile));
        factDefinitions.addAll((definitionList.factDefinitions));
    }

    public void setRecordDefinitionFiles(File... files) throws FileNotFoundException, MetadataException {
        for (File file : files) {
            RecordDefinition recordDefinition = readRecordDefinition(new FileInputStream(file));
            recordDefinitions.put(recordDefinition.prefix, recordDefinition);
        }
    }

    public List<FactDefinition> getFactDefinitions() {
        return factDefinitions;
    }

    @Override
    public Set<String> getPrefixes() {
        return recordDefinitions.keySet();
    }

    @Override
    public RecordDefinition getRecordDefinition(String prefix) {
        RecordDefinition definition = recordDefinitions.get(prefix);
        if (definition == null) {
            throw new RuntimeException("Expected to find a record definition for prefix " + prefix);
        }
        return definition;
    }

    private FactDefinition.List readFacts(InputStream in) {
        XStream stream = stream();
        try {
            Reader reader = new InputStreamReader(in, "UTF-8");
            return (FactDefinition.List) stream.fromXML(reader);
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private RecordDefinition readRecordDefinition(InputStream in) throws MetadataException {
        try {
            Reader inReader = new InputStreamReader(in, "UTF-8");
            RecordDefinition recordDefinition = (RecordDefinition) stream().fromXML(inReader);
            recordDefinition.initialize(factDefinitions);
            return recordDefinition;
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String toString(RecordDefinition recordDefinition) {
        return stream().toXML(recordDefinition);
    }

    private static XStream stream() {
        XStream stream = new XStream(new PureJavaReflectionProvider());
        stream.processAnnotations(new Class[]{
                FactDefinition.List.class,
                RecordDefinition.class,
                ElementDefinition.class,
                FieldDefinition.class
        });
        return stream;
    }

}
