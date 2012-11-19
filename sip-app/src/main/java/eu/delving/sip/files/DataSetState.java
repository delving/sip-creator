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

package eu.delving.sip.files;

import eu.delving.sip.base.SwingHelper;

import javax.swing.*;
import java.awt.event.KeyEvent;

/**
 * The different states that a data set can be in.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public enum DataSetState {
    ABSENT("Clear", "No dataset selected", "Clear the screen"),
    NO_DATA("Import", "Dataset has no data yet", "Import data into it"),
    IMPORTED("Analyze Import", "Data has been imported", "Analyze the imported data"),
    ANALYZED_IMPORT("Delimit", "Dataset has been analyzed", "Choose record root and unique element"),
    DELIMITED("Convert", "Delimiters have been set", "Convert the data to source format"),
    SOURCED("Analyze Source","Source data is available", "Analyze the source data"),
    ANALYZED_SOURCE("Map", "Source data has been analyzed", "Building the mapping is now possible"),
    MAPPING("Validate", "There is a mapping", "Transform and validate all of the data"),
    VALIDATED("Upload", "Dataset has been validated", "Initiate upload to CultureHub");

    private static final KeyStroke KEY = SwingHelper.MENU_G;
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
                        "Click here or %s to : %s<br>",
                description[0], description[1], KeyEvent.getKeyModifiersText(KEY.getModifiers()) + KeyEvent.getKeyText(KEY.getKeyCode()), description[2]
        );
    }
}
