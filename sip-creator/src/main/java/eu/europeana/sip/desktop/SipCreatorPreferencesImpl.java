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


import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.util.prefs.Preferences;

/**
 * Use Java Preferences to load local data. When stored data is found, the listener will be
 * notified with the found data.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class SipCreatorPreferencesImpl implements SipCreatorPreferences {

    private static final Logger LOG = Logger.getRootLogger();
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";
    private Preferences preferences = Preferences.userNodeForPackage(getClass());
    private Listener listener;

    public SipCreatorPreferencesImpl(Listener listener) {
        this.listener = listener;
        loadCredentials();
        loadWindowState();
    }

    private void loadWindowState() {

    }

    private void loadCredentials() {
        LOG.info("Looking for stored credentials.");
        final String username = preferences.get(USERNAME, null);
        final String password = preferences.get(PASSWORD, null);
        if (!StringUtils.isEmpty(username) && !StringUtils.isEmpty(password)) {
            LOG.info("Found credentials, will notify the listener.");
            listener.credentialsFound(
                    new Credentials() {

                        @Override
                        public String getUsername() {
                            return username;
                        }

                        @Override
                        public String getPassword() {
                            return password;
                        }
                    }
            );
            return;
        }
        LOG.info("No credentials found.");
    }

    @Override
    public void saveCredentials(Credentials credentials) {
        preferences.put(USERNAME, credentials.getUsername());
        preferences.put(PASSWORD, credentials.getPassword());
    }

    @Override
    public void saveWindowState(WindowState windowState) {
        // todo: add body and return void;
    }
}

