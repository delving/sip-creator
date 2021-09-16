package eu.delving.groovy;

import eu.delving.metadata.RecMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

import javax.script.CompiledScript;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.util.Collections;

/**
 * Designed not for user-feedback but for re-executing many scripts, very often.
 */
public class BulkMappingRunner extends AbstractMappingRunner {

    private static final Logger LOG = LoggerFactory.getLogger(BulkMappingRunner.class);
    private CompiledScript compiledScript;

    /**
     * @param recMapping represents to mapping to be applied
     * @param generatedCode the code to be executed against each record
     */
    public BulkMappingRunner(final RecMapping recMapping, final String generatedCode) {
        super(recMapping, generatedCode);
        try {
            this.compiledScript = EngineHolder.getInstance().compile(generatedCode);
        } catch (ScriptException e) {
            // we don't expect non-compiling scripts in the bulk runner.
            LOG.error("Error compiling script: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public Node runMapping(final MetadataRecord metadataRecord) {
        LOG.trace("Running mapping for record {}", metadataRecord);
        SimpleBindings bindings = Utils.bindingsFor(recMapping.getFacts(),
            recMapping.getRecDefTree().getRecDef(), metadataRecord.getRootNode(),
            recMapping.getRecDefTree().getRecDef().valueOptLookup);
        try {
            Object result = compiledScript.eval(bindings);
            return Utils.stripEmptyElements(result);
        } catch (ScriptException e) {
            if (e.getCause() instanceof DiscardRecordException) {
                throw (DiscardRecordException) e.getCause();
            }
            throw new RuntimeException(e);
        }
    }

}
