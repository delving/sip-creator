package eu.delving.schema;


import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * See if it works
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestSchemaRepository {

    @Test
    public void testLocal() {
        System.out.println("from local resources:");
        fetchTest(new ResourceFetcher());
    }

    @Test
    public void testSchemasDelvingEU() {
        System.out.println("from schemas.delving.eu:");
        fetchTest(new HTTPFetcher());
    }

    private void fetchTest(SchemaRepository.Fetcher fetcher) {
        SchemaRepository repo = new SchemaRepository(fetcher);
        for (SchemaRepository.Format format : repo.getFormats()) {
            String schema = repo.getSchema(format.prefix, "1.0.0", "record-definition.xml");
            System.out.println(schema.split("\\n").length + " lines");
            schema = repo.getSchema(format.prefix, "1.0.0", "validation.xsd");
            System.out.println(schema.split("\\n").length + " lines");
        }
    }

    private abstract class GenericFetcher implements SchemaRepository.Fetcher {

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

        String getFileContents(String path) {
            try {
                InputStream inputStream = getURL(path).openStream();
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

        abstract URL getURL(String path);
    }

    private class ResourceFetcher extends GenericFetcher {

        @Override
        public URL getURL(String path) {
            return getClass().getResource(path);
        }
    }

    private class HTTPFetcher extends GenericFetcher {

        @Override
        public URL getURL(String path) {
            try {
                return new URL("http://schemas.delving.eu"+path);
            }
            catch (MalformedURLException e) {
                throw new RuntimeException("HTTP failed");
            }
        }
    }


}
