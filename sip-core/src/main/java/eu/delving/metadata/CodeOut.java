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

import java.util.Stack;

/**
 * Easy indenting output to a string builder, which has a special facility for collecting the code associated
 * with one particular node mapping, so that each can be edited individually
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class CodeOut {
    private static final String INDENT = "   ";
    private int indentLevel;
    private StringBuilder code = new StringBuilder();
    private Stack<Frame> stack = new Stack<Frame>();

    public static CodeOut create() {
        return new CodeOut();
    }

    public void start(NodeMapping nodeMapping) {
        if (nodeMapping.generatedCode == null) stack.push(new Frame(nodeMapping));
    }

    public void end(NodeMapping nodeMapping) {
        if (stack.isEmpty()) return;
        if (stack.peek().nodeMapping == nodeMapping && nodeMapping.generatedCode == null) stack.pop().finish();
    }

    public CodeOut line(String string, Object... params) {
        for (Frame frame : stack) frame.codeOut.line(string, params);
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
        for (Frame frame : stack) frame.codeOut.in();
        indentLevel++;
        return this;
    }

    public CodeOut out() {
        for (Frame frame : stack) frame.codeOut.out();
        indentLevel--;
        return this;
    }

    @Override
    public String toString() {
        return code.toString();
    }

    private static class Frame {
        private NodeMapping nodeMapping;
        private CodeOut codeOut = new CodeOut();

        private Frame(NodeMapping nodeMapping) {
            this.nodeMapping = nodeMapping;
        }

        void finish() {
            nodeMapping.generatedCode = StringUtil.stringToLines(codeOut.toString());
        }
    }
}
