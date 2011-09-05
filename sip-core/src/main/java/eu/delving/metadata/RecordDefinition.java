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

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Defines the root of a hierarchical model
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

@XStreamAlias("record-definition")
public class RecordDefinition {

    @XStreamAsAttribute
    public String prefix;

    public List<NamespaceDefinition> namespaces;

    public ElementDefinition root;

    public ViewDefinition views;

    public String validation;

    public void initialize(List<FactDefinition> factDefinitions) throws MetadataException {
        root.setPaths(new Path());
        root.setFactDefinitions(factDefinitions);
    }

    public List<FieldDefinition> getMappableFields() {
        List<FieldDefinition> fields = new ArrayList<FieldDefinition>();
        root.getMappableFields(fields);
        return fields;
    }

    public FieldDefinition getFieldDefinition(Path path) {
        return root.getFieldDefinition(path);
    }

    public List<String> getFieldNameList() {
        List<String> fieldNames = new ArrayList<String>();
        root.getFieldNames(fieldNames);
        return fieldNames;
    }

    public Map<String, String> getFacetMap() {
        Map<String, String> facetMap = new TreeMap<String, String>();
        root.getFacetMap(facetMap);
        return facetMap;
    }

    public String[] getFacetFieldStrings() {
        List<String> facetFieldStrings = new ArrayList<String>();
        root.getFacetFieldStrings(facetFieldStrings);
        return facetFieldStrings.toArray(new String[facetFieldStrings.size()]);
    }

    public String[] getFieldStrings() {
        List<String> fieldStrings = new ArrayList<String>();
        root.getFieldStrings(fieldStrings);
        return fieldStrings.toArray(new String[fieldStrings.size()]);
    }

    public String toString() {
        return String.format("RecordDefinition(%s)", prefix);
    }
}
