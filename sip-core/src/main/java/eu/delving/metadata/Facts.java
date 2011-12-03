/*
 * Copyright 2011 DELVING BV
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

package eu.delving.metadata;

import java.io.*;
import java.util.Map;
import java.util.TreeMap;

/**
 * This is a simple map and how it is stored in a file for reading.  Get returns
 * an empty string if nothing is found.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class Facts {
    private Map<String, String> map = new TreeMap<String, String>();

    public boolean set(String name, String value) {
        String existing = map.get(name);
        if (existing == null || !value.equals(existing)) {
            map.put(name, value);
            return true;
        }
        return false;
    }

    public Map<String, String> getMap() {
        return map;
    }

    public String get(String name) {
        String value = map.get(name);
        if (value == null) {
            map.put(name, value = "");
        }
        return value;
    }

    public static Facts read(InputStream inputStream) throws MetadataException {
        try {
            Facts facts = new Facts();
            BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("#")) continue;
                int equals = line.indexOf("=");
                if (equals < 0) {
                    continue;
                }
                String fieldName = line.substring(0, equals).trim();
                String value = line.substring(equals + 1).trim();
                facts.set(fieldName, value);
            }
            in.close();
            return facts;
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        catch (IOException e) {
            throw new MetadataException("Unable to read facts", e);
        }
    }
}
