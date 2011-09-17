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

package eu.delving.sip.files;

/**
 * The different states that a data set can be in.
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public enum DataSetState {
    EMPTY("No imported or source data"),
    IMPORTED("Import file to be analyzed"),
    ANALYZED_IMPORT("Imported file analyzed"),
    DELIMITED("Record root and unique element set"),
    SOURCED("Source data available"),
    ANALYZED_SOURCE("Source data analyzed"),
    MAPPING("Mapping created - not validated"),
    VALIDATED("Mapping validated - ready for upload"),
    PHANTOM("Nonexistent");

    private String description;
    DataSetState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public String toHtml() {
        return String.format("<html><strong>&quot;%s&quot;</strong><br><font size=-1><i>%s</i></font>", toString(), description);
    }
}
