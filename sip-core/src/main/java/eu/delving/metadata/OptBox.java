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
    final DynOpt dynOpt;

    public static OptBox asRoot(OptList.Opt opt) {
        return new OptBox(ROOT, null, opt, null);
    }

    public static OptBox asRoot(OptList optList) {
        return new OptBox(ROOT, optList, null, null);
    }

    public static OptBox asDynamic(DynOpt dynOpt) {
        return new OptBox(DYNAMIC, null, null, dynOpt);
    }

    private OptBox(OptRole role, OptList optList, OptList.Opt opt, DynOpt dynOpt) {
        this.role = role;
        this.optList = optList;
        this.opt = opt;
        this.dynOpt = dynOpt;
    }

    OptBox inRoleFor(Path path) {
        if (role == UNASSIGNED_CHILD) {
            path = path.withoutOpts(); // the list has paths without opts so this compares properly
            OptList list = optList;
            if (list == null && opt != null) list = opt.parent;
            if (list == null) throw new RuntimeException("Unassigned child with no list to refer to");
            if (pathMatch(list.key, path)) return new OptBox(KEY, optList, opt, null);
            if (pathMatch(list.value, path)) return new OptBox(VALUE, optList, opt, null);
            if (pathMatch(list.schema, path)) return new OptBox(SCHEMA, optList, opt, null);
            if (pathMatch(list.schemaUri, path)) return new OptBox(SCHEMA_URI, optList, opt, null);
        }
        return null;
    }

    private boolean pathMatch(Path listPath, Path testPath) {
        return listPath != null && listPath.equals(testPath);
    }

    OptBox createDescendant() {
        if (role != ROOT) throw new RuntimeException();
        return new OptBox(UNASSIGNED_CHILD, optList, opt, dynOpt);
    }

    public boolean isDictionary() {
        return optList != null;
    }

    public boolean isChild() {
        switch (role) {
            case ABSENT:
            case ROOT:
            case DYNAMIC:
                return false;
            default:
                return true;
        }
    }

    public String getDictionaryName() {
        return optList.dictionary;
    }

    public String getDictionaryName(int index) {
        return optList.dictionary + index;
    }

    public String toString() {
        if (opt != null) {
            switch (role) {
                case ROOT:
                    return opt.value == null ? opt.key : opt.value;
                case DYNAMIC:
                    return dynOpt.value;
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
        else if (dynOpt != null) {
            return dynOpt.value;
        }
        else {
            return String.format("Dictionary %s", optList.dictionary);
        }
    }
}
