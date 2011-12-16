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

import org.apache.commons.lang.WordUtils;

/**
 * The different states that a data set can be in.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public enum DataSetState {
    ABSENT("There is currently no dataset selected", "Press here to clear the screen"),
    EMPTY("This dataset is currently empty", "Press here to import data into it"),
    IMPORTED("There is data imported for this dataset", "Press here to analyze the imported data"),
    ANALYZED_IMPORT("The dataset is analyzed", "Press here to show analysis frames"),
    DELIMITED("The delimiters have been set", "Press here to convert the data to source format"),
    SOURCED("The source data is available", "Press here to analyze the source"),
    ANALYZED_SOURCE("The source data has been analyzed", "Press here to show mapping frames"),
    MAPPING("There is a mapping present", "Press here to try and transform and validate the data"),
    VALIDATED("This dataset has been validated", "Press here to initiate upload to Culture Hub");

    private String [] description;

    DataSetState(String... description) {
        this.description = description;
    }
    
    public String toTitle() {
        return WordUtils.capitalize(toString().replaceAll("_", " ").toLowerCase());
    }

    public String toToolTip() {
        StringBuilder out = new StringBuilder("<html><strong>&quot;");
        out.append(toTitle());
        out.append("&quot;</strong><br>");
        for (String line : description) {
            out.append("<p>").append(line).append("</p>");
        }
        return out.toString();
    }
}
