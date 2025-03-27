/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
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
 * Part of the record definition which attaches a list of options to an element.  When an option is chosen,
 * there are a number of descendant elements and attributes that can be given the option's values.
 *
 *
 */

@XStreamAlias("opt-list")
public class OptList {

    @XStreamAsAttribute
    public String dictionary;

    @XStreamAsAttribute
    public Path path;

    @XStreamAsAttribute
    public Tag value;

    @XStreamAsAttribute
    public Path key;

    @XStreamAsAttribute
    public Path schema;

    @XStreamAsAttribute
    public Path schemaUri;

    @XStreamImplicit
    public List<Opt> opts;

    @XStreamOmitField
    public boolean valuePresent;

    public void resolve(RecDef recDef) {
        for (Opt opt : opts) opt.parent = this;
        if (path == null) throw new RuntimeException("No path for opt-list: " + opts);
        if (dictionary == null) throw new RuntimeException("An opt-list must have a dictionary: " + opts);
        if (path.peek().isAttribute()) throw new RuntimeException("No opt-list allowed on an attribute: " + path);
        path = path.withDefaultPrefix(recDef.prefix);
        if (value == null) {
            value = path.peek();
        }
        else {
            value = value.defaultPrefix(recDef.prefix);
            valuePresent = true;
        }
        key = resolveTag(path, key, recDef);
        schema = resolveTag(path, schema, recDef);
        schemaUri = resolveTag(path, schemaUri, recDef);
        RecDef.Elem elem = recDef.findElem(path);
        elem.optList = this;
        Map<String, Opt> valueLookup = new TreeMap<String, Opt>();
        for (Opt opt : opts) valueLookup.put(opt.value, opt);
        recDef.valueOptLookup.put(dictionary, valueLookup);
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

        @XStreamOmitField
        public OptList parent;

        public String toString() {
            return value == null ? key : value;
        }
    }
}
