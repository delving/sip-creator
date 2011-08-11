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
    private Preferences preferences = Preferences.userNodeForPackage(getClass());
    private Class<?> clazz; // todo: remove, it's only used for showing the path while debugging

    public DesktopPreferencesImpl(Class<?> clazz) {
        this.clazz = clazz;
        preferences = Preferences.userNodeForPackage(clazz);
    }

    @Override
    public void saveCredentials(Credentials credentials) {
        try {
            writeObject(CREDENTIALS, credentials);
        }
        catch (IOException e) {
            LOG.error("Error storing credentials", e);
        }
    }

    @Override
    public Credentials loadCredentials() {
        try {
            return (Credentials) readObject(CREDENTIALS);
        }
        catch (IOException e) {
            LOG.error("Error loading credentials", e);
        }
        catch (ClassNotFoundException e) {
            LOG.error("Error loading credentials", e);
        }
        return null;
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

    private void writeObject(String key, Object object) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(object);
        LOG.info(String.format("%d bytes written for key %s", byteArrayOutputStream.toByteArray().length, key));
        preferences.putByteArray(key, byteArrayOutputStream.toByteArray());
    }

    private Object readObject(String key) throws IOException, ClassNotFoundException {
        byte[] data = preferences.getByteArray(key, null);
        if (null == data) {
            LOG.error("No data found for key " + key);
            return null;
        }
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data);
        ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
        LOG.info(String.format("%s bytes read for key %s", data.length, key));
        return objectInputStream.readObject();
    }

    @Override
    public DesktopState loadDesktopState() {
        try {
            return (DesktopState) readObject(DESKTOP_STATE);
        }
        catch (IOException e) {
            LOG.error("Error reading desktop state", e);
        }
        catch (ClassNotFoundException e) {
            LOG.error("Error reading desktop state", e);
        }
        return null;
    }

    @Override
    public void clear() {
        try {
            preferences.clear();
            LOG.info("Cleared data for node : " + clazz.getName());
        }
        catch (BackingStoreException e) {
            LOG.error("Error clearing preferences", e);
        }
    }
}

