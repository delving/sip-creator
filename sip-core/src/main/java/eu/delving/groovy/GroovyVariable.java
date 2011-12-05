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

package eu.delving.groovy;

import eu.delving.metadata.Path;
import eu.delving.metadata.Tag;

/**
 * Handle the creation of Groovy variable names from paths
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class GroovyVariable {

    public static String name(Path path) {
        return name(null, path);
    }

    public static String name(Path ancestor, Path path) {
        StringBuilder out = new StringBuilder();
        if (ancestor == null) {
            out.append("input");
        }
        else {
            out.append(paramName(ancestor));
            path = path.minusAncestor(ancestor).chop(-1);
        }
        out.append(pathToVariable(path));
        return out.toString();
    }

    public static String paramName(Path path) {
        return String.format("X%d", path.size());
    }

    private static final String PLAIN_ASCII =
            "AaEeIiOoUu"    // grave
                    + "AaEeIiOoUuYy"  // acute
                    + "AaEeIiOoUuYy"  // circumflex
                    + "AaOoNn"        // tilde
                    + "AaEeIiOoUuYy"  // umlaut
                    + "Aa"            // ring
                    + "Cc"            // cedilla
                    + "OoUu"          // double acute
                    + "_"             // dash
                    + "_"             // dot
                    + "_"             // colon
            ;

    private static final String UNICODE =
            "\u00C0\u00E0\u00C8\u00E8\u00CC\u00EC\u00D2\u00F2\u00D9\u00F9"
                    + "\u00C1\u00E1\u00C9\u00E9\u00CD\u00ED\u00D3\u00F3\u00DA\u00FA\u00DD\u00FD"
                    + "\u00C2\u00E2\u00CA\u00EA\u00CE\u00EE\u00D4\u00F4\u00DB\u00FB\u0176\u0177"
                    + "\u00C3\u00E3\u00D5\u00F5\u00D1\u00F1"
                    + "\u00C4\u00E4\u00CB\u00EB\u00CF\u00EF\u00D6\u00F6\u00DC\u00FC\u0178\u00FF"
                    + "\u00C5\u00E5"
                    + "\u00C7\u00E7"
                    + "\u0150\u0151\u0170\u0171"
                    + "-"
                    + "."
                    + ":";

    private static String pathToVariable(Path path) {
        if (path.isEmpty()) return "";
        Tag attr = path.peek().isAttribute() ? path.pop() : null;
        String s = path.toString();
        StringBuilder sb = new StringBuilder();
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == '/') {
                sb.append(".");
            }
            else if (c == ':') {
                sb.append('_');
            }
            else {
                int pos = UNICODE.indexOf(c);
                if (pos > -1) {
                    sb.append(PLAIN_ASCII.charAt(pos));
                }
                else {
                    sb.append(c);
                }
            }
        }
        if (attr != null) sb.append(String.format("['%s']", attr.toString()));
        return sb.toString();
    }
}
