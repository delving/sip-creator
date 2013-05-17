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

import static eu.delving.metadata.OptRole.*;

/**
 * This little container holds an Opt value and tells you which role it is playing
 * in the tree of RecDefNodes.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

class OptBox {
    final OptRole role;
    final OptList optList;

    public static OptBox forList(OptList list) {
        return new OptBox(list.value == null ? VALUE : ROOT, list);
    }

    private OptBox(OptRole role, OptList optList) {
        this.role = role;
        this.optList = optList;
    }

    OptBox inRoleFor(Path path) {
        if (role == CHILD) {
            path = path.withoutOpts(); // the list has paths without opts so this compares properly
            if (optList == null) throw new RuntimeException("Missing optList");
            if (optList.value.equals(path.peek())) return new OptBox(VALUE, optList);
            if (pathMatch(optList.key, path)) return new OptBox(KEY, optList);
            if (pathMatch(optList.schema, path)) return new OptBox(SCHEMA, optList);
            if (pathMatch(optList.schemaUri, path)) return new OptBox(SCHEMA_URI, optList);
        }
        return null;
    }

    private boolean pathMatch(Path listPath, Path testPath) {
        return listPath != null && listPath.equals(testPath);
    }

    OptBox createDescendant() {
        if (role != ROOT) throw new RuntimeException();
        return new OptBox(CHILD, optList);
    }

    public String getDictionaryName() {
        return optList.dictionary;
    }

    public String getDictionaryName(int index) {
        return optList.dictionary + index;
    }

    public String getOptReference() {
        if (role == ROOT) {
            return String.format("OptList.Opt _found%s", optList.dictionary);
        }
        else {
            return String.format("_found%s.%s", optList.dictionary, role.getFieldName());
        }
    }

    public String toString() {
        if (optList.dictionary != null) {
            return String.format("Opt(%s/%s)", role, optList.dictionary);
        }
        else {
            return String.format("Opt(%s)", role);
        }
    }

}
