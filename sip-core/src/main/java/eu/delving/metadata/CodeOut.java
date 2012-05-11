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
    private int indentLevel;
    private NodeMapping nodeMapping;
    private StringBuilder code = new StringBuilder();
    private CodeOut nodeMappingCodeOut;
    private String nodeMappingCode;
    private int depth;

    public static CodeOut create(NodeMapping nodeMapping) {
        return new CodeOut(nodeMapping);
    }

    public static CodeOut create() {
        return new CodeOut(null);
    }

    private CodeOut(NodeMapping nodeMapping) {
        this.nodeMapping = nodeMapping;
    }

    public void start(NodeMapping nodeMapping) {
        if (this.nodeMapping == nodeMapping) {
            if (depth == 0) nodeMappingCodeOut = new CodeOut(null);
            depth++;
        }
    }

    public void end(NodeMapping nodeMapping) {
        if (this.nodeMapping == nodeMapping && nodeMappingCodeOut != null) {
            depth--;
            if (depth == 0) {
                nodeMappingCode = nodeMappingCodeOut.toString();
                nodeMappingCodeOut = null;
            }
        }
    }

    public CodeOut line(String string, Object... params) {
        if (nodeMappingCodeOut != null) nodeMappingCodeOut.line(string, params);
        for (int walk = 0; walk < indentLevel; walk++) code.append(INDENT);
        if (params.length == 0) {
            code.append(string).append('\n');
        }
        else {
            code.append(String.format(string, params)).append('\n');
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
        if (nodeMappingCodeOut != null) nodeMappingCodeOut.in();
        indentLevel++;
        return this;
    }

    public CodeOut out() {
        if (nodeMappingCodeOut != null) nodeMappingCodeOut.out();
        indentLevel--;
        return this;
    }

    public String getNodeMappingCode() {
        return nodeMappingCode;
    }

    @Override
    public String toString() {
        return code.toString();
    }
}
