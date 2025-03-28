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

package eu.delving.metadata;

import eu.delving.schema.SchemaVersion;

/**
 * This interface describes objects which are able to deliver all of the information
 * about the target record definitions.  It creates a fresh RecDefTree every time
 * the create method is called, since the tree is used as a prototype to put
 * rec mappings into.
 *
 *
 */

public interface RecDefModel {

    RecDefTree createRecDefTree(SchemaVersion schemaVersion) throws MetadataException;
}
