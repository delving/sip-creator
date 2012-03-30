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
 * The different possible operators between a list and a closure in the generated code.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public enum Operator {
    ALL("All Entries", "*"),
    FIRST("First Entry", "**");
    
    private final String display;
    private final String opchar;

    private Operator(String display, String opchar) {
        this.display = display;
        this.opchar = opchar;
    }

    public String getChar() {
        return opchar;
    }

    @Override
    public String toString() {
        return display;
    }
}
