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

package eu.delving.sip.desktop.windows;

import eu.delving.sip.desktop.WindowState;
import eu.europeana.sip.model.SipModel;

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
    protected SipModel sipModel;

    public DesktopWindow(SipModel sipModel) {
        super("", true, true, true, true);
        this.sipModel = sipModel;
        setPreferredSize(DEFAULT_SIZE);
        setLayout(new FlowLayout());
    }

    public void setWindowState(WindowState windowState) {
        this.windowState = windowState;
    }

    public WindowState getWindowState() {
        return windowState;
    }

    public void setId(WindowId id) {
        this.id = id;
        setTitle(id.getTitle());
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
