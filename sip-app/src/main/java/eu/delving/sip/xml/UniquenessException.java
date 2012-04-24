/*
 * Copyright 2011 DELVING BV
 *
 * Licensed under the EUPL, Version 1.0 or as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the
 * Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.delving.sip.xml;

import eu.delving.metadata.Path;

/**
 * Thrown when a field is marked as unique, but isn't.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */

public class UniquenessException extends Exception {

    private Path uniqueElementPath;
    private int recordNumber;

    public UniquenessException(Path uniqueElementPath, int recordNumber) {
        super(String.format("Element is not unique: %s (#%d)", uniqueElementPath, recordNumber));
        this.uniqueElementPath = uniqueElementPath;
        this.recordNumber = recordNumber;
    }

    public Path getUniqueElementPath() {
        return uniqueElementPath;
    }

    public int getRecordNumber() {
        return recordNumber;
    }
}
