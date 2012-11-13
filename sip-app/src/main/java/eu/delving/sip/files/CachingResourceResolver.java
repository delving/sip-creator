/*
 * Copyright 2011, 2012 Delving BV
 *
 * Licensed under the EUPL, Version 1.0 or? as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the
 * Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.delving.sip.files;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import java.io.*;

/**
 * Fetch XML resources using HTTP and cache them locally in a subdirectory beside all of the directories for
 * the datasets.  With the resources fetched, it becomes possible to work off-line.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class CachingResourceResolver implements LSResourceResolver {
    private Storage storage;
    private HttpClient httpClient;

    public CachingResourceResolver(Storage storage, HttpClient httpClient) {
        this.storage = storage;
        this.httpClient = httpClient;
    }

    @Override
    public LSInput resolveResource(String type, final String namespaceUri, final String publicId, final String systemId, final String baseUri) {
        File resourceFile = createResourceFile(systemId, baseUri);
        if (!resourceFile.exists()) {
            try {
                String schemaText = fetchResource(systemId, baseUri);
                FileOutputStream fileOutputStream = new FileOutputStream(resourceFile);
                IOUtils.write(schemaText, fileOutputStream, "UTF-8");
                IOUtils.closeQuietly(fileOutputStream);
            }
            catch (Exception e) {
                throw new RuntimeException("Unable to fetch and store resource", e);
            }
        }
        return new FileBasedInput(publicId, systemId, baseUri, resourceFile);
    }

    private String fetchResource(String systemId, String baseUri) throws IOException {
        String url = systemId;
        if (!url.startsWith("http:")) {
            if (!baseUri.startsWith("http:")) throw new IOException("No URL found");
            String baseURL = baseUri.substring(0, baseUri.lastIndexOf('/') + 1);
            url = baseURL + systemId;
        }
        HttpGet get = new HttpGet(url);
        HttpResponse response = httpClient.execute(get);
        StatusLine line = response.getStatusLine();
        if (line.getStatusCode() != HttpStatus.SC_OK) {
            throw new IOException(String.format(
                    "HTTP Error %s (%s) on %s",
                    line.getStatusCode(), line.getReasonPhrase(), url
            ));
        }
        return EntityUtils.toString(response.getEntity());
    }

    private File createResourceFile(String systemId, String baseUri) {
        int lastSlash = systemId.lastIndexOf("/");
        if (lastSlash < 0) {
            return storage.cache(systemId);
        }
        else {
            String schemaName = systemId.substring(lastSlash + 1);
            return storage.cache(schemaName);
        }
    }

    private static class FileBasedInput implements LSInput {

        private String publicId, systemId, baseUri;
        private File file;

        private FileBasedInput(String publicId, String systemId, String baseUri, File file) {
            this.publicId = publicId;
            this.systemId = systemId;
            this.baseUri = baseUri;
            this.file = file;
        }

        @Override
        public Reader getCharacterStream() {
            try {
                return new FileReader(file);
            }
            catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void setCharacterStream(Reader reader) {
            throw new RuntimeException();
        }

        @Override
        public InputStream getByteStream() {
            try {
                return new FileInputStream(file);
            }
            catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void setByteStream(InputStream inputStream) {
            throw new RuntimeException();
        }

        @Override
        public String getStringData() {
            try {
                return IOUtils.toString(getByteStream());
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void setStringData(String s) {
            throw new RuntimeException();
        }

        @Override
        public String getSystemId() {
            return systemId;
        }

        @Override
        public void setSystemId(String s) {
            this.systemId = s;
        }

        @Override
        public String getPublicId() {
            return publicId;
        }

        @Override
        public void setPublicId(String s) {
            this.publicId = s;
        }

        @Override
        public String getBaseURI() {
            return baseUri;
        }

        @Override
        public void setBaseURI(String s) {
            throw new RuntimeException();
        }

        @Override
        public String getEncoding() {
            return "UTF-8";
        }

        @Override
        public void setEncoding(String s) {
            throw new RuntimeException();
        }

        @Override
        public boolean getCertifiedText() {
            return false;
        }

        @Override
        public void setCertifiedText(boolean b) {
            throw new RuntimeException();
        }
    }
}
