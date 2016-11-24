package eu.delving.groovy;

import eu.delving.metadata.RecMapping;
import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.jsr223.GroovyScriptEngineImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

import javax.script.CompiledScript;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
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
    public Node runMapping(MetadataRecord metadataRecord) throws MappingException {
        LOG.debug("Running mapping");
        SimpleBindings bindings = new SimpleBindings();
        final ScriptBinding ourScriptIO = new ScriptBinding();
        bindings.put("WORLD", ourScriptIO);

        ourScriptIO._facts = initFactsNode(recMapping.getFacts());
        ourScriptIO._optLookup = recMapping.getRecDefTree().getRecDef().valueOptLookup;
        ourScriptIO.output = DOMBuilder.createFor(recMapping.getRecDefTree().getRecDef());
        ourScriptIO.input = Collections.singletonList(metadataRecord.getRootNode());

        try {
            Node result = (Node) compiledScript.eval(bindings);
            // clone the result to avoid hanging on to the object and therefore it's classloader
            // Avoids permgen / metaspace issues
            // TODO hans is this required?
            return stripEmptyElements(result.cloneNode(true));
        } catch (ScriptException e) {
            throw new RuntimeException(e);
        }

    }



}
