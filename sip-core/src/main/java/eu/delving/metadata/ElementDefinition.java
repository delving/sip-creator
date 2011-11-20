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
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Defines the root of a hierarchical model
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

@XStreamAlias("element")
public class ElementDefinition {

    @XStreamAsAttribute
    public String prefix;

    @XStreamAsAttribute
    public String localName;

    @XStreamImplicit
    public List<FieldDefinition> fields = new ArrayList<FieldDefinition>();

    @XStreamImplicit
    public List<ElementDefinition> elements = new ArrayList<ElementDefinition>();

    @XStreamOmitField
    public Path path;

    @XStreamOmitField
    private Tag tag;

    public Tag getTag() {
        if (tag == null) {
            tag = Tag.element(prefix, localName);
        }
        return tag;
    }

    public void setPaths(Path path) {
        path.push(getTag());
        this.path = new Path(path);
        if (fields != null) {
            for (FieldDefinition fieldDefinition : fields) {
                fieldDefinition.setPath(path);
            }
        }
        if (elements != null) {
            for (ElementDefinition elementDefinition : elements) {
                elementDefinition.setPaths(path);
            }
        }
        path.pop();
    }

    public void addFieldDefinitions(Map<Path, FieldDefinition> fieldDefinitions) {
        if (fields != null) {
            for (FieldDefinition fieldDefinition : fields) {
                fieldDefinitions.put(fieldDefinition.path, fieldDefinition);
            }
        }
        if (elements != null) {
            for (ElementDefinition elementDefinition : elements) {
                elementDefinition.addFieldDefinitions(fieldDefinitions);
            }
        }
    }

    public void setFactDefinitions(List<FactDefinition> factDefinitions) throws MetadataException {
        if (fields != null) {
            for (FieldDefinition fieldDefinition : fields) {
                if (fieldDefinition.factName != null) {
                    for (FactDefinition factDefinition : factDefinitions) {
                        if (fieldDefinition.factName.equals(factDefinition.name)) {
                            fieldDefinition.factDefinition = factDefinition;
                            break;
                        }
                    }
                    if (fieldDefinition.factDefinition == null) {
                        throw new MetadataException(String.format("Record Definition %s requires fact %s", prefix, fieldDefinition.factName));
                    }
                }
            }
        }
        if (elements != null) {
            for (ElementDefinition elementDefinition : elements) {
                elementDefinition.setFactDefinitions(factDefinitions);
            }
        }
    }

    public void getMappableFields(List<FieldDefinition> fieldDefinitions) {
        if (this.fields != null) {
            for (FieldDefinition fieldDefinition : this.fields) {
                fieldDefinitions.add(fieldDefinition);
            }
        }
        if (elements != null) {
            for (ElementDefinition elementDefinition : elements) {
                elementDefinition.getMappableFields(fieldDefinitions);
            }
        }
    }

    public void getFieldNames(List<String> fieldNames) {
        if (fields != null) {
            for (FieldDefinition fieldDefinition : fields) {
                fieldNames.add(fieldDefinition.getFieldNameString());
            }
        }
        if (elements != null) {
            for (ElementDefinition elementDefinition : elements) {
                elementDefinition.getFieldNames(fieldNames);
            }
        }
    }

    public String toString() {
        return String.format("Element(%s)", tag);
    }

}
