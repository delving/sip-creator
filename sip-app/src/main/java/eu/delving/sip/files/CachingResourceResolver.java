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
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import java.io.*;

/**
 * Fetch XML resources and cache them. // todo: make it work
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class CachingResourceResolver implements LSResourceResolver {
    private File home;
    private HttpClient httpClient;

    public CachingResourceResolver(File home, HttpClient httpClient) {
        this.home = home;
        this.httpClient = httpClient;
    }

    @Override
    public LSInput resolveResource(String type, final String namespaceUri, final String publicId, final String systemId, final String baseUri) {
        File resourceFile = createResourceFile(systemId);
        if (!resourceFile.exists()) {
            try {
                InputStream inputStream = fetchResource(systemId);
                OutputStream out = new FileOutputStream(resourceFile);
                IOUtils.copy(inputStream, out);
                IOUtils.closeQuietly(out);
            }
            catch (Exception e) {
                throw new RuntimeException("Unable to store the fetched resource", e);
            }
        }
        return new FileBasedInput(publicId, systemId, baseUri, resourceFile);
    }

    private InputStream fetchResource(String url) {
        try {
            HttpGet get = new HttpGet(url);
            HttpResponse response = httpClient.execute(get);
            return response.getEntity().getContent();
        }
        catch (Exception e) {
            throw new RuntimeException("Cannot fetch "+url, e);
        }
    }

    private File createResourceFile(String url) {
        int lastSlash = url.lastIndexOf("/");
        if (lastSlash < 0) throw new RuntimeException("Strange resource system id: " + url);
        String schemaName = url.substring(lastSlash + 1);
        return new File(home, schemaName);
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
