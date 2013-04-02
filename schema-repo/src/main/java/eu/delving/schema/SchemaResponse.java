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

/**
 * What you get back when you request a schema.  Tells if it has been validated with a hash (determines
 * whether you should cache what comes in.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class SchemaResponse {
    private String schemaText;
    private boolean isValidated;

    public SchemaResponse(String schemaText, boolean isValidated) {
        this.schemaText = schemaText;
        this.isValidated = isValidated;
    }

    public String getSchemaText() {
        return schemaText;
    }

    public boolean isValidated() {
        return isValidated;
    }
}
