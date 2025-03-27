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

import eu.delving.metadata.AssertionTest;
import eu.delving.metadata.MappingResult;
import eu.delving.metadata.RecMapping;
import org.apache.jena.riot.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

import javax.xml.validation.Validator;
import java.util.List;
import java.util.Map;

/**
 * Utility class for executing mappings and collecting all validation results.
 * Provides a non-throwing API that returns all validation results in a single
 * object.
 */
public class MappingExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(MappingExecutor.class);

    private final MappingRunner baseRunner;
    private final ValidatingMappingRunner validatingRunner;
    private final Map<String, String> facts;

    /**
     * Creates a new MappingExecutor with the given configuration.
     *
     * @param recMapping    The mapping to execute
     * @param generatedCode The generated mapping code
     * @param validator     XML Schema validator (optional)
     * @param assertions    List of assertions to check (optional)
     * @param rdfFormat     Desired RDF output format
     * @param orgId         Organization ID for output
     * @param spec          Specification ID for output
     * @throws MappingException if the mapping code fails to compile
     */
    public MappingExecutor(
            RecMapping recMapping,
            String generatedCode,
            Validator validator,
            List<AssertionTest> assertions,
            RDFFormat rdfFormat) throws MappingException {
        this.baseRunner = new BulkMappingRunner(recMapping, generatedCode);
        this.validatingRunner = new ValidatingMappingRunner(
                baseRunner,
                validator,
                assertions,
                rdfFormat);
        this.facts = recMapping.getFacts();
    }

    /**
     * Executes the mapping on a record and returns all results and validation
     * outcomes.
     *
     * @param record The record to map
     * @return Result object containing mapping output and any validation errors
     */
    public MappingExecutionResult execute(MetadataRecord record) {
        MappingExecutionResult.Builder builder = new MappingExecutionResult.Builder();

        try {
            // Run the mapping with validation
            Node node = validatingRunner.runMapping(record);

            // Create the mapping result
            MappingResult result = new MappingResult(
                    new XmlSerializer(),
                    record.getId(),
                    node,
                    baseRunner.getRecDefTree());

            // Generate the final output
            String output = result.toXml(facts);

            // Set successful results
            builder.withMappingResult(result)
                    .withRawOutput(output);

        } catch (DiscardRecordException e) {
            builder.withDiscarded(true, e.getMessage());

        } catch (MappingException e) {
            // Categorize errors based on type
            switch (e.getErrorType()) {
                case VALIDATION:
                    builder.addSchemaValidationError(e.getMessage());
                    break;
                case STRUCTURE:
                    builder.addStructureViolation(e.getMessage());
                    break;
                case CONTENT:
                    builder.addContentViolation(e.getMessage());
                    break;
                case RDF:
                    builder.addRdfViolation(e.getMessage());
                    break;
                case COMPILATION:
                case EXECUTION:
                default:
                    // Unexpected execution errors
                    builder.addSchemaValidationError(
                            "Unexpected error during mapping execution: " + e.getMessage());
                    break;
            }
        } catch (Exception e) {
            // Handle any unexpected errors
            LOG.error("Unexpected error during mapping execution", e);
            builder.addSchemaValidationError(
                    "Unexpected error: " + e.getMessage());
        }

        return builder.build();
    }

    /**
     * Executes the mapping on a record with custom XML validation.
     * This version allows passing custom validation options.
     *
     * @param record              The record to map
     * @param enableXmlValidation Whether to perform XML validation
     * @return Result object containing mapping output and any validation errors
     */
    public MappingExecutionResult execute(MetadataRecord record, boolean enableXmlValidation) {
        // TODO: Implement custom validation logic if needed
        return execute(record);
    }

    /**
     * Gets the underlying mapping runner.
     * Useful for accessing mapping configuration or code.
     *
     * @return The base mapping runner
     */
    public MappingRunner getMappingRunner() {
        return baseRunner;
    }
}
