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
 * The different possible operators between a list and a closure in the generated code.
 *
 *
 */

public enum Operator {
    ALL("All Entries", "*"),
    FIRST("First Entry", "**"),
    COMMA_DELIM("Comma-Delimited", "* ', ' *"),
    SEMI_DELIM("Semicolon-Delimited", "* '; ' *"),
    SPACE_DELIM("Space-Delimited", "* ' ' *"),
    PIPE_DELIM("Pipe-delimited", "* '|' * "),
    AS_ARRAY("As-Array", ">>");

    private final String display;
    private final String codeString;

    private Operator(String display, String codeString) {
        this.display = display;
        this.codeString = codeString;
    }

    public String getCodeString() {
        return codeString;
    }

    @Override
    public String toString() {
        return display;
    }
}
