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

import eu.delving.metadata.MappingResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Container for all results and validation outcomes from a mapping execution.
 * Allows collecting all validation issues without throwing exceptions.
 */
public class MappingExecutionResult {
    private final MappingResult mappingResult;
    private final List<String> schemaValidationErrors;
    private final List<String> structureViolations;
    private final List<String> contentViolations;
    private final List<String> rdfViolations;
    private final boolean wasDiscarded;
    private final String discardReason;
    private final String rawOutput;

    private MappingExecutionResult(Builder builder) {
        this.mappingResult = builder.mappingResult;
        this.schemaValidationErrors = Collections.unmodifiableList(new ArrayList<>(builder.schemaValidationErrors));
        this.structureViolations = Collections.unmodifiableList(new ArrayList<>(builder.structureViolations));
        this.contentViolations = Collections.unmodifiableList(new ArrayList<>(builder.contentViolations));
        this.rdfViolations = Collections.unmodifiableList(new ArrayList<>(builder.rdfViolations));
        this.wasDiscarded = builder.wasDiscarded;
        this.discardReason = builder.discardReason;
        this.rawOutput = builder.rawOutput;
    }

    /**
     * @return The successful mapping result, or null if mapping failed
     */
    public MappingResult getMappingResult() {
        return mappingResult;
    }

    /**
     * @return List of XML schema validation errors
     */
    public List<String> getSchemaValidationErrors() {
        return schemaValidationErrors;
    }

    /**
     * @return List of structure validation violations
     */
    public List<String> getStructureViolations() {
        return structureViolations;
    }

    /**
     * @return List of content validation violations
     */
    public List<String> getContentViolations() {
        return contentViolations;
    }

    /**
     * @return List of RDF conversion violations
     */
    public List<String> getRdfViolations() {
        return rdfViolations;
    }

    /**
     * @return true if the record was explicitly discarded
     */
    public boolean isWasDiscarded() {
        return wasDiscarded;
    }

    /**
     * @return The reason for discarding the record, if it was discarded
     */
    public String getDiscardReason() {
        return discardReason;
    }

    /**
     * @return The raw XML/RDF output
     */
    public String getRawOutput() {
        return rawOutput;
    }

    /**
     * @return true if there were any validation errors
     */
    public boolean hasValidationErrors() {
        return !schemaValidationErrors.isEmpty() ||
                !structureViolations.isEmpty() ||
                !contentViolations.isEmpty() ||
                !rdfViolations.isEmpty();
    }

    /**
     * @return A list of all validation errors combined
     */
    public List<String> getAllValidationErrors() {
        List<String> allErrors = new ArrayList<>();
        if (!schemaValidationErrors.isEmpty()) {
            allErrors.add("Schema Validation Errors:");
            allErrors.addAll(schemaValidationErrors);
        }
        if (!structureViolations.isEmpty()) {
            allErrors.add("Structure Violations:");
            allErrors.addAll(structureViolations);
        }
        if (!contentViolations.isEmpty()) {
            allErrors.add("Content Violations:");
            allErrors.addAll(contentViolations);
        }
        if (!rdfViolations.isEmpty()) {
            allErrors.add("RDF Violations:");
            allErrors.addAll(rdfViolations);
        }
        return allErrors;
    }

    public static class Builder {
        private MappingResult mappingResult;
        private final List<String> schemaValidationErrors = new ArrayList<>();
        private final List<String> structureViolations = new ArrayList<>();
        private final List<String> contentViolations = new ArrayList<>();
        private final List<String> rdfViolations = new ArrayList<>();
        private boolean wasDiscarded;
        private String discardReason;
        private String rawOutput;

        public Builder withMappingResult(MappingResult result) {
            this.mappingResult = result;
            return this;
        }

        public Builder addSchemaValidationError(String error) {
            this.schemaValidationErrors.add(error);
            return this;
        }

        public Builder addStructureViolation(String violation) {
            this.structureViolations.add(violation);
            return this;
        }

        public Builder addContentViolation(String violation) {
            this.contentViolations.add(violation);
            return this;
        }

        public Builder addRdfViolation(String violation) {
            this.rdfViolations.add(violation);
            return this;
        }

        public Builder withDiscarded(boolean discarded, String reason) {
            this.wasDiscarded = discarded;
            this.discardReason = reason;
            return this;
        }

        public Builder withRawOutput(String output) {
            this.rawOutput = output;
            return this;
        }

        public MappingExecutionResult build() {
            return new MappingExecutionResult(this);
        }
    }
}
