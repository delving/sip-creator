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
    ABSENT("No dataset yet"),
    EMPTY("Press here to import data"),
    IMPORTED("Press here to analyze the imported data"),
    ANALYZED_IMPORT("Press here for the analysis frames"),
    DELIMITED("Press here to convert to source format"),
    SOURCED("Press here to analyze the source"),
    ANALYZED_SOURCE("Press here for the mapping frames"),
    MAPPING("Press here to transform/validate"),
    VALIDATED("Press here for upload");

    private String description;

    DataSetState(String description) {
        this.description = description;
    }

    public String toHtml() {
        return String.format("<html><center><strong>&quot;%s&quot;</strong><br><i>%s</i></center>", toString(), description);
    }
}
