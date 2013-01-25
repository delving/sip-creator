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

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Part of the record definition
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

@XStreamAlias("opt-list")
public class OptList {

    @XStreamAsAttribute
    public String displayName;

    @XStreamAsAttribute
    public String dictionary;

    @XStreamAsAttribute
    public Path path;

    @XStreamAsAttribute
    public Path key;

    @XStreamAsAttribute
    public Path value;

    @XStreamAsAttribute
    public Path schema;

    @XStreamAsAttribute
    public Path schemaUri;

    @XStreamImplicit
    public List<Opt> opts;

    public void resolve(RecDef recDef) {
        for (Opt opt : opts) opt.parent = this;
        if (path == null) throw new RuntimeException("No path for OptList: " + opts);
        path = path.withDefaultPrefix(recDef.prefix);
        key = resolveTag(path, key, recDef);
        value = resolveTag(path, value, recDef);
        schema = resolveTag(path, schema, recDef);
        schemaUri = resolveTag(path, schemaUri, recDef);
        if (path.peek().isAttribute()) {
            throw new RuntimeException("An option list may not be connected to an attribute: " + path);
        }
        else {
            RecDef.Elem elem = recDef.findElem(path);
            elem.optList = this;
            if (dictionary != null) {
                Map<String, Opt> lookup = new TreeMap<String, Opt>();
                for (Opt opt : opts) lookup.put(opt.value, opt);
                recDef.optLookup.put(dictionary, lookup);
            }
        }
    }

    private Path resolveTag(Path path, Path pathX, RecDef recDef) {
        if (pathX == null) return null;
        pathX = pathX.withDefaultPrefix(recDef.prefix);
        path = path.descendant(pathX);
        if (path.peek().isAttribute()) {
            recDef.findAttr(path);
        }
        else {
            recDef.findElem(path);
        }
        return path;
    }

    public List<String> getValues() {
        List<String> values = new ArrayList<String>(opts.size());
        for (Opt opt : opts) values.add(opt.value);
        return values;
    }

    @XStreamAlias("opt")
    public static class Opt {

        @XStreamAsAttribute
        public String key;

        @XStreamAsAttribute
        public String value;

        @XStreamAsAttribute
        public String schema;

        @XStreamAsAttribute
        public String schemaUri;

        @XStreamAsAttribute
        public boolean hidden;

        public List<Dict> dicts;

        @XStreamOmitField
        public OptList parent;

        public String toString() {
            return value == null ? key : value;
        }
    }


}
