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

package eu.europeana.sip.desktop;

/**
 * Load the local preferences for the SIP-Creator.
 *
 * The following properties will be loaded:
 *
 * <ul>
 * <li>Credentials</li>
 * <li>Window states</li>
 * </ul>
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public interface SipCreatorPreferences {

    interface Listener {

        void windowStateFound(WindowState windowState);

        void credentialsFound(Credentials credentials);
    }

    interface Credentials {

        String getUsername();

        String getPassword();
    }

    interface WindowState {
        // todo: define states to remember.
    }

    /**
     * Store a hashed password.
     *
     * @param credentials The credentials to remember.
     */
    void saveCredentials(Credentials credentials);

    /**
     * Remember the window state.
     *
     * @param windowState The last window state.
     */
    void saveWindowState(WindowState windowState);
}
