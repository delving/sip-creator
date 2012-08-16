package eu.delving.schema;


import eu.delving.schema.xml.Schema;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;

import static eu.delving.schema.SchemaType.RECORD_DEFINITION;
import static eu.delving.schema.SchemaType.VALIDATION_SCHEMA;

/**
 * See if it works
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestSchemaRepository {

    @Test
    public void compare() throws IOException {
        HTTPFetcher httpFetcher = new HTTPFetcher();
        LocalFetcher localFetcher = new LocalFetcher();
        SchemaRepository repo = new SchemaRepository(localFetcher);
        for (Schema schema : repo.getSchemas()) {
            SchemaVersion schemaVersion = new SchemaVersion(
                    schema.prefix,
                    schema.versions.get(0).number
            );
            String http = httpFetcher.fetchSchema(schemaVersion, SchemaType.RECORD_DEFINITION).trim();
            String resource = localFetcher.fetchSchema(schemaVersion, SchemaType.RECORD_DEFINITION).trim();
            Assert.assertEquals("Should be identical", resource, http);
            http = httpFetcher.fetchSchema(schemaVersion, SchemaType.VALIDATION_SCHEMA).trim();
            resource = localFetcher.fetchSchema(schemaVersion, SchemaType.VALIDATION_SCHEMA).trim();
            Assert.assertEquals("Should be identical", resource, http);
        }
    }

    @Test
    public void testLocal() throws IOException {
        System.out.println("from local resources:");
        fetchTest(new LocalFetcher());
    }

    @Test
    public void testSchemasDelvingEU() throws IOException {
        System.out.println("from schemas.delving.eu:");
        fetchTest(new HTTPFetcher());
    }

    private void fetchTest(Fetcher fetcher) throws IOException {
        SchemaRepository repo = new SchemaRepository(fetcher);
        for (Schema schema : repo.getSchemas()) {
            SchemaVersion schemaVersion = new SchemaVersion(
                    schema.prefix,
                    schema.versions.get(0).number
            );
            String content = repo.getSchema(schemaVersion, RECORD_DEFINITION);
            Assert.assertTrue(content != null);
            System.out.println(content.split("\\n").length + " lines");
            content = repo.getSchema(schemaVersion, VALIDATION_SCHEMA);
            Assert.assertTrue(content != null);
            System.out.println(content.split("\\n").length + " lines");
        }
    }

    private class HTTPFetcher implements Fetcher {
        private HttpClient httpClient = new DefaultHttpClient();

        @Override
        public String fetchList() throws IOException {
            return getFileContents(SCHEMA_DIRECTORY);
        }

        @Override
        public String fetchFactDefinitions(String versionNumber) throws IOException {
            return getFileContents(FACT_DEFINITIONS);
        }

        @Override
        public String fetchSchema(SchemaVersion schemaVersion, SchemaType schemaType) throws IOException {
            return getFileContents(schemaVersion.getPath(schemaType));
        }

        @Override
        public Boolean isValidating() {
            return true;
        }

        String getFileContents(String path) throws IOException {
            HttpGet get = new HttpGet("http://schemas.delving.eu" + path);
            HttpResponse response = httpClient.execute(get);
            StatusLine line = response.getStatusLine();
            if (line.getStatusCode() != HttpStatus.SC_OK) {
                throw new IOException("HTTP Error " + line.getStatusCode() + " " + line.getReasonPhrase());
            }
            return EntityUtils.toString(response.getEntity());
        }
    }

    private class LocalFetcher implements Fetcher {
        private File schemas;

        @Override
        public String fetchList() {
            return getFileContents(SCHEMA_DIRECTORY);
        }

        @Override
        public String fetchFactDefinitions(String versionNumber) throws IOException {
            return getFileContents(FACT_DEFINITIONS);
        }

        @Override
        public String fetchSchema(SchemaVersion schemaVersion, SchemaType schemaType) {
            return getFileContents(schemaVersion.getPath(schemaType));
        }

        @Override
        public Boolean isValidating() {
            return true;
        }

        public String getFileContents(String path) {
            if (schemas == null) findSchemasDirectory();
            try {
                InputStream in = new FileInputStream(new File(schemas, path));
                StringBuilder text = new StringBuilder();
                boolean firstLine = true;
                for (String line : IOUtils.readLines(in, "UTF-8")) {
                    if (firstLine) {
                        firstLine = false;
                    }
                    else {
                        text.append('\n');
                    }
                    text.append(line);
                }
                return text.toString();
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void findSchemasDirectory() {
            String here = new File(".").getAbsolutePath();
            schemas = new File(here.substring(0, here.length() - 1)); // remove the dot
            while (true) {
                String schemasPath = schemas.getAbsolutePath();
                if (schemasPath.length() > 1) {
                    File[] schemaDirectory = schemas.listFiles(new SchemaFilter());
                    if (schemaDirectory.length == 1) {
                        schemas = schemaDirectory[0];
                        break;
                    }
                }
                schemas = schemas.getParentFile();
            }
        }

        private class SchemaFilter implements FileFilter {
            @Override
            public boolean accept(File directory) {
                return directory.isDirectory() && directory.getName().equals("schemas.delving.eu");
            }
        }
    }

}
