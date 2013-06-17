/*
 * Copyright 2013 Delving BV
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
package eu.delving.crm;

import org.apache.log4j.Logger;

import java.util.*;

/**
 * Execute URI functions
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class FunctionExecutor implements MapToCRM.URIGenerator {
    private static final Logger LOG = Logger.getLogger(FunctionExecutor.class);
    private static final Map<String, URIGeneratorFunction> FUNCTION_MAP = new TreeMap<String, URIGeneratorFunction>();
    private static final String CLASS_NAME = "className";
    private static final String DOMAIN_URI = "domainURI";
    private static final String MUSEUM_NAME = "museumName";
    private static final String IDENTIFIER = "identifier";
    private static final String TYPE = "type";
    private static final String NOTE = "note";

    @Override
    public Set<String> getArgNames(String name) {
        TreeSet<String> names = new TreeSet<String>();
        URIGeneratorFunction function = FUNCTION_MAP.get(name);
        if (function != null) {
            Collections.addAll(names, function.getArgNames());
        }
        return names;
    }

    @Override
    public String createURI(MapToCRM.Entity entity, String domainURI, String name, Map<String, String> argMap) {
        if (domainURI != null) {
            argMap.put(DOMAIN_URI, domainURI);
        }
        argMap.put(CLASS_NAME, entity.tag.toString());
        return createURI(name, argMap);
    }

    public String createURI(String name, Map<String, String> argMap) {
        URIGeneratorFunction function = FUNCTION_MAP.get(name);
        if (function != null) {
            return function.generateURI(name, argMap);
        }
        StringBuilder out = new StringBuilder(name);
        out.append("(");
        int left = argMap.size();
        for (Map.Entry<String, String> entry : argMap.entrySet()) {
            out.append(entry.getKey()).append("=\"").append(entry.getValue()).append("\"");
            if (--left > 0) out.append(", ");
        }
        out.append(")");
        LOG.info(out.toString());
        return "URI";
    }

    public interface URIGeneratorFunction {
        String getName();

        String[] getArgNames();

        String generateURI(String name, Map<String, String> argMap);
    }

    private static class PhysicalObject implements URIGeneratorFunction {

        @Override
        public String getName() {
            return "PhysicalObject";
        }

        @Override
        public String[] getArgNames() {
            return new String[]{MUSEUM_NAME, IDENTIFIER};
        }

        @Override
        public String generateURI(String name, Map<String, String> argMap) {
            String museumName = argMap.get(MUSEUM_NAME);
            String identifier = argMap.get(IDENTIFIER);
            if (museumName != null && identifier != null) {
                return encode(PRE + ":(Main_Object)" + museumName + "-" + identifier);
            }
            return uuid();
        }
    }

    private static class PhysicalThing implements URIGeneratorFunction {

        @Override
        public String getName() {
            return "PhysicalThing";
        }

        @Override
        public String[] getArgNames() {
            return new String[]{IDENTIFIER};
        }

        @Override
        public String generateURI(String name, Map<String, String> argMap) {
            String className = argMap.get(CLASS_NAME);
            String identifier = argMap.get(IDENTIFIER);
            if (identifier != null) {
                return encode(PRE + ":(Thing" + dashClassName(className) + ")" + identifier);
            }
            return uuid();
        }
    }

    private static class Type implements URIGeneratorFunction {

        @Override
        public String getName() {
            return "Type";
        }

        @Override
        public String[] getArgNames() {
            return new String[]{IDENTIFIER};
        }

        @Override
        public String generateURI(String name, Map<String, String> argMap) {
            String className = argMap.get(CLASS_NAME);
            String identifier = argMap.get(IDENTIFIER);
            if (identifier != null) {
                return encode(PRE + ":(Type" + dashClassName(className, "Type") + ")" + identifier);
            }
            return uuid();
        }
    }

    private static class Appellation implements URIGeneratorFunction {

        @Override
        public String getName() {
            return "Appellation";
        }

        @Override
        public String[] getArgNames() {
            return new String[]{IDENTIFIER};
        }

        @Override
        public String generateURI(String name, Map<String, String> argMap) {
            String className = argMap.get(CLASS_NAME);
            String domainURI = argMap.get(DOMAIN_URI);
            String identifier = argMap.get(IDENTIFIER);
            if (domainURI != null && identifier != null) {
                return encode(PRE + ":(Appellation" + dashClassName(className, "Appellation") + ")@" + domainURI + "@" + identifier);
            }
            return uuid();
        }
    }

    private static class Literal implements URIGeneratorFunction {

        @Override
        public String getName() {
            return "Literal";
        }

        @Override
        public String[] getArgNames() {
            return new String[]{TYPE, NOTE};
        }

        @Override
        public String generateURI(String name, Map<String, String> argMap) {
            String type = argMap.get(TYPE);
            String note = argMap.get(NOTE);
            return "literal:"+type+":"+note;
        }
    }

    private static String dashClassName(String className, String defaultName) {
        return (className.equals(defaultName) ? "" : "-" + className);
    }

    private static String dashClassName(String className) {
        return (className.isEmpty() ? "" : "-" + className);
    }

    private static String uuid() {
        return URN + ":uuid:" + UUID.randomUUID();
    }

    private static void register(URIGeneratorFunction generator) {
        FUNCTION_MAP.put(generator.getName(), generator);
    }

    static {
        register(new PhysicalObject());
        register(new PhysicalThing());
        register(new Type());
        register(new Appellation());
        register(new Literal());
    }

    private static String encode(String s) {
        StringBuilder out = new StringBuilder();
        int len = s.length();
        for (int i = 0; i < len; i++) {
            int ch = s.charAt(i);
            if ('A' <= ch && ch <= 'Z') {        // 'A'..'Z'
                out.append((char) ch);
            }
            else if ('a' <= ch && ch <= 'z') {    // 'a'..'z'
                out.append((char) ch);
            }
            else if ('0' <= ch && ch <= '9') {    // '0'..'9'
                out.append((char) ch);
            }
            else if (ch == ' ') {            // space
                out.append('_');
            }
            else if (ch == '-' || ch == '_' // unreserved
                    || ch == '.' || ch == '!' || ch == '*' || ch == '(' || ch == ')' ||
                    ch == '+' || ch == ',' || ch == ':' || ch == '=' || ch == '@' || ch == ';' || ch == '$') {
                out.append((char) ch);
            }
            else if (ch <= 0x007f) {        // other ASCII
                out.append(hex[ch]);
            }
            else if (ch <= 0x07FF) {        // non-ASCII <= 0x7FF
                out.append(hex[0xC0 | (ch >> 6)]);
                out.append(hex[0x80 | (ch & 0x3F)]);
            }
            else {// 0x7FF < ch <= 0xFFFF
                out.append(hex[0xe0 | (ch >> 12)]);
                out.append(hex[0x80 | ((ch >> 6) & 0x3F)]);
                out.append(hex[0x80 | (ch & 0x3F)]);
            }
        }
        return out.toString();
    }

    final static String[] hex = {
            "%00", "%01", "%02", "%03", "%04", "%05", "%06", "%07",
            "%08", "%09", "%0a", "%0b", "%0c", "%0d", "%0e", "%0f",
            "%10", "%11", "%12", "%13", "%14", "%15", "%16", "%17",
            "%18", "%19", "%1a", "%1b", "%1c", "%1d", "%1e", "%1f",
            "%20", "%21", "%22", "%23", "%24", "%25", "%26", "%27",
            "%28", "%29", "%2a", "%2b", "%2c", "%2d", "%2e", "%2f",
            "%30", "%31", "%32", "%33", "%34", "%35", "%36", "%37",
            "%38", "%39", "%3a", "%3b", "%3c", "%3d", "%3e", "%3f",
            "%40", "%41", "%42", "%43", "%44", "%45", "%46", "%47",
            "%48", "%49", "%4a", "%4b", "%4c", "%4d", "%4e", "%4f",
            "%50", "%51", "%52", "%53", "%54", "%55", "%56", "%57",
            "%58", "%59", "%5a", "%5b", "%5c", "%5d", "%5e", "%5f",
            "%60", "%61", "%62", "%63", "%64", "%65", "%66", "%67",
            "%68", "%69", "%6a", "%6b", "%6c", "%6d", "%6e", "%6f",
            "%70", "%71", "%72", "%73", "%74", "%75", "%76", "%77",
            "%78", "%79", "%7a", "%7b", "%7c", "%7d", "%7e", "%7f",
            "%80", "%81", "%82", "%83", "%84", "%85", "%86", "%87",
            "%88", "%89", "%8a", "%8b", "%8c", "%8d", "%8e", "%8f",
            "%90", "%91", "%92", "%93", "%94", "%95", "%96", "%97",
            "%98", "%99", "%9a", "%9b", "%9c", "%9d", "%9e", "%9f",
            "%a0", "%a1", "%a2", "%a3", "%a4", "%a5", "%a6", "%a7",
            "%a8", "%a9", "%aa", "%ab", "%ac", "%ad", "%ae", "%af",
            "%b0", "%b1", "%b2", "%b3", "%b4", "%b5", "%b6", "%b7",
            "%b8", "%b9", "%ba", "%bb", "%bc", "%bd", "%be", "%bf",
            "%c0", "%c1", "%c2", "%c3", "%c4", "%c5", "%c6", "%c7",
            "%c8", "%c9", "%ca", "%cb", "%cc", "%cd", "%ce", "%cf",
            "%d0", "%d1", "%d2", "%d3", "%d4", "%d5", "%d6", "%d7",
            "%d8", "%d9", "%da", "%db", "%dc", "%dd", "%de", "%df",
            "%e0", "%e1", "%e2", "%e3", "%e4", "%e5", "%e6", "%e7",
            "%e8", "%e9", "%ea", "%eb", "%ec", "%ed", "%ee", "%ef",
            "%f0", "%f1", "%f2", "%f3", "%f4", "%f5", "%f6", "%f7",
            "%f8", "%f9", "%fa", "%fb", "%fc", "%fd", "%fe", "%ff"
    };
    private static final String URN = "URN";
    private static final String ISO = "iso21127";
    private static final String PRE = URN + ":" + ISO;


}
