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

package eu.delving.sip.desktop;

import java.io.Serializable;
import java.util.List;

/**
 * Load the preferences for the desktop.
 *
 * The following properties will be loaded:
 *
 * <ul>
 * <li>Credentials - Username/password and server</li>
 * <li>Window states - The last states of the windows</li>
 * <li>Data sets - The last data set</li>
 * </ul>
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public interface DesktopPreferences extends Serializable {

    interface Credentials extends Serializable {

        String getUsername();

        String getPassword();

        String serverAddress();

        int serverPort();
    }

    interface DesktopState extends Serializable {

        String getSpec();

        List<WindowState> getWindowStates();
    }

    /**
     * Store a hashed password.
     *
     * @param credentials The credentials to remember.
     */
    void saveCredentials(Credentials credentials);

    Credentials loadCredentials();

    /**
     * Remember the window state.
     *
     * @param desktopState The last window state.
     */
    void saveDesktopState(DesktopState desktopState);

    DesktopState loadDesktopState();

    void clear();
}
