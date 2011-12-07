/*
 * Copyright 2011 DELVING BV
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

package eu.delving.metadata;

import java.util.List;
import java.util.Set;

/**
 * This interface describes objects which are able to deliver all of the information
 * about the target record definitions.
 *
 * It reveals which prefixes are available
 * as target formats, and has a factory method for building new instances of these
 * composite hierarchical data structures for usage in the user interface
 * and for mapping.
 *
 * It also gives access to the read-only facts that apply to all mappings in
 * a dataset.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public interface RecDefModel {

    List<FactDefinition> getFactDefinitions();

    Set<String> getPrefixes() throws MetadataException;

    RecDefTree createRecDef(String prefix) throws MetadataException;
}
