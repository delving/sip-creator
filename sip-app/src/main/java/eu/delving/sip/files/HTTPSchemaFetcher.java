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

package eu.delving.sip.files;

import eu.delving.schema.Fetcher;
import eu.delving.schema.SchemaType;
import eu.delving.schema.SchemaVersion;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

/**
 * Fetch schema text from the schema repository, checking first locally
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class HTTPSchemaFetcher implements Fetcher {
    private static final String SCHEMA_SERVER = "http://schemas.delving.eu";
    private HttpClient httpClient;

    public HTTPSchemaFetcher(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String fetchList() throws IOException {
        return fetchFile(SCHEMA_DIRECTORY);
    }

    @Override
    public String fetchFactDefinitions(String versionNumber) throws IOException {
        return fetchFile(FACT_DEFINITIONS);
    }

    @Override
    public String fetchSchema(SchemaVersion schemaVersion, SchemaType schemaType) throws IOException {
        return fetchFile(schemaVersion.getPath(schemaType));
    }

    @Override
    public Boolean isValidating() {
        return true;
    }

    private String fetchFile(String path) throws IOException {
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
