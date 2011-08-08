package eu.delving.metadata;

/**
 * An assertion error wrapped in a checked exception
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class ValidationException extends Exception {
    private static final String ASSERT = "assert ";
    private static final String EXPRESSION = ". Expression: "; // note: pretty specific to Groovy's whim
    private AssertionError assertionError;
    private int recordNumber;

    public ValidationException(AssertionError assertionError, int recordNumber) {
        super("Record Invalid.");
        this.assertionError = assertionError;
        this.recordNumber = recordNumber;
    }

    public AssertionError getAssertionError() {
        return assertionError;
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
