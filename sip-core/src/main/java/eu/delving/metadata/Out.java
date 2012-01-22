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

/**
 * Easy indenting output to a string builder
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class Out {
    private static final String INDENT = "   ";
    private int indentLevel;
    private StringBuilder stringBuilder = new StringBuilder();
    
    public Out line() {
        return line("");
    }

    public Out line(String string, Object... params) {
        for (int walk = 0; walk < indentLevel; walk++) stringBuilder.append(INDENT);
        if (params.length == 0) {
            stringBuilder.append(string).append('\n');
        }
        else {
            stringBuilder.append(String.format(string, params)).append('\n');
        }
        return this;
    }
    
    public Out line_(String string, Object... params) {
        return line(string, params).in();
    }
    
    public Out _line(String string, Object... params) {
        return out().line(string, params);
    }

    public Out in() {
        indentLevel++;
        return this;
    }

    public Out out() {
        indentLevel--;
        return this;
    }

    @Override
    public String toString() {
        return stringBuilder.toString();
    }
}
