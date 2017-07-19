package eu.delving.sip.model;

import eu.delving.sip.files.HomeDirectory;

import java.io.*;
import java.util.Properties;

import static eu.delving.sip.files.Storage.*;

public class SipProperties {

    private Properties prop = new Properties();

    public SipProperties() {
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

    private File getPropertiesPath() {
        String propertyPath = System.getProperty("property.path");
        if (propertyPath != null) {
            File propertyFile = new File(propertyPath);
            return propertyFile;
        }
        String filename = "sip-creator.properties";
        return new File(HomeDirectory.WORKSPACE_DIR.toString(), filename);
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
