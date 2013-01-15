package eu.delving;

import eu.delving.metadata.MappingEngineImpl;
import eu.delving.metadata.MetadataException;
import eu.delving.metadata.RecDefModel;

import java.io.FileNotFoundException;
import java.util.Map;

/**
 * Create mapping engines
 */

public class MappingEngineFactory {

    public static MappingEngine newInstance(
            ClassLoader classLoader,
            Map<String, String> namespaces
    ) throws FileNotFoundException, MetadataException {
        return new MappingEngineImpl(classLoader, namespaces, null, null);
    }

    public static MappingEngine newInstance(
            ClassLoader classLoader,
            Map<String, String> namespaces,
            RecDefModel recDefModel,
            String mapping
    ) throws FileNotFoundException, MetadataException {
        return new MappingEngineImpl(classLoader, namespaces, recDefModel, mapping);
    }
}
