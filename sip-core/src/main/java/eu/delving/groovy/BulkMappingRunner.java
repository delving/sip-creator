package eu.delving.groovy;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.util.Map;
import java.util.Optional;

/**
 * Designed not for user-feedback whether a mapping-script is correct but for re-executing many scripts, very often.
 */
public class BulkMappingRunner implements MappingRunner {

    private static final Logger LOG = LoggerFactory.getLogger(BulkMappingRunner.class);


    @Override
    public Optional<String> transform(final String record, final String scriptCode,
                                      final Map<String, ?> additionalContext) {
        try {
            CompiledScript compiledScript = EngineHolder.getInstance().compile(scriptCode);
            String result = (String) compiledScript.eval(createContext(record, additionalContext));
            return Optional.ofNullable(result);
        } catch (ScriptException e) {
            LOG.error("Unexpected script compilation failure: {}", e);
            throw new IllegalArgumentException(e);
        }
    }

    private Bindings createContext(final String record, final Map<String, ?> additionalContext) {
        final Map<String, Object> world = ImmutableMap.of("input", record, "_facts", additionalContext);
        return new SimpleBindings(ImmutableMap.of("WORLD", world));
    }
}
