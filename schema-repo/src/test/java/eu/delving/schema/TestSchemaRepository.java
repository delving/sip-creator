/*
 * Copyright 2011, 2012 Delving BV
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

package eu.delving.schema;

import eu.delving.schema.util.FileSystemFetcher;
import eu.delving.schema.xml.Schema;
import eu.delving.schema.xml.Version;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

import static eu.delving.schema.SchemaType.*;

/**
 * See if it works
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestSchemaRepository {

    @Test
    public void testLocal() throws IOException {
        System.out.println("from local resources:");
        fetchTest(new FileSystemFetcher(true));
    }

    @Test
    public void testSchemasDelvingEU() throws IOException {
        System.out.println("from schemas.delving.eu:");
        fetchTest(new HTTPFetcher());
    }

    @Ignore
    @Test
    public void compareLocalToOnline() throws IOException {
        HTTPFetcher httpFetcher = new HTTPFetcher();
        FileSystemFetcher localFetcher = new FileSystemFetcher(true);
        SchemaRepository repo = new SchemaRepository(localFetcher);
        for (Schema schema : repo.getSchemas()) {
            SchemaVersion schemaVersion = new SchemaVersion(schema.prefix, schema.versions.get(0).number);
            String http = httpFetcher.fetchSchema(schemaVersion, SchemaType.RECORD_DEFINITION).trim();
            String resource = localFetcher.fetchSchema(schemaVersion, SchemaType.RECORD_DEFINITION).trim();
            Assert.assertEquals("Should be identical", resource, http);
            http = httpFetcher.fetchSchema(schemaVersion, SchemaType.VALIDATION_SCHEMA).trim();
            resource = localFetcher.fetchSchema(schemaVersion, SchemaType.VALIDATION_SCHEMA).trim();
            Assert.assertEquals("Should be identical", resource, http);
        }
    }

    private void fetchTest(Fetcher fetcher) throws IOException {
        SchemaRepository repo = new SchemaRepository(fetcher);
        for (Schema schema : repo.getSchemas()) {
            for (Version version : schema.versions) {
                SchemaVersion schemaVersion = new SchemaVersion(schema.prefix, version.number);
                if (schema.prefix.equals("facts")) {
                    String content = repo.getSchema(schemaVersion, FACT_DEFINITIONS);
                    Assert.assertTrue(content != null);
                    System.out.println(schemaVersion.getFullFileName(FACT_DEFINITIONS) + ": " + lineCount(content) + " lines");
                }
                else {
                    String content = repo.getSchema(schemaVersion, RECORD_DEFINITION);
                    Assert.assertTrue(content != null);
                    System.out.println(schemaVersion.getFullFileName(RECORD_DEFINITION) + ": " + lineCount(content) + " lines");
                    content = repo.getSchema(schemaVersion, VALIDATION_SCHEMA);
                    Assert.assertTrue(content != null);
                    System.out.println(schemaVersion.getFullFileName(VALIDATION_SCHEMA) + ": " + lineCount(content) + " lines");
                }
            }
        }
    }

    private int lineCount(String content) {
        return content.split("\\n").length;
    }

    private class HTTPFetcher implements Fetcher {
        private HttpClient httpClient = new DefaultHttpClient();

        @Override
        public String fetchList() throws IOException {
            return getFileContents(SCHEMA_DIRECTORY);
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
                throw new IOException("HTTP Error " + line.getStatusCode() + " " + line.getReasonPhrase() + " >> " + path);
            }
            return EntityUtils.toString(response.getEntity(), "UTF-8");
        }
    }

}
