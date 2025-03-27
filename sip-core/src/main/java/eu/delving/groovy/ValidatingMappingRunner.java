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
import eu.delving.metadata.JenaHelper;
import eu.delving.metadata.MappingResult;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.StructureTest;
import org.apache.jena.riot.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPathException;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactoryConfigurationException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A MappingRunner implementation that wraps another runner and adds validation.
 * Performs XML Schema, structure, content, and RDF validation on the mapping
 * output.
 */
public class ValidatingMappingRunner implements MappingRunner {
    private static final Logger LOG = LoggerFactory.getLogger(ValidatingMappingRunner.class);

    private final MappingRunner delegate;
    private final Validator validator;
    private final List<AssertionTest> assertions;
    private final RDFFormat rdfFormat;
    private final XmlSerializer serializer;

    public ValidatingMappingRunner(
            MappingRunner delegate,
            Validator validator,
            List<AssertionTest> assertions,
            RDFFormat rdfFormat) {
        this.delegate = delegate;
        this.validator = validator;
        this.assertions = assertions;
        this.rdfFormat = rdfFormat;
        this.serializer = new XmlSerializer();
    }

    @Override
    public RecDefTree getRecDefTree() {
        return delegate.getRecDefTree();
    }

    @Override
    public String getCode() {
        return delegate.getCode();
    }

    @Override
    public Node runMapping(MetadataRecord record) throws MappingException {
        try {
            // First run the base mapping
            Node node = delegate.runMapping(record);
            if (node == null) {
                throw new MappingException(
                        MappingException.ErrorType.EXECUTION,
                        "Mapping produced null result");
            }

            // Validate the output
            validateOutput(node, record);

            return node;

        } catch (DiscardRecordException e) {
            // Re-throw DiscardRecordException as is
            throw e;
        } catch (MappingException e) {
            // Re-throw MappingExceptions with their original error type
            throw e;
        } catch (Exception e) {
            // Catch any other unexpected exceptions
            LOG.error("Unexpected error during mapping execution", e);
            throw new MappingException(
                    MappingException.ErrorType.EXECUTION,
                    "Unexpected error during mapping: " + e.getMessage(),
                    e);
        }
    }

    private void validateOutput(Node node, MetadataRecord record) throws MappingException {
        try {
            // XML Schema validation
            if (validator != null) {
                validateSchema(node);
            }

            // Create mapping result for further validation
            MappingResult result = createMappingResult(node, record);

            // Validate URIs
            validateURIs(result);

            // Check structure
            validateStructure(node);

            // Check assertions
            if (assertions != null) {
                validateAssertions(node);
            }

            // Validate RDF conversion
            validateRDF(result);

        } catch (MappingException e) {
            // Re-throw MappingExceptions with their original error type
            throw e;
        } catch (Exception e) {
            // Handle any unexpected exceptions during validation
            LOG.error("Unexpected error during validation", e);
            throw new MappingException(
                    MappingException.ErrorType.VALIDATION,
                    "Unexpected validation error: " + e.getMessage(),
                    e);
        }
    }

    private MappingResult createMappingResult(Node node, MetadataRecord record) throws MappingException {
        try {
            return new MappingResult(
                    serializer,
                    record.getId(),
                    node,
                    getRecDefTree());
        } catch (Exception e) {
            throw new MappingException(
                    MappingException.ErrorType.EXECUTION,
                    "Failed to create mapping result: " + e.getMessage(),
                    e);
        }
    }

    private void validateSchema(Node node) throws MappingException {
        ForgivingErrorHandler handler = new ForgivingErrorHandler();
        validator.setErrorHandler(handler);

        try {
            validator.validate(new DOMSource(node));
            handler.checkErrors();
        } catch (SAXException e) {
            throw new MappingException(
                    MappingException.ErrorType.VALIDATION,
                    "Schema validation failed:\n" + handler.getErrorMessages(),
                    e);
        } catch (IOException e) {
            throw new MappingException(
                    MappingException.ErrorType.VALIDATION,
                    "IO error during schema validation: " + e.getMessage(),
                    e);
        } catch (Exception e) {
            throw new MappingException(
                    MappingException.ErrorType.VALIDATION,
                    "Unexpected error during schema validation: " + e.getMessage(),
                    e);
        }
    }

