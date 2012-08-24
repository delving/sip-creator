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

package eu.delving.schema;

import java.io.IOException;

/**
 * Defining how the text of the schemas are to be fetched
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public interface Fetcher {

    String SCHEMA_DIRECTORY = "/schema-repository.xml";
    String FACT_DEFINITIONS = "/fact-definition-list_1.0.0.xml";

    String fetchList() throws IOException;

    String fetchSchema(SchemaVersion schemaVersion, SchemaType schemaType) throws IOException;

    Boolean isValidating();
}
