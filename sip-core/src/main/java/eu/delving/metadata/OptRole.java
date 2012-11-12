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

import org.apache.commons.lang.WordUtils;

/**
 * What roles can an opt play in the tree of RecDefNodes
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public enum OptRole {
    ABSENT,
    ROOT,
    DYNAMIC,
    UNASSIGNED_CHILD,
    KEY,
    VALUE,
    SCHEMA,
    SCHEMA_URI;

    private final String fieldName;

    private OptRole() {
        String caps = WordUtils.capitalizeFully(toString(), new char[]{'_'}).replaceAll("_", "");
        this.fieldName = Character.toLowerCase(caps.charAt(0)) + caps.substring(1);
    }

    public String getFieldName() {
        return fieldName;
    }

    public static OptRole[] getFields() {
        return new OptRole[]{KEY, VALUE, SCHEMA, SCHEMA_URI};
    }
}
