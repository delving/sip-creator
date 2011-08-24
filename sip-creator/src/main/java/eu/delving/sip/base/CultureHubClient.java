/*
 * Copyright 2011 DELVING BV
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

package eu.delving.sip.base;

import eu.delving.metadata.Hasher;
import eu.delving.sip.FileStore;
import eu.delving.sip.FileType;
import eu.delving.sip.ProgressListener;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.Logger;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.zip.ZipInputStream;

import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_ACCEPTABLE;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;

/**
 * Connect to the culture hub using HTTP
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class CultureHubClient {
    private Logger log = Logger.getLogger(getClass());
    private Executor executor = Executors.newSingleThreadExecutor();
    private Context context;

    public interface Context {
        String getServerUrl();

        String getAccessToken();

        void tellUser(String message);
    }

    public enum Response {
        OK(SC_OK),
        GOT_IT_ALREADY(SC_NOT_ACCEPTABLE),
        DATA_SET_NOT_FOUND(SC_NOT_FOUND),
        ACCESS_KEY_FAILURE(SC_FORBIDDEN),
        UNAUTHORIZED(SC_UNAUTHORIZED),
        SYSTEM_ERROR(SC_INTERNAL_SERVER_ERROR),
        UNKNOWN_RESPONSE(-1);

        private int httpCode;

        Response(int httpCode) {
            this.httpCode = httpCode;
        }

        static Response translate(int httpCode) {
            for (Response response : values()) {
                if (response.httpCode == httpCode) {
                    return response;
                }
            }
            return UNKNOWN_RESPONSE;
        }
    }

    public interface UploadCallback {

        void onResponseReceived(Response response);

    }

    public CultureHubClient(Context context) {
        this.context = context;
    }

    public void uploadFile(FileType fileType, String spec, File file, ProgressListener progressListener, UploadCallback callback) {
        executor.execute(new FileUploader(fileType, spec, file, progressListener, callback));
    }

    public void downloadDataSet(FileStore.DataSetStore dataSetStore, ProgressListener progressListener) {
        executor.execute(new DataSetDownloader(dataSetStore, progressListener));
    }

    public class FileUploader implements Runnable {
        private static final int BLOCK_SIZE = 4096;
        private Logger log = Logger.getLogger(getClass());
        private File file;
        private String spec;
        private FileType fileType;
        private ProgressListener progressListener;
        private UploadCallback uploadCallback;

        public FileUploader(FileType fileType, String spec, File file, ProgressListener progressListener, UploadCallback uploadCallback) {
            this.fileType = fileType;
            this.spec = spec;
            this.file = file;
            this.progressListener = progressListener;
            this.uploadCallback = uploadCallback;
        }

        @Override
        public void run() {
            final int totalBlocks = (int) (file.length() / BLOCK_SIZE);
            progressListener.setTotal(totalBlocks);
            try {
                file = Hasher.ensureFileHashed(file);
                log.info("Uploading " + file);
                final Response response = uploadFile();
                boolean success = response == Response.OK;
                progressListener.finished(success);
                if (!success) {
                    notifyUser(response);
                }
                uploadCallback.onResponseReceived(response);
            }
            catch (IOException e) {
                log.warn("Unable to upload file " + file.getAbsolutePath(), e);
                progressListener.finished(false);
                notifyUser(Response.SYSTEM_ERROR);
            }
        }

        private Response uploadFile() {
            HttpPost httpPost = new HttpPost(createRequestUrl());
            httpPost.setEntity(createEntity());
            return execute(httpPost);
        }

        private FileEntity createEntity() {
            FileEntity fileEntity = new FileEntity(file, fileType.getContentType());
            fileEntity.setChunked(true);
            return fileEntity;
        }

        private String createRequestUrl() {
            return String.format(
                    "%s/submit/%s/%s/%s?accessKey=%s",
                    context.getServerUrl(),
                    spec,
                    fileType,
                    file.getName(),
                    context.getAccessToken()
            );
        }

        private class FileEntity extends AbstractHttpEntity implements Cloneable {

            private final File file;
            private long bytesSent;
            private int blocksReported;
            private boolean abort = false;

            public FileEntity(final File file, final String contentType) {
                if (file == null) {
                    throw new IllegalArgumentException("File may not be null");
                }
                this.file = file;
                setContentType(contentType);
            }

            public boolean isRepeatable() {
                return true;
            }

            public long getContentLength() {
                return this.file.length();
            }

            public InputStream getContent() throws IOException {
                return new FileInputStream(this.file);
            }

            public void writeTo(final OutputStream outputStream) throws IOException {
                if (outputStream == null) {
                    throw new IllegalArgumentException("Output stream may not be null");
                }
                InputStream inputStream = new FileInputStream(this.file);
                try {
                    byte[] buffer = new byte[BLOCK_SIZE];
                    int bytes;
                    while (!abort && (bytes = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytes);
                        bytesSent += bytes;
                        int blocks = (int) (bytesSent / BLOCK_SIZE);
                        if (blocks > blocksReported) {
                            blocksReported = blocks;
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    if (!progressListener.setProgress(blocksReported)) {
                                        abort = true;
                                    }
                                }
                            });
                        }
                    }
                    outputStream.flush();
                }
                finally {
                    inputStream.close();
                }
            }

            /**
             * Tells that this entity is not streaming.
             *
             * @return <code>false</code>
             */
            public boolean isStreaming() {
                return false;
            }

            public Object clone() throws CloneNotSupportedException {
                // File instance is considered immutable
                // No need to make a copy of it
                return super.clone();
            }
        }
    }

    private class DataSetDownloader implements Runnable {
        private FileStore.DataSetStore dataSetStore;
        private ProgressListener progressListener;

        private DataSetDownloader(FileStore.DataSetStore dataSetStore, ProgressListener progressListener) {
            this.dataSetStore = dataSetStore;
            this.progressListener = progressListener;
        }

        @Override
        public void run() {
            HttpClient httpClient = new DefaultHttpClient();
            try {
                HttpGet method = new HttpGet(String.format(
                        "%s/fetch/%s-sip.zip?accessKey=%s",
                        context.getServerUrl(),
                        dataSetStore.getSpec(),
                        context.getAccessToken()
                ));
                HttpResponse httpResponse = httpClient.execute(method);
                if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                    HttpEntity entity = httpResponse.getEntity();
                    ZipInputStream zipInputStream = new ZipInputStream(entity.getContent());
                    dataSetStore.acceptSipZip(zipInputStream, progressListener);
                }
                else {
                    log.warn("Unable to download source. HTTP response " + httpResponse.getStatusLine().getReasonPhrase());
                }
            }
            catch (Exception e) {
                log.warn("Unable to download source", e);
                context.tellUser("Unable to download source");
            }
        }
    }

    private Response execute(HttpGet httpGet) {
        HttpClient httpClient = new DefaultHttpClient();
        try {
            return translate(httpClient.execute(httpGet));
        }
        catch (HttpHostConnectException e) {
            log.warn("Problem executing get (connecting)", e);
            return null;
        }
        catch (IOException e) {
            log.warn("Problem executing get (I/O)", e);
            return null;
        }
        finally {
            httpClient.getConnectionManager().shutdown();
        }
    }

    private Response execute(HttpPost httpPost) {
        log.info("POST: " + httpPost.getURI());
        HttpClient httpClient = new DefaultHttpClient();
        try {
            return translate(httpClient.execute(httpPost));
        }
        catch (IOException e) {
            log.warn("Problem executing post", e);
            return Response.SYSTEM_ERROR;
        }
        finally {
            httpClient.getConnectionManager().shutdown();
        }
    }

    private Response translate(HttpResponse httpResponse) {
        return Response.translate(httpResponse.getStatusLine().getStatusCode());
    }

    private void notifyUser(final Response response) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                log.warn("Problem communicating with CultureHub: " + response);
                context.tellUser("Sorry, there was a problem communicating with Repository");
            }
        });
    }
}
