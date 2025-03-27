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

package eu.delving.schema;

/**
 * The different kinds of schemas that you can find in the repository
 *
 *
 */

public enum SchemaType {
    FACT_DEFINITIONS("definition-list.xml"),
    RECORD_DEFINITION("record-definition.xml"),
    VALIDATION_SCHEMA("validation.xsd"),
    VIEW_DEFINITION("view-definition.xml"),
    SHACL_SHAPE("shacl.ttl");

    public final String fileName;

    SchemaType(String fileName) {
        this.fileName = fileName;
    }

    public static SchemaType forFile(String fileName) {
        for (SchemaType type : SchemaType.values()) {
            if (type.fileName.equals(fileName)) {
                return type;
            }
        }
        return null;
    }
}
