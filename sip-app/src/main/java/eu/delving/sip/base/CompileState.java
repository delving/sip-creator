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

package eu.delving.sip.base;

import javax.swing.*;
import java.awt.*;

/**
 * The different phases in the live-compiling of code, reflected in the colors of
 * the code editing area.
 *
 *
 */

public enum CompileState {
    ORIGINAL(new Color(240, 240, 255)),
    SAVED(Color.WHITE),
    EDITED(new Color(230, 255, 230)),
    ERROR(new Color(255, 220, 220));

    private Color color;

    private CompileState(Color color) {
        this.color = color;
    }

    public void setBackgroundOf(JComponent component) {
        component.setBackground(color);
    }

    public void setBackgroundOf(JComponent component, String themeMode) {
        if ("dark".equals(themeMode)) {
            component.setBackground(new Color(255 - color.getRed(), 255 - color.getGreen(), 255 - color.getBlue()));
        } else {
            component.setBackground(color);
        }
    }
}
