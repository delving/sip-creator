package eu.delving.groovy;

import eu.delving.metadata.RecDefTree;
import org.w3c.dom.Node;

/**
 * Base interface for all mapping implementations.
 * Provides methods for accessing mapping configuration and executing mappings.
 */
public interface MappingRunner {
    /**
     * Gets the RecDefTree associated with this mapping.
     *
     * @return The RecDefTree configuration
     */
    RecDefTree getRecDefTree();

    /**
     * Gets the generated mapping code.
     *
     * @return The Groovy code used for mapping
     */
    String getCode();

    /**
     * Executes the mapping on a single record.
     *
     * @param metadataRecord The record to map
     * @return The mapped Node result
     * @throws MappingException if any error occurs during mapping
     */
    Node runMapping(MetadataRecord metadataRecord) throws MappingException;
}
