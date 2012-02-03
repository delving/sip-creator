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
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * This class describes a utility function (closure) which is available in the
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

@XStreamAlias("mapping-function")
public class MappingFunction implements Comparable<MappingFunction> {

    @XStreamAsAttribute
    public String name;

    @XStreamAlias("sample-input")
    public List<String> sampleInput;
    
    @XStreamAlias("groovy-code")
    public List<String> groovyCode;

    public MappingFunction() {
    }

    public MappingFunction(String name) {
        this.name = name;
    }

    public void setSampleInput(String sampleInput) {
        this.sampleInput = Arrays.asList(sampleInput.split("\n"));
    }

    public void setGroovyCode(String groovyCode) {
        this.groovyCode = null;
        for (String line : groovyCode.split("\n")) addCodeLine(line);
    }

    public boolean isCodeLike(String codeString) {
        if (groovyCode == null) return false;
        Iterator<String> walk = groovyCode.iterator();
        for (String line : codeString.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (!walk.hasNext()) return false;
            if (!walk.next().equals(line)) return false;
        }
        return !walk.hasNext();
    }

    public String getSampleInputString() {
        return StringUtils.join(sampleInput, '\n');
    }

    public String getUserCode(String editedCode) {
        Out out = new Out();
        toUserCode(out, editedCode);
        return out.toString();
    }

    public String getUserCode() {
        return getUserCode(null);
    }

    public String toCode(String editedCode) {
        Out out = new Out();
        toCode(out, editedCode);
        return out.toString();
    }
    
    public void toCode(Out out) {
        toCode(out, null);
    }

    public void toCode(Out out, String editedCode) {
        out.line_(String.format("def %s = { it ->", name));
        toUserCode(out, editedCode);
        out._line("}");
    }

    private void toUserCode(Out out, String editedCode) {
        if (editedCode != null) {
            StringUtil.indentCode(editedCode, out);
        }
        else if (groovyCode != null) {
            StringUtil.indentCode(groovyCode, out);
        }
        else {
            out.line("it");
        }
    }

    private void addCodeLine(String line) {
        if (groovyCode == null) groovyCode = new ArrayList<String>();
        line = line.trim();
        if (!line.isEmpty()) groovyCode.add(line);
    }


    public String toString() {
        return name + "(it)";
    }

    @Override
    public int compareTo(MappingFunction mappingFunction) {
        return this.name.compareTo(mappingFunction.name);
    }
}

