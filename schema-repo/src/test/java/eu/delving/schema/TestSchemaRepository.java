package eu.delving.schema;


import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

/**
 * See if it works
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestSchemaRepository {

    @Test
    public void read() {
        SchemaRepository repo = new SchemaRepository(new ResourceFetcher());
        for (SchemaRepository.Format format : repo.getFormats()) {
            String schema = repo.getSchema(format.prefix, "1.0.0", "record-definition.xml");
            System.out.println(schema.split("\\n").length + " lines");
            schema = repo.getSchema(format.prefix, "1.0.0", "validation.xsd");
            System.out.println(schema.split("\\n").length + " lines");
        }
    }

    private class ResourceFetcher implements SchemaRepository.Fetcher {

        @Override
        public String fetchList() {
            return getFileContents("/schema-repository.xml");
        }

        @Override
        public String fetchSchema(String prefix, String versionNumber, String fileName) {
            String path = String.format("/%s/%s_%s_%s", prefix, prefix, versionNumber, fileName);
            return getFileContents(path);
        }

        @Override
        public Boolean isValidating() {
            return true;
        }
    }

    private String getFileContents(String path) {
        try {
            InputStream inputStream = getClass().getResource(path).openStream();
            StringBuilder xml = new StringBuilder();
            for (String line : IOUtils.readLines(inputStream)) {
                xml.append(line).append('\n');
            }
            return xml.toString();
        }
        catch (IOException e) {
            throw new RuntimeException("oops", e);
        }
    }
}
