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

import java.util.Iterator;

/**
 * Handle the creation of Groovy variable names from paths
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class GroovyVariable {

    public static String fromPaths(Path ancestor, Path path, boolean loop) {
        StringBuilder out = new StringBuilder();
        if (ancestor != null) {
            out.append(paramName(ancestor));
            path = path.minusAncestor(ancestor);
        }
        out.append(pathToVariable(path, loop));
        return out.toString();
    }

    public static String paramName(Path path) {
        return String.format("this_%s", nameToVariable(path.peek().getLocalName()));
    }

    private static String nameToVariable(String name) {
        if (name.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(name.length());
        int n = name.length();
        for (int i = 0; i < n; i++) {
            char c = name.charAt(i);
            int pos = UNICODE.indexOf(c);
            if (pos > -1) {
                sb.append(PLAIN_ASCII.charAt(pos));
            }
            else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String pathToVariable(Path path, boolean loop) {
        if (path.isEmpty()) return "";
        if (path.peek().isAttribute() && loop) throw new RuntimeException("Attribute cannot be a loop variable");
        StringBuilder sb = new StringBuilder();
        Iterator<Tag> walk = path.iterator();
        boolean first = true;
        while (walk.hasNext()) {
            Tag tag = walk.next();
            if (walk.hasNext()) {
                sb.append(nameToVariable(tag.toString()));
                if (!first) {
                    sb.append("[0]"); // todo: what about empty array?
                }
                sb.append('.');
            }
            else if (tag.isAttribute()) {
                sb.append(String.format("['%s']", tag.toString()));
            }
            else if (loop) {
                sb.append(nameToVariable(tag.toString()));
            }
            else {
                sb.append(nameToVariable(tag.toString())).append("[0]"); // todo: what about empty array?
            }
            first = false;
        }
        return sb.toString();
    }

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
}
