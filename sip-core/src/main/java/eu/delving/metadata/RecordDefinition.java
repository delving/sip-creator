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
import com.thoughtworks.xstream.annotations.XStreamOmitField;

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

    @XStreamOmitField
    public Map<Path, FieldDefinition> fieldDefinitions = new TreeMap<Path, FieldDefinition>();

    public void initialize(List<FactDefinition> factDefinitions) throws MetadataException {
        root.setPaths(new Path());
        root.setFactDefinitions(factDefinitions);
        root.addFieldDefinitions(fieldDefinitions);
    }

    public List<FieldDefinition> getMappableFields() {
        List<FieldDefinition> fields = new ArrayList<FieldDefinition>();
        root.getMappableFields(fields);
        return fields;
    }

    public FieldDefinition getFieldDefinition(Path path) {
        return fieldDefinitions.get(path);
    }

    public String toString() {
        return String.format("RecordDefinition(%s)", prefix);
    }
}
