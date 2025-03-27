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

package eu.delving.groovy;

/**
 * Exception thrown during mapping operations.
 * Provides detailed error categorization and preserves cause information.
 * Can be used for error reporting and statistics gathering.
 */
public class MappingException extends Exception {

    private final ErrorType errorType;

    /**
     * Categorizes different types of mapping errors for statistical analysis
     * and error handling.
     */
    public enum ErrorType {
        /** Error during script compilation */
        COMPILATION("Compilation error"),

        /** Error during script execution */
        EXECUTION("Execution error"),

        /** XML Schema validation error */
        VALIDATION("Schema validation error"),

        /** Error in output structure */
        STRUCTURE("Structure error"),

        /** Error in content values */
        CONTENT("Content error"),

        /** Error in RDF conversion */
        RDF("RDF conversion error");

        private final String description;

        ErrorType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Creates a simple mapping exception with just a message.
     * Defaults to EXECUTION error type.
     *
     * @param message Detailed error message
     */
    public MappingException(String message) {
        this(ErrorType.EXECUTION, message);
    }

    /**
     * Creates a mapping exception with message and cause.
     * Defaults to EXECUTION error type.
     *
     * @param message Detailed error message
     * @param cause   The underlying cause
     */
    public MappingException(String message, Throwable cause) {
        this(ErrorType.EXECUTION, message, cause);
    }

    /**
     * Creates a new MappingException with type and message.
     *
     * @param type    The type of mapping error
     * @param message Detailed error message
     */
    public MappingException(ErrorType type, String message) {
        super(message);
        this.errorType = type;
    }

    /**
     * Creates a new MappingException with type, message and cause.
     *
     * @param type    The type of mapping error
     * @param message Detailed error message
     * @param cause   The underlying cause
     */
    public MappingException(ErrorType type, String message, Throwable cause) {
        super(message, cause);
        this.errorType = type;
    }

    /**
     * Gets the type of mapping error.
     *
     * @return The error type
     */
    public ErrorType getErrorType() {
        return errorType;
    }

    /**
     * Creates a formatted error message including the error type.
     *
     * @return Formatted error message
     */
    @Override
    public String getMessage() {
        return String.format("[%s] %s", errorType.getDescription(), super.getMessage());
    }

    /**
     * Checks if this exception is of a particular error type.
     *
     * @param type The error type to check
     * @return true if this exception is of the specified type
     */
    public boolean isType(ErrorType type) {
        return this.errorType == type;
    }
}
