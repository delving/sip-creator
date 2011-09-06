package eu.delving.metadata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.TreeMap;

/**
 * The extra data required when uploading a zip to the repository
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
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
