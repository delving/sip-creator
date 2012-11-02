/*
 * Copyright 2011, 2012 Delving BV
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving;

import eu.delving.metadata.MetadataException;
import eu.delving.schema.SchemaVersion;

/**
 * Provide a mapping service to take text XML.
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public interface MappingEngine {

    /**
     * Add a mapping runner for a given schemaVersion
     *
     * @param schemaVersion what this runner will run
     * @param mapping the XML string representing the mapping
     */

    void addMappingRunner(SchemaVersion schemaVersion, String mapping) throws MetadataException;

    /**
     * Initiate the asynchronous mapping of a record
     *
     * @param index a number to be returned in the call to completion
     * @param recordId the unique identifier of the record
     * @param recordXML the XML of the record
     * @param schemaVersions which mappings are to take place
     * @param completion the callback when the job is done
     */

    void mapRecord(
            int index,
            String recordId,
            String recordXML,
            SchemaVersion[] schemaVersions,
            MappingCompletion completion
    );
}
