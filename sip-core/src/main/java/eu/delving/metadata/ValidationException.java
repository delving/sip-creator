/*
 * Copyright 2011 DELVING BV
 *
 * Licensed under the EUPL, Version 1.0 or? as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the
 * Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.delving.metadata;

import groovy.util.Node;

/**
 * An assertion error wrapped in a checked exception.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class ValidationException extends Exception {
    private static final String ASSERT = "assert ";
    private static final String EXPRESSION = ". Expression: "; // note: pretty specific to Groovy's whim
    private AssertionError assertionError;
    private Node record;
    private int recordNumber;

    public ValidationException(AssertionError assertionError, Node record, int recordNumber) {
        super("Record Invalid.");
        this.assertionError = assertionError;
        this.record = record;
        this.recordNumber = recordNumber;
    }

    public AssertionError getAssertionError() {
        return assertionError;
    }

    public Node getRecord() {
        return record;
    }

    @Override
    public String getMessage() {
        String message = assertionError.getMessage();
        if (message.startsWith(ASSERT)) {
            message = message.substring(ASSERT.length(), message.indexOf("\n"));
        }
        else if (message.indexOf(EXPRESSION) > 0){
            int expr = message.indexOf(EXPRESSION);
            message = message.substring(0, expr);
        }
        return message;
    }

    public int getRecordNumber() {
        return recordNumber;
    }
}
