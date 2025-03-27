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

package eu.delving.metadata;

import org.apache.commons.io.IOUtils;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import java.io.*;
import java.net.URL;

/**
 * Fetch XML resources using HTTP and cache them locally in a subdirectory beside all of the directories for
 * the datasets.  With the resources fetched, it becomes possible to work off-line.
 */
public class CachedResourceResolver implements LSResourceResolver {
    private Context context;

    public interface Context {
        String get(String url);
        File file(String systemId);
    }

    public CachedResourceResolver(Context context) {
        this.context = context;
    }

    public CachedResourceResolver() {
        this.context = new Context() {
            @Override
            public String get(String urlString) {
                try {
                    URL url = new URL(urlString);
                    return IOUtils.toString(url.openStream(), "UTF-8");
                }
                catch (Exception e) {
                    throw new RuntimeException("Problem fetching", e);
                }
            }

            @Override
            public File file(String systemId) {
                File cache = new File("/tmp");
                return new File(cache, systemId.replaceAll("[/:]", "_"));
            }
        };
    }

    @Override
    public LSInput resolveResource(String type, final String namespaceUri, final String publicId, final String systemId, final String baseUri) {
        File resourceFile = context.file(systemId);
        if (!resourceFile.exists()) {
            String internalLocation = "/cache/" + systemId.replaceAll("[/:]", "_");
            InputStream cachedIn = getClass().getResourceAsStream(internalLocation);

            try {
                String schemaText;
                if (cachedIn != null) {
                    StringBuilder schemaTextBuilder = new StringBuilder();
                    try(BufferedReader reader = new BufferedReader(new InputStreamReader(cachedIn))) {
                        String line;
                        while((line = reader.readLine()) != null) {
                            schemaTextBuilder.append(line);
                            schemaTextBuilder.append('\n');
                        }
                        schemaText = schemaTextBuilder.toString();
                    }
                } else {
                    schemaText = fetchResource(systemId, baseUri);
                }
                FileOutputStream fileOutputStream = new FileOutputStream(resourceFile);
                IOUtils.write(schemaText, fileOutputStream, "UTF-8");
                IOUtils.closeQuietly(fileOutputStream);
            } catch (Exception e) {
                throw new RuntimeException("Unable to fetch and store resource: " + resourceFile, e);
            }
        }
        return new FileBasedInput(publicId, systemId, baseUri, resourceFile);
    }

    private String fetchResource(String systemId, String baseUri) throws IOException {
        String urlString = systemId;
        if (!urlString.startsWith("http:")) {
            if (!baseUri.startsWith("http:")) throw new IOException("No URL found");
            String baseURL = baseUri.substring(0, baseUri.lastIndexOf('/') + 1);
            urlString = baseURL + systemId;
        }
        return context.get(urlString);
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
