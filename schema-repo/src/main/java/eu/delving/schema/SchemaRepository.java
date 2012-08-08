package eu.delving.schema;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamImplicit;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Try out the ideas
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class SchemaRepository {
    private MessageDigest messageDigest;
    private Schemas schemas;
    private Fetcher fetcher;

    public interface Fetcher {
        String fetchList();

        String fetchSchema(String prefix, String version, String fileName);

        Boolean isValidating();
    }

    public SchemaRepository(Fetcher fetcher) {
        this.fetcher = fetcher;
        XStream xstream = new XStream();
        xstream.processAnnotations(Schemas.class);
        this.schemas = (Schemas) xstream.fromXML(fetcher.fetchList());
        try {
            this.messageDigest = MessageDigest.getInstance("MD5");
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available??");
        }
    }

    public List<Schema> getFormats() {
        return schemas.schemas;
    }

    public String getSchema(String prefix, String versionNumber, String fileName) {
        String hash = null;
        for (Schema schema : schemas.schemas) {
            if (!schema.prefix.equals(prefix)) continue;
            for (Version version : schema.versions) {
                if (!version.number.equals(versionNumber)) continue;
                for (SchemaFile schemaFile : version.files) {
                    if (!schemaFile.name.equals(fileName)) continue;
                    hash = schemaFile.hash;
                    break;
                }
            }
        }
        if (hash == null) return null;
        String schema = fetcher.fetchSchema(prefix, versionNumber, fileName);
        if (fetcher.isValidating()) {
            String foundHash = getHashString(schema);
            if (!hash.equals(foundHash)) throw new IllegalStateException(fileName + ": expected hash " + foundHash);
        }
        return schema;
    }

    public String getHashString(String value) {
        try {
            return toHexadecimal(messageDigest.digest(value.getBytes("UTF-8")));
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    // == all of this is for binding

    @XStreamAlias("schemas")
    public static class Schemas {
        @XStreamImplicit
        List<Schema> schemas;
    }

    @XStreamAlias("schema")
    public static class Schema {
        @XStreamAsAttribute
        String prefix;
        @XStreamImplicit
        List<Version> versions;
    }

    @XStreamAlias("version")
    public static class Version {
        @XStreamAsAttribute
        String number;
        String author;
        String message;
        String date;
        @XStreamImplicit
        List<SchemaFile> files;
    }

    @XStreamAlias("file")
    public static class SchemaFile {
        @XStreamAsAttribute
        String name;
        @XStreamAsAttribute
        String hash;
    }


    static final String HEXES = "0123456789ABCDEF";

    private static String toHexadecimal(byte[] raw) {
        final StringBuilder hex = new StringBuilder(2 * raw.length);
        for (final byte b : raw) {
            hex.append(HEXES.charAt((b & 0xF0) >> 4)).append(HEXES.charAt((b & 0x0F)));
        }
        return hex.toString();
    }

}
