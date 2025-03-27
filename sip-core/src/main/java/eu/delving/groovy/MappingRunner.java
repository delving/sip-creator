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
