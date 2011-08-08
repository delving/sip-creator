/*
 * Copyright 2010 DELVING BV
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

package eu.europeana.sip.desktop.windows;

import eu.europeana.sip.desktop.WindowState;

import javax.swing.*;
import java.awt.*;

/**
 * The base of all windows within the SIP-Creator.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public abstract class DesktopWindow extends JInternalFrame {

    public static final Dimension DEFAULT_SIZE = new Dimension(600, 350);
    private WindowState windowState;
    private WindowId id;
    private boolean preferencesTransient;

    public enum WindowId {

        ANALYZE("Analyze", true),
        DATA_SET("Open data set", true),
        WELCOME("Welcome", true),
        AUTHENTICATION("Authentication", false),
        MAPPING("Mapping", true),
        PREVIEW("Preview", true),
        UPLOAD("Upload", true),
        NORMALIZE("Normalize", true);

        private String title;
        private boolean draggable;

        private WindowId(String title, @Deprecated boolean draggable) {
            this.title = title;
            this.draggable = draggable;
        }

        public String getTitle() {
            return title;
        }
    }

    public void setWindowState(WindowState windowState) {
        this.windowState = windowState;
    }

    public WindowState getWindowState() {
        return windowState;
    }

    public DesktopWindow(WindowId id) {
        super(id.title, id.draggable, true, true, true);
        this.id = id;
        setPreferredSize(DEFAULT_SIZE);
        setLayout(new FlowLayout());
    }

    public WindowId getId() {
        return id;
    }

    public void setPreferencesTransient(boolean preferencesTransient) {
        this.preferencesTransient = preferencesTransient;
    }

    public boolean isPreferencesTransient() {
        return preferencesTransient;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DesktopWindow that = (DesktopWindow) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "SipCreatorWindow{" +
                "id=" + id +
                '}';
    }
}
