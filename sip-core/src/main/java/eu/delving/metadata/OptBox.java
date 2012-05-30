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
    final OptList.Opt opt;

    public static OptBox asRoot(OptList.Opt opt) {
        return new OptBox(ROOT, opt);
    }

    private OptBox(OptRole role, OptList.Opt opt) {
        this.role = role;
        this.opt = opt;
    }

    OptBox inRoleFor(Tag tag) {
        if (role == CHILD) {
            OptList p = opt.parent;
            if (p.key != null && p.key.equals(tag)) return new OptBox(KEY, opt);
            if (p.value != null && p.value.equals(tag)) return new OptBox(VALUE, opt);
            if (p.schema != null && p.schema.equals(tag)) return new OptBox(SCHEMA, opt);
            if (p.schemaUri != null && p.schemaUri.equals(tag)) return new OptBox(SCHEMA_URI, opt);
        }
        return null;
    }

    OptBox createChild() {
        if (role != ROOT) throw new RuntimeException();
        return new OptBox(CHILD, opt);
    }

    public boolean isChild() {
        return role != ROOT;
    }

    public String toString() {
        switch (role) {
            case ROOT:
                return opt.value;
            case KEY:
                return opt.key;
            case VALUE:
                return opt.value;
            case SCHEMA:
                return opt.schema;
            case SCHEMA_URI:
                return opt.schemaUri;
            default:
                return "OPT";
        }
    }
}