    private void validateURIs(MappingResult result) throws MappingException {
        try {
            List<String> uriErrors = result.getUriErrors();
            if (!uriErrors.isEmpty()) {
                throw new MappingException(
                        MappingException.ErrorType.CONTENT,
                        "URI validation errors:\n" + String.join("\n", uriErrors));
            }
        } catch (XPathExpressionException e) {
            throw new MappingException(
                    MappingException.ErrorType.CONTENT,
                    "Error evaluating URIs: " + e.getMessage(),
                    e);
        } catch (Exception e) {
            throw new MappingException(
                    MappingException.ErrorType.CONTENT,
                    "Unexpected error during URI validation: " + e.getMessage(),
                    e);
        }
    }

    private void validateStructure(Node node) throws MappingException {
        try {
            List<String> violations = new ArrayList<>();
            for (StructureTest test : StructureTest.listFrom(getRecDefTree().getRecDef())) {
                StructureTest.Violation violation = test.getViolation(node);
                switch (violation) {
                    case REQUIRED:
                        violations.add("Required piece was missing: " + test);
                        break;
                    case SINGULAR:
                        violations.add("Too many sub-elements: " + test);
                        break;
                }
            }

            if (!violations.isEmpty()) {
                throw new MappingException(
                        MappingException.ErrorType.STRUCTURE,
                        "Structure violations:\n" + String.join("\n", violations));
            }
        } catch (XPathFactoryConfigurationException | XPathExpressionException e) {
            throw new MappingException(
                    MappingException.ErrorType.STRUCTURE,
                    "Error in structure validation XPath: " + e.getMessage(),
                    e);
        } catch (Exception e) {
            throw new MappingException(
                    MappingException.ErrorType.STRUCTURE,
                    "Unexpected error during structure validation: " + e.getMessage(),
                    e);
        }
    }

    private void validateAssertions(Node node) throws MappingException {
        try {
            List<String> violations = new ArrayList<>();
            for (AssertionTest test : assertions) {
                String violation = test.getViolation(node);
                if (violation != null) {
                    violations.add(test + " : " + violation);
                }
            }

            if (!violations.isEmpty()) {
                throw new MappingException(
                        MappingException.ErrorType.CONTENT,
                        "Assertion violations:\n" + String.join("\n", violations));
            }
        } catch (XPathException e) {
            throw new MappingException(
                    MappingException.ErrorType.CONTENT,
                    "Error in assertion XPath: " + e.getMessage(),
                    e);
        } catch (Exception e) {
            throw new MappingException(
                    MappingException.ErrorType.CONTENT,
                    "Unexpected error during assertion validation: " + e.getMessage(),
                    e);
        }
    }

    private void validateRDF(MappingResult result) throws MappingException {
        try {
            String rdfOutput = result.toXml();

            // Check RDF errors from the mapping result first
            List<String> rdfErrors = result.getRDFErrors();
            if (!rdfErrors.isEmpty()) {
                throw new MappingException(
                        MappingException.ErrorType.RDF,
                        "RDF validation errors:\n" + String.join("\n", rdfErrors));
            }

            // Try the RDF conversion
            JenaHelper.convertRDF("", rdfOutput, rdfFormat);

        } catch (MappingException e) {
            // Re-throw existing MappingExceptions
            throw e;
        } catch (Exception e) {
            ByteArrayOutputStream errorBuffer = new ByteArrayOutputStream();
            e.printStackTrace(new PrintWriter(errorBuffer, true));
            String errorTrace = new String(errorBuffer.toByteArray(), StandardCharsets.UTF_8);

            throw new MappingException(
                    MappingException.ErrorType.RDF,
                    "RDF conversion failed:\n" + errorTrace,
                    e);
        }
    }

    /**
     * Error handler that collects all validation errors.
     */
    private static class ForgivingErrorHandler implements ErrorHandler {
        private final List<String> errors = new ArrayList<>();

        @Override
        public void warning(SAXParseException e) {
            errors.add("Warning: " + e.getMessage());
        }

        @Override
        public void error(SAXParseException e) {
            errors.add("Error: " + e.getMessage());
        }

        @Override
        public void fatalError(SAXParseException e) {
            errors.add("Fatal: " + e.getMessage());
        }

        public String getErrorMessages() {
            return String.join("\n", errors);
        }

        public void checkErrors() throws SAXException {
            if (!errors.isEmpty()) {
                throw new SAXException(getErrorMessages());
            }
        }
    }
}
