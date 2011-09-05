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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
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

    public void setRecordDefinitionResources(List<String> paths) throws IOException, MetadataException {
        URL url = getClass().getResource("/fact-definition-list.xml");
        FactDefinition.List definitionList = readFacts(url.openStream());
        factDefinitions.clear();
        factDefinitions.addAll((definitionList.factDefinitions));
        for (String path : paths) {
            url = getClass().getResource(path);
            RecordDefinition recordDefinition = readRecordDefinition(url.openStream());
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
        Reader reader = new InputStreamReader(in);
        return (FactDefinition.List) stream.fromXML(reader);
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
