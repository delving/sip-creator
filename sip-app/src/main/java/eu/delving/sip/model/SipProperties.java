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

package eu.delving.sip.model;

import eu.delving.sip.files.HomeDirectory;

import java.io.*;
import java.util.Properties;

import static eu.delving.sip.files.Storage.*;

public class SipProperties {
    public static final String FILE_NAME = "sip-creator.properties";

    private final boolean isAppProperties;
    private Properties prop = new Properties();

    public SipProperties() {
        this(false);
    }

    public SipProperties(boolean isAppProperties) {
        this.isAppProperties = isAppProperties;
        loadProperties();
    }

    public Properties getProp() {
        return prop;
    }

    public void setProp(Properties prop) {
        this.prop = prop;
    }

    public void saveProperties() {
        OutputStream output = null;

        try {

            output = new FileOutputStream(getPropertiesPath());

            if (prop.isEmpty()) {
                // set the properties value
                prop.setProperty(NARTHEX_URL, "http://delving.org/narthex");
                prop.setProperty(NARTHEX_USERNAME, "admin");
                prop.setProperty(NARTHEX_PASSWORD, "password");
                prop.setProperty(XSD_VALIDATION, "false");
            }


            // save properties
            prop.store(output, null);

        } catch (IOException io) {
            io.printStackTrace();
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
    }

    public File getPropertiesPath() {
        if (isAppProperties) {
            return getAppPropertiesPath();
        } else {
            return getSipPropertiesPath();
        }
    }

    public static File getAppPropertiesPath() {
        String propertyPath = System.getProperty("property.path");
        if (propertyPath != null) {
            return new File(propertyPath);
        }
        return new File(HomeDirectory.WORKSPACE_DIR.getPath(), FILE_NAME);
    }

    public static File getSipPropertiesPath() {
        return new File(HomeDirectory.getWorkspaceDir().getPath(), FILE_NAME);
    }

    public static boolean isSameAsAppPropertiesPath(File sipPropertiesPath) {
        try {
            return getAppPropertiesPath().getCanonicalPath().equals(sipPropertiesPath.getCanonicalPath());
        } catch (IOException e) {
            return false;
        }
    }

    private void loadProperties() {

        InputStream input = null;

        try {
            input = new FileInputStream(getPropertiesPath());

            if (input == null) {
                System.out.println("Sorry, unable to find " + getPropertiesPath());
                return;
            }

            //load a properties file from path
            prop.load(input);

        } catch (FileNotFoundException ex) {
            saveProperties();
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally{
            if(input!=null){
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
