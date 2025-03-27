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

import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

import javax.script.CompiledScript;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

/**
 * Optimized implementation for high-throughput batch processing of mappings.
 * Pre-compiles the script for efficiency and provides minimal overhead.
 */
public class BulkMappingRunner implements MappingRunner {
    private static final Logger LOG = LoggerFactory.getLogger(BulkMappingRunner.class);

    private final CompiledScript compiledScript;
    private final RecMapping recMapping;
    private final String generatedCode;

    /**
     * Creates a new BulkMappingRunner with pre-compiled script for efficiency.
     *
     * @param recMapping    The mapping configuration to be applied
     * @param generatedCode The Groovy code to be executed against each record
     * @throws MappingException if script compilation fails
     */
    public BulkMappingRunner(RecMapping recMapping, String generatedCode) throws MappingException {
        this.recMapping = recMapping;
        this.generatedCode = generatedCode;

        try {
            this.compiledScript = EngineHolder.getInstance().compile(generatedCode);
        } catch (ScriptException e) {
            LOG.error("Failed to compile mapping script: {}", e.getMessage());
            throw new MappingException(
                    MappingException.ErrorType.COMPILATION,
                    "Failed to compile mapping script",
                    e);
        }
    }

    @Override
    public RecDefTree getRecDefTree() {
        return recMapping.getRecDefTree();
    }

    @Override
    public String getCode() {
        return generatedCode;
    }

    @Override
    public Node runMapping(MetadataRecord record) throws MappingException {
        LOG.trace("Running mapping for record {}", record);

        try {
            SimpleBindings bindings = Utils.bindingsFor(
                    recMapping.getFacts(),
                    recMapping.getRecDefTree().getRecDef(),
                    record.getRootNode(),
                    recMapping.getRecDefTree().getRecDef().valueOptLookup);

            Object result = compiledScript.eval(bindings);
            return Utils.stripEmptyElements(result);

        } catch (ScriptException e) {
            // Special handling for explicitly discarded records
            if (e.getCause() instanceof DiscardRecordException) {
                throw (DiscardRecordException) e.getCause();
            }

            // Handle other execution errors
            String errorMessage = String.format(
                    "Failed to process record (id=%s, number=%s)",
                    record.getId(),
                    record.getRecordNumber());

            LOG.error(errorMessage, e);
            throw new MappingException(
                    MappingException.ErrorType.EXECUTION,
                    errorMessage,
                    e);
        } catch (RuntimeException e) {
            // Handle unexpected runtime errors
            String errorMessage = String.format(
                    "Unexpected error processing record (id=%s, number=%s)",
                    record.getId(),
                    record.getRecordNumber());

            LOG.error(errorMessage, e);
            throw new MappingException(
                    MappingException.ErrorType.EXECUTION,
                    errorMessage,
                    e);
        }
    }

    /**
     * Gets the underlying RecMapping configuration.
     * 
     * @return The RecMapping being used by this runner
     */
    public RecMapping getRecMapping() {
        return recMapping;
    }
}
