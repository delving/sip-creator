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

import eu.delving.metadata.SystemField;
import org.w3c.dom.Node;

import java.util.List;
import java.util.Map;

/**
 * This is how mapping results are given back from the MappingEngine
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public interface MappingResult {

    Node root();

    Node rootAugmented();

    Map<String, List<String>> fields();

    Map<SystemField, List<String>> systemFields();

    Map<String, List<String>> searchFields();

    void checkMissingFields() throws MissingFieldsException;

    String toXml();

    String toXmlAugmented();

    public static class MissingFieldsException extends Exception {
        public MissingFieldsException(String message) {
            super(message);
        }
    }
}
