package eu.delving.schema;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamImplicit;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Try out the ideas
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class SchemaRepository {

    public static void main(String[] args) throws IOException {
        InputStream in = SchemaRepository.class.getResource("/test-repository.xml").openStream();
        XStream xstream = new XStream();
        xstream.processAnnotations(Schemas.class);
        Schemas schemas = (Schemas) xstream.fromXML(in);
        System.out.println(schemas.formats.size());
    }

    private Schemas schemas;

    public interface Fetcher {
        String fetchList();

        String fetchSchema(String prefix, String version, String fileName);

        Boolean isValidating();
    }

    public SchemaRepository(Fetcher fetcher) {
        XStream xstream = new XStream();
        xstream.processAnnotations(Schemas.class);
        this.schemas = (Schemas) xstream.fromXML(fetcher.fetchList());
    }

    public List<Format> getFormats() {
        return schemas.formats;
    }

    public String getSchema(String prefix, String versionNumber, String fileName) {
        String hash = null;
        for (Format format : schemas.formats) {
            if (!format.prefix.equals(prefix)) continue;
            for (Version version : format.versions) {
                if (!version.number.equals(versionNumber)) continue;
                for (SchemaFile schemaFile : version.files) {
                    if (!schemaFile.name.equals(fileName)) continue;
                    hash = schemaFile.hash;
                    break;
                }
            }
        }
        return hash;
    }

    // == all of this is for binding

    @XStreamAlias("schemas")
    public static class Schemas {
        @XStreamImplicit
        List<Format> formats;
    }

    @XStreamAlias("format")
    public static class Format {
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

}
