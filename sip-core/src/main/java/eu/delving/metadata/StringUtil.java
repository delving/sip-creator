/*
 * Copyright 2011 DELVING BV
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

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.util.*;

/**
 * Handle some common string manipulations.
 * <p/>
 * Remove the frightening things from tag strings so that they can become proper Groovy variable names.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class StringUtil {

    private static final Hasher HASHER = new Hasher();

    public static String toDictionaryName(NodeMapping nodeMapping) {
        return "Dictionary" + HASHER.getHashString(nodeMapping.outputPath.toString()).substring(16);
    }

    public static void toDictionaryCode(NodeMapping nodeMapping, CodeOut codeOut) {
        if (nodeMapping.dictionary == null) return;
        String name = toDictionaryName(nodeMapping);
        codeOut.line_(String.format("def %s = [", name));
        Iterator<Map.Entry<String, String>> walk = nodeMapping.dictionary.entrySet().iterator();
        while (walk.hasNext()) {
            Map.Entry<String, String> entry = walk.next();
            codeOut.line(String.format("'''%s''':'''%s'''%s",
                    StringUtil.sanitizeGroovy(entry.getKey()),
                    StringUtil.sanitizeGroovy(entry.getValue()),
                    walk.hasNext() ? "," : ""
            ));
        }
        codeOut._line("]");
        codeOut.line_("def from%s = { value ->", name);
        codeOut.line_("if (value) {");
        codeOut.line("def v = %s[value.sanitize()];", name);
        codeOut.line_("if (v) {");
        codeOut.line_("if (v.endsWith(':')) {");
        codeOut.line("return \"${v} ${value}\"");
        codeOut._line("} else {").in();
        codeOut.line("return v");
        codeOut._line("}");
        codeOut._line("}");
        codeOut._line("}");
        codeOut.line("return ''");
        codeOut._line("}");
    }

    public static String toLoopGroovyParam(Path path) {
        Tag inner = path.getTag(1);
        return toGroovyIdentifier(inner);
    }

    public static String toLeafGroovyParam(Path path) {
        Tag inner = path.getTag(0);
        return toGroovyIdentifier(inner);
    }

    public static String toLoopRef(Path path) {
        Tag outer = path.getTag(0);
        Tag inner = path.getTag(1);
        if (outer == null || inner == null) throw new RuntimeException("toLoopRef called on "+path);
        return toGroovyIdentifier(outer) + toGroovyReference(inner);
    }

    public static String toMapExpression(NodeMapping nodeMapping) {
        List<Path> paths = nodeMapping.getInputPaths();
        StringBuilder expression = new StringBuilder("(");
        Iterator<Path> walk = paths.iterator();
        while (walk.hasNext()) {
            Path inputPath = walk.next();
            if (inputPath.size() < 2) throw new RuntimeException("Path too short");
            expression.append(StringUtil.toMapExpressionElement(inputPath));
            if (walk.hasNext()) expression.append(" | ");
        }
        expression.append(")");
        return expression.toString();
    }

    public static String toMapExpressionElement(Path path) {
        if (path.size() != 2) throw new RuntimeException("Yes it is");
        Tag outer = path.getTag(-2); // todo: is this really different from the above?
        Tag inner = path.getTag(-1);
        return toGroovyIdentifier(outer) + toGroovyReference(inner);
    }

    public static String toGroovyIdentifier(Tag tag) {
        return "_" + StringUtil.tagToVariable(tag.toString()) + (tag.isDescendent() ? "_" + tag.getDescendency() : "");
    }

    private static String toGroovyReference(Tag tag) {
        return tag.isAttribute() ? String.format("['@%s']", tag.toString()) : "." + tagToVariable(tag.toString());
    }

    public static String documentToString(Document document) {
        try {
            int length = document.getLength();
            return document.getText(0, length);
        }
        catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
    }

    public static String linesToString(List<String> list) {
        StringBuilder builder = new StringBuilder();
        if (list != null) for (String line : list) {
            line = line.trim();
            if (!line.isEmpty()) builder.append(line).append('\n');
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    public static List<String> stringToLines(String string) {
        List<String> lines = new ArrayList<String>();
        if (string != null) for (String line : Arrays.asList(string.split("\n"))) {
            line = line.trim();
            if (!line.isEmpty()) lines.add(line);
        }
        return lines.isEmpty() ? null : lines;
    }

    public static void indentCode(String code, CodeOut out) {
        indentCode(Arrays.asList(code.split("\n")), out);
    }

    public static void indentCode(List<String> code, CodeOut out) {
        for (String codeLine : code) {
            int indent = codeIndent(codeLine);
            if (indent < 0) out.out();
            out.line(codeLine);
            if (indent > 0) out.in();
        }
    }

    private static int codeIndent(String line) {
        int indent = 0;
        for (char c : line.toCharArray()) {
            if (c == '}') indent--;
            if (c == '{') indent++;
        }
        return indent;
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
            ;

    private static final String DELETED = "-.;:_";

    private static final String UNICODE =
            "\u00C0\u00E0\u00C8\u00E8\u00CC\u00EC\u00D2\u00F2\u00D9\u00F9"
                    + "\u00C1\u00E1\u00C9\u00E9\u00CD\u00ED\u00D3\u00F3\u00DA\u00FA\u00DD\u00FD"
                    + "\u00C2\u00E2\u00CA\u00EA\u00CE\u00EE\u00D4\u00F4\u00DB\u00FB\u0176\u0177"
                    + "\u00C3\u00E3\u00D5\u00F5\u00D1\u00F1"
                    + "\u00C4\u00E4\u00CB\u00EB\u00CF\u00EF\u00D6\u00F6\u00DC\u00FC\u0178\u00FF"
                    + "\u00C5\u00E5"
                    + "\u00C7\u00E7"
                    + "\u0150\u0151\u0170\u0171";

    public static String tagToVariable(String s) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder();
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            int pos = UNICODE.indexOf(c);
            if (pos > -1) {
                sb.append(PLAIN_ASCII.charAt(pos));
            }
            else if (DELETED.indexOf(c) < 0) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String sanitizeGroovy(String thing) {
        return thing.replaceAll("'", "\\\\'")
                .replaceAll("\n", " ")
                .replaceAll(" +", " ");
    }

}
