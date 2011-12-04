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

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;

import java.util.*;

/**
 * This class describes how one node is transformed into another, which is part of mapping
 * one hierarchy onto another.  It can contain a dictionary, as well as a snippet
 * of Groovy code.
 *
 * Instances of this class are placed in the RecDefNode elements of the record definition
 * so that that data structure can be used as a scaffolding to recursively write the code
 * for the Groovy builder.
 *
 * Instances are also stored in a list in the RecMapping, and upon reading a mapping they
 * are distributed into the local prototype instance of the record definition data structure.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

@XStreamAlias("node-mapping")
public class NodeMapping {
    private static final String INDENT = "   ";

    @XStreamAsAttribute
    public Path inputPath;

    @XStreamAsAttribute
    public Path outputPath;

    @XStreamAlias("dictionary")
    public Map<String, String> dictionary;

    @XStreamAlias("groovy-code")
    public List<String> groovyCode;

    public NodeMapping setOutputPath(Path path) {
        this.outputPath = path;
        return this;
    }

    public void clearDictionary() {
        dictionary = null;
    }

    public void clearCode() {
        groovyCode = null;
    }

    public void addCodeLine(String line) {
        if (groovyCode == null) {
            groovyCode = new ArrayList<String>();
        }
        groovyCode.add(line.trim());
    }

    public void createDictionary(Set<String> domainValues) {
        this.dictionary = new TreeMap<String, String>();
        for (String key : domainValues) {
            this.dictionary.put(key, "");
        }
    }

    public boolean codeLooksLike(String codeString) {
        Iterator<String> walk = groovyCode.iterator();
        for (String line : codeString.split("\n")) {
            line = line.trim();
            if (!line.isEmpty()) {
                if (!walk.hasNext()) {
                    return false;
                }
                String codeLine = walk.next();
                if (!codeLine.equals(line)) {
                    return false;
                }
            }
        }
        return !walk.hasNext();
    }

    public void setGroovyCode(String groovyCode) {
        this.groovyCode = null;
        for (String line : groovyCode.split("\n")) {
            addCodeLine(line);
        }
    }

    public void toCode(RecDefTree.Out out, String editedCode) {
        if (dictionary != null) {
            out.before();
            out.line("//lookup in dictionary!");
            out.after();
        }
        else if (groovyCode != null) {
            for (String codeLine : groovyCode) {
                if (codeIndent(codeLine) < 0) out.after();
                out.line(codeLine);
                if (codeIndent(codeLine) > 0) out.before();
            }
        }
    }

    public String toAttributeValue() {
        return "AttributeValue";
    }

    public String toString() {
        return outputPath.toString();
    }

    private static int codeIndent(String line) {
        int indent = 0;
        for (char c : line.toCharArray()) {
            switch (c) {
                case '}':
                    indent--;
                    break;
                case '{':
                    indent++;
                    break;
            }
        }
        return indent;
    }
}

