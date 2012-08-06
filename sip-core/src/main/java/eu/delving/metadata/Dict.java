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

import java.util.List;

/**
 * A dictionary list
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

@XStreamAlias("dict")
public class Dict {
    @XStreamAsAttribute
    public String name;

    @XStreamAsAttribute
    public String uri;

    @XStreamAsAttribute
    public String lang;

    @XStreamAsAttribute
    public String displayName;

    @XStreamAsAttribute
    public Path keyPath;

    @XStreamAsAttribute
    public Path valuePath;

    @XStreamAsAttribute
    public Tag key;

    @XStreamAsAttribute
    public Tag value;

    @XStreamImplicit
    public List<DictEntry> entries;

    @XStreamOmitField
    public OptList.Opt parent;

    @XStreamAlias("entry")
    public static class DictEntry {

        @XStreamAsAttribute
        public String key;

        @XStreamAsAttribute
        public String value;

        @XStreamOmitField
        public Dict parent;

        public String toString() {
            return value;
        }
    }
}
