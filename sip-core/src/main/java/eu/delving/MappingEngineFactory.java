package eu.delving;

import eu.delving.metadata.MappingEngineImpl;
import eu.delving.metadata.RecDefModel;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Create mapping engines with one of these
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MappingEngineFactory {
    private Executor executor;
    private ClassLoader classLoader;
    private RecDefModel recDefModel;

    /**
     * Create a factory for MappingEngine instances.
     * <p/>
     * try Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
     *
     * @param classLoader who loads the groovy classes
     * @param executor    who executes the async jobs
     */

    public MappingEngineFactory(ClassLoader classLoader, Executor executor, RecDefModel recDefModel) {
        this.classLoader = classLoader;
        this.executor = executor;
        this.recDefModel = recDefModel;
    }

    public MappingEngine createEngine(Map<String, String> namespaces) {
        return new MappingEngineImpl(classLoader, executor, recDefModel, namespaces);
    }
}
