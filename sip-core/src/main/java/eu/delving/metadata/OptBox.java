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
    final OptList.Opt opt;

    public static OptBox asRoot(OptList.Opt opt) {
        return new OptBox(ROOT, null, opt);
    }

    public static OptBox asRoot(OptList optList) {
        return new OptBox(ROOT, optList, null);
    }

    private OptBox(OptRole role, OptList optList, OptList.Opt opt) {
        this.role = role;
        this.optList = optList;
        this.opt = opt;
    }

    OptBox inRoleFor(Path path) {
        if (role == DESCENDANT) {
            path = path.withoutOpts(); // the list has paths without opts so this compares properly
            OptList list = optList != null ? optList : opt.parent;
            if (pathMatch(list.key, path)) return new OptBox(KEY, optList, opt);
            if (pathMatch(list.value, path)) return new OptBox(VALUE, optList, opt);
            if (pathMatch(list.schema, path)) return new OptBox(SCHEMA, optList, opt);
            if (pathMatch(list.schemaUri, path)) return new OptBox(SCHEMA_URI, optList, opt);
        }
        return null;
    }

    private boolean pathMatch(Path listPath, Path testPath) {
        return listPath != null && listPath.equals(testPath);
    }

    OptBox createDescendant() {
        if (role != ROOT) throw new RuntimeException();
        return new OptBox(DESCENDANT, optList, opt);
    }

    public boolean isDictionary() {
        return optList != null;
    }

    public boolean isChild() {
        return role != ROOT;
    }

    public String getDictionaryName() {
        return optList.dictionary;
    }

    public String getFieldName() {
        return role.getFieldName();
    }

    public String toString() {
        if (opt != null) {
            switch (role) {
                case ROOT:
                    return opt.value == null ? opt.key : opt.value;
                case KEY:
                    return opt.key;
                case VALUE:
                    return opt.value == null ? opt.key : opt.value;
                case SCHEMA:
                    return opt.schema;
                case SCHEMA_URI:
                    return opt.schemaUri;
                default:
                    return "OPT";
            }
        }
        else {
            return String.format("Dictionary %s", optList.dictionary);
        }
    }
}
