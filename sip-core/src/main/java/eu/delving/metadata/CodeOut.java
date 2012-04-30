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

/**
 * Easy indenting output to a string builder
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class CodeOut {
    private static final String INDENT = "   ";
    private CodeOut parent;
    private int indentLevel;
    private StringBuilder stringBuilder = new StringBuilder();

    public static CodeOut create() {
        return new CodeOut(null);
    }

    private CodeOut(CodeOut parent) {
        this.parent = parent;
    }

    public CodeOut createChild() {
        return new CodeOut(this);
    }

    public CodeOut line(String string, Object... params) {
        if (parent != null) parent.line(string, params);
        for (int walk = 0; walk < indentLevel; walk++) stringBuilder.append(INDENT);
        if (params.length == 0) {
            stringBuilder.append(string).append('\n');
        }
        else {
            stringBuilder.append(String.format(string, params)).append('\n');
        }
        return this;
    }
    
    public CodeOut line_(String string, Object... params) {
        return line(string, params).in();
    }
    
    public CodeOut _line(String string, Object... params) {
        return out().line(string, params);
    }

    public CodeOut in() {
        if (parent != null) parent.in();
        indentLevel++;
        return this;
    }

    public CodeOut out() {
        if (parent != null) parent.out();
        indentLevel--;
        return this;
    }

    @Override
    public String toString() {
        return stringBuilder.toString();
    }
}
