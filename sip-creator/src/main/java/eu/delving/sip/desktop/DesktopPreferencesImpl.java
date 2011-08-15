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


import org.apache.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Uses java.util.prefs.Preferences to access local data. When stored data is found, the listener will be
 * notified with the found data.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class DesktopPreferencesImpl implements DesktopPreferences {

    private static final Logger LOG = Logger.getRootLogger();
    private static final String DESKTOP_STATE = "desktopState";
    private static final String CREDENTIALS = "credentials";
    private static final String WORKSPACE = "workspace";
    private Preferences preferences = Preferences.userNodeForPackage(getClass());
    private Set<Credentials> credentials;
    private Workspace workspace;
    private DesktopState desktopState;

    public DesktopPreferencesImpl(Class<?> clazz) {
        preferences = Preferences.userNodeForPackage(clazz);
        // cache the preferences
        refresh();
    }

    private void refresh() {
        credentials = loadCredentials();
        workspace = loadWorkspace();
        desktopState = loadDesktopState();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void saveCredentials(Credentials credentials) {
        LOG.info("Looking for existing credentials");
        Set<Credentials> credentialsList = loadCredentials();
        if (null == credentialsList) {
            LOG.info("There are no credentials found, will create an empty list");
            credentialsList = new HashSet<Credentials>();
        }
        credentialsList.add(credentials);
        try {
            LOG.info("Writing list " + credentialsList);
            writeObject(CREDENTIALS, credentialsList);
            LOG.info("Done.");
        }
        catch (IOException e) {
            LOG.error("Error storing credentials", e);
        }
    }

    @Override
    public Set<Credentials> getCredentials() {
        if (null == credentials) {
            refresh();
        }
        return credentials;
    }

    @SuppressWarnings("unchecked")
    private Set<Credentials> loadCredentials() {
        return (Set<Credentials>) readObject(CREDENTIALS);
    }

    @Override
    public void saveDesktopState(DesktopState desktopState) {
        try {
            writeObject(DESKTOP_STATE, desktopState);
        }
        catch (IOException e) {
            LOG.info("Error storing desktop state", e);
        }
    }

    @Override
    public DesktopState getDesktopState() {
        if (null == desktopState) {
            refresh();
        }
        return desktopState;
    }

    private void writeObject(String key, Object object) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(object);
        LOG.info(String.format("%d bytes written for key %s", byteArrayOutputStream.toByteArray().length, key));
        preferences.putByteArray(key, byteArrayOutputStream.toByteArray());
    }

    private Object readObject(String key) {
        byte[] data = preferences.getByteArray(key, null);
        if (null == data) {
            LOG.error("No data found for key : " + key);
            return null;
        }
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data);
        try {
            ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
            LOG.info(String.format("%s bytes read for key %s", data.length, key));
            return objectInputStream.readObject();
        }
        catch (IOException e) {
            LOG.error(String.format("Error reading data for key '%s'", key), e);
        }
        catch (ClassNotFoundException e) {
            LOG.error(String.format("Error reading data for key '%s'", key), e);
        }
        return null;
    }

    private DesktopState loadDesktopState() {
        return (DesktopState) readObject(DESKTOP_STATE);
    }

    @Override
    public void saveWorkspace(Workspace workspace) {
        try {
            writeObject(WORKSPACE, workspace);
        }
        catch (IOException e) {
            LOG.error("Error writing workspace", e);
        }
    }

    @Override
    public Workspace getWorkspace() {
        if (null == workspace) {
            refresh();
        }
        return workspace;
    }

    private Workspace loadWorkspace() {
        return (Workspace) readObject(WORKSPACE);
    }

    @Override
    public void clear() {
        try {
            preferences.clear();
            LOG.info("Cleared data for node : " + preferences.absolutePath());
        }
        catch (BackingStoreException e) {
            LOG.error("Error clearing preferences", e);
        }
    }
}

