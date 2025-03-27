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

package eu.delving.sip.files;

/**
 * The different states that a data set can be in.
 *
 *
 */

public enum DataSetState {
    ABSENT("Clear", "No dataset selected", "Clear the screen"),
    SOURCED("Analyze Source","Source data is available", "Analyze the source data"),
    ANALYZED_SOURCE("Map", "Source data has been analyzed", "Building the mapping is now possible"),
    MAPPING("Validate", "There is a mapping", "Transform and validate all of the data"),
    PROCESSED("Generate SIP", "Dataset has been processed", "Create a SIP-Zip file for Narthex upload");

    private String[] description;

    DataSetState(String... description) {
        this.description = description;
    }

    public boolean atLeast(DataSetState state) {
        return ordinal() >= state.ordinal();
    }

    public String toHtml() {
        return String.format(
                "<html><center><b>&quot;%s&quot;</b><br>" +
                        "Status is now : %s<br>" +
                        "Click here to : %s<br>",
                description[0], description[1], description[2]
        );
    }
}
