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
        String schema = repo.getSchema("icn", "1.0.0", "record-definition.xml");
        System.out.println(schema);
    }

    private class ResourceFetcher implements SchemaRepository.Fetcher {

        @Override
        public String fetchList() {
            try {
                InputStream inputStream = getClass().getResource("/test-repository.xml").openStream();
                StringBuilder xml = new StringBuilder();
                for (String line : IOUtils.readLines(inputStream)) {
                    xml.append(line);
                }
                return xml.toString();
            }
            catch (IOException e) {
                throw new RuntimeException("oops", e);
            }
        }

        @Override
        public String fetchSchema(String prefix, String version, String fileName) {
            throw new RuntimeException("not implemented");
        }

        @Override
        public Boolean isValidating() {
            return false;
        }
    }
}
