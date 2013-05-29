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

import java.util.List;

/**
 * An assertion about data content
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

@XStreamAlias("assert")
public class Assertion {
    @XStreamAsAttribute
    public String xpath;

    public List<String> allowed;

    public String condition;

    public String code;

    public boolean hasCondition() {
        return code != null || condition != null || allowed != null;
    }

    @XStreamAlias("on-fail")
    public String onFail;

    public String getScript() {
        if (code != null) {
            return code.trim();
        }
        else if (condition != null) {
            if (onFail != null) {
                return String.format("if (!(%s)) {\n\"%s\"\n}", condition, onFail);
            }
            else {
                return String.format("if (!(%s)) {\n\"Failed condition: %s\"\n}", condition, condition);
            }
        }
        else if (allowed != null) {
            StringBuilder out = new StringBuilder("if (!(it.toString() in [\n");
            for (String value : allowed) {
                out.append(String.format("'''%s''',\n", value));
            }
            out.append(String.format("])) {\n\"%s\"\n}", onFail));
            return out.toString();
        }
        else {
            return "";
        }
    }

    @XStreamAlias("assertion-list")
    public static class AssertionList {
        @XStreamImplicit
        public List<Assertion> assertions;
    }
}
