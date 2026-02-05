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
 * Exception thrown when an invalid xml:lang attribute value is detected.
 *
 * This exception is used to provide clear, user-friendly error messages when:
 * - xml:lang contains invalid BCP 47 language tags (e.g., text content instead of "en", "nl")
 * - xml:lang is set but the element has no content
 */
public class LanguageTagException extends RuntimeException {

    private final String elementName;
    private final String invalidValue;
    private final Type type;

    public enum Type {
        INVALID_FORMAT("Invalid language tag format"),
        EMPTY_CONTENT("Empty content with language tag");

        private final String description;

        Type(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public LanguageTagException(Type type, String elementName, String invalidValue, String message) {
        super(message);
        this.type = type;
        this.elementName = elementName;
        this.invalidValue = invalidValue;
    }

    public String getElementName() {
        return elementName;
    }

    public String getInvalidValue() {
        return invalidValue;
    }

    public Type getType() {
        return type;
    }

    /**
     * Returns a user-friendly error message with guidance on how to fix the issue.
     */
    public String getUserFriendlyMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("Language Tag Error in element '").append(elementName).append("'\n\n");

        switch (type) {
            case INVALID_FORMAT:
                sb.append("Problem: The xml:lang attribute contains an invalid value.\n\n");
                sb.append("Found: ").append(truncateForDisplay(invalidValue)).append("\n\n");
                sb.append("Expected: A valid BCP 47 language tag such as:\n");
                sb.append("  - 'en' (English)\n");
                sb.append("  - 'nl' (Dutch)\n");
                sb.append("  - 'de' (German)\n");
                sb.append("  - 'en-US' (American English)\n");
                sb.append("  - 'zh-Hans' (Simplified Chinese)\n\n");
                sb.append("How to fix: Check your mapping - the text content may have been\n");
                sb.append("accidentally mapped to the xml:lang attribute instead of the\n");
                sb.append("element's text content.");
                break;

            case EMPTY_CONTENT:
                sb.append("Problem: Element has xml:lang='").append(invalidValue);
                sb.append("' but no text content.\n\n");
                sb.append("A language tag only makes sense when there is text to describe.\n\n");
                sb.append("How to fix: Either:\n");
                sb.append("  - Add content to the element, or\n");
                sb.append("  - Remove the xml:lang attribute from the mapping");
                break;
        }

        return sb.toString();
    }

    private String truncateForDisplay(String value) {
        if (value == null) return "null";
        if (value.length() <= 60) return "'" + value + "'";
        return "'" + value.substring(0, 57) + "...'";
    }
}
