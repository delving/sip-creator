/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.metadata;

/**
 * Easy indenting output to a string builder, which has a special facility for collecting the code associated
 * with one particular node mapping, so that each can be edited individually
 */
public class CodeOut {
    private static final String INDENT = "   ";
    private int indentLevel;
    private NodeMapping nodeMapping;
    private StringBuilder code = new StringBuilder();
    private CodeOut nodeMappingCodeOut;
    private String nodeMappingCode;

    public void setNodeMapping(NodeMapping nodeMapping) {
        this.nodeMapping = nodeMapping;
    }

    public void start(NodeMapping nodeMapping) {
        if (this.nodeMapping == nodeMapping && nodeMappingCode == null) {
            nodeMappingCodeOut = new CodeOut();
        }
    }

    public void end(NodeMapping nodeMapping) {
        if (this.nodeMapping == nodeMapping && nodeMappingCodeOut != null) {
            nodeMappingCode = nodeMappingCodeOut.toString();
            nodeMappingCodeOut = null;
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
