/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.sip.files;

import eu.delving.schema.Fetcher;
import eu.delving.schema.SchemaType;
import eu.delving.schema.SchemaVersion;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

/**
 * Fetch schema text from the schema repository, checking first locally
 *
 *
 */

public class SchemaFetcher implements Fetcher {
    private static final String SCHEMA_SERVER = "http://schemas.delving.eu";
    private File localSchemaDirectory;
    private HttpClient httpClient;

    public SchemaFetcher(HttpClient httpClient) {
        findSchemasDirectory();
        this.httpClient = httpClient;
    }

    @Override
    public String fetchList() throws IOException {
        if (localSchemaDirectory != null) {
            return getFileContents(SCHEMA_DIRECTORY);
        }
        else {
            return httpGetFile(SCHEMA_DIRECTORY);
        }
    }

    @Override
    public String fetchSchema(SchemaVersion schemaVersion, SchemaType schemaType) throws IOException {
        String path = schemaVersion.getPath(schemaType);
        if (localSchemaDirectory != null) {
            return getFileContents(path);
        }
        else {
            return httpGetFile(path);
        }
    }

    @Override
    public Boolean isValidating() {
        return true;
    }

    private String getFileContents(String path) {
        try {
            return FileUtils.readFileToString(new File(localSchemaDirectory, path), "UTF-8");
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void findSchemasDirectory() {
        String here = new File(".").getAbsolutePath();
        localSchemaDirectory = new File(here.substring(0, here.length() - 1)); // remove the dot
        while (localSchemaDirectory != null) {
            String schemasPath = localSchemaDirectory.getAbsolutePath();
            if (schemasPath.length() > 1) {
                File[] schemaDirectory = localSchemaDirectory.listFiles(new SchemaFilter());
                if (schemaDirectory.length == 1) {
                    localSchemaDirectory = schemaDirectory[0];
                    break;
                }
            }
            localSchemaDirectory = localSchemaDirectory.getParentFile();
        }
    }

    private class SchemaFilter implements FileFilter {
        @Override
        public boolean accept(File directory) {
            return directory.isDirectory() && directory.getName().equals("schemas.delving.eu");
        }
    }

    private String httpGetFile(String path) throws IOException {
        String url = SCHEMA_SERVER + path;
        HttpGet get = new HttpGet(url);
        HttpResponse response = httpClient.execute(get);
        StatusLine line = response.getStatusLine();
        if (line.getStatusCode() != HttpStatus.SC_OK) {
            throw new IOException("HTTP Error " + line.getStatusCode() + " " + line.getReasonPhrase());
        }
        return EntityUtils.toString(response.getEntity(), "UTF-8").trim();
    }
}
