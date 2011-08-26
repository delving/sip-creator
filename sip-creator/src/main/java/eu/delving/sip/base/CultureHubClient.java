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
import eu.delving.sip.ProgressListener;
import eu.delving.sip.files.FileStore;
import eu.delving.sip.files.FileStoreException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.zip.ZipInputStream;

import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;

/**
 * Connect to the culture hub using HTTP
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class CultureHubClient {
    private static final int BLOCK_SIZE = 4096;
    private Logger log = Logger.getLogger(getClass());
    private Executor executor = Executors.newSingleThreadExecutor();
    private Context context;

    public interface Context {
        String getServerUrl();

        String getAccessToken();

        void tellUser(String message);
    }

    public enum Code {
        OK(SC_OK),
        UNAUTHORIZED(SC_UNAUTHORIZED),
        SYSTEM_ERROR(SC_INTERNAL_SERVER_ERROR),
        UNKNOWN_RESPONSE(-1);

        private int httpCode;

        Code(int httpCode) {
            this.httpCode = httpCode;
        }

        static Code from(HttpResponse httpResponse) {
            int httpCode = httpResponse.getStatusLine().getStatusCode();
            for (Code code : values()) {
                if (code.httpCode == httpCode) {
                    return code;
                }
            }
            return UNKNOWN_RESPONSE;
        }
    }

    public CultureHubClient(Context context) {
        this.context = context;
    }

    public void uploadFiles(FileStore.DataSetStore store, ProgressListener progressListener) throws FileStoreException {
        executor.execute(new FileUploader(store, progressListener));
    }

    public void downloadDataSet(FileStore.DataSetStore dataSetStore, ProgressListener progressListener) {
        executor.execute(new DataSetDownloader(dataSetStore, progressListener));
    }

    public class FileUploader implements Runnable {
        private FileStore.DataSetStore store;
        private List<File> uploadFiles;
        private ProgressListener progressListener;

        public FileUploader(FileStore.DataSetStore store, ProgressListener progressListener) throws FileStoreException {
            this.store = store;
            this.uploadFiles = store.getUploadFiles();
            this.progressListener = progressListener;
        }

        @Override
        public void run() {
            try {
                HttpPost post = new HttpPost(createListRequestUrl());
                post.setEntity(createListEntity());
                post.setHeader("Accept", "text/plain");
                HttpResponse response = execute(post);
                switch (Code.from(response)) {
                    case OK:
                        HttpEntity entity = response.getEntity();
                        String listString = EntityUtils.toString(entity);
                        List<File> filteredUploadFiles = new ArrayList<File>();
                        for (String fileName: listString.split("\n")) {
                            for (File file : uploadFiles) {
                                if (file.getName().equals(fileName)) {
                                    filteredUploadFiles.add(file);
                                }
                            }
                        }
                        uploadFiles = filteredUploadFiles;
                        break;
                    case UNAUTHORIZED:
                    case SYSTEM_ERROR:
                    case UNKNOWN_RESPONSE:
                        throw new IOException("Unable to fetch file list, response: "+Code.from(response));
                }
                for (File file : uploadFiles) {
                    log.info("Uploading " + file);
                    post = new HttpPost(createFileRequestUrl(file));
                    post.setEntity(new FileEntity(file, progressListener));
                    response = execute(post);
                    switch (Code.from(response)) {
                        case OK:
                            break;
                        case UNAUTHORIZED:
                        case SYSTEM_ERROR:
                        case UNKNOWN_RESPONSE:
                            throw new IOException("Unable to upload file, response: "+Code.from(response));
                    }
                }
            }
            catch (IOException e) {
                notifyUser(e.getMessage());
            }
        }

        private HttpEntity createListEntity() throws UnsupportedEncodingException {
            StringBuilder fileList = new StringBuilder();
            for (File file : uploadFiles) {
                fileList.append(file.getName()).append("\n");
            }
            return new StringEntity(fileList.toString());
        }

        private String createListRequestUrl() {
            return String.format(
                    "%s/submit/%s?accessToken=%s",
                    context.getServerUrl(),
                    store.getSpec(),
                    context.getAccessToken()
            );
        }

        private String createFileRequestUrl(File file) {
            return String.format(
                    "%s/submit/%s/%s?accessToken=%s",
                    context.getServerUrl(),
                    store.getSpec(),
                    file.getName(),
                    context.getAccessToken()
            );
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
                HttpGet get = new HttpGet(String.format(
                        "%s/fetch/%s-sip.zip?accessKey=%s",
                        context.getServerUrl(),
                        dataSetStore.getSpec(),
                        context.getAccessToken()
                ));
                get.setHeader("Accept", "application/zip");
                HttpResponse httpResponse = httpClient.execute(get);
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

    private HttpResponse execute(HttpPost httpPost) throws IOException {
        HttpClient httpClient = new DefaultHttpClient();
        try {
            return httpClient.execute(httpPost);
        }
        finally {
            httpClient.getConnectionManager().shutdown();
        }
    }

    private static String deriveContentType(File file) {
        String name = Hasher.extractFileName(file);
        if (name.endsWith(".gz")) {
            return "application/x-gzip";
        }
        else if (name.endsWith(".txt")) {
            return "text/plain";
        }
        else if (name.endsWith(".xml")) {
            return "text/xml";
        }
        else {
            throw new RuntimeException("Cannot determine content type of "+file.getAbsolutePath());
        }
    }

    private void notifyUser(final String message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                log.warn("Problem communicating with CultureHub: " + message);
                context.tellUser("<html>Sorry, there was a problem communicating with Repository<br>"+message);
            }
        });
    }

    private static class FileEntity extends AbstractHttpEntity implements Cloneable {
        private final File file;
        private final ProgressListener progressListener;
        private long bytesSent;
        private int blocksReported;
        private boolean abort = false;

        public FileEntity(File file, ProgressListener progressListener) {
            this.file = file;
            this.progressListener = progressListener;
            setChunked(true);
            setContentType(deriveContentType(file));
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

        public void writeTo(OutputStream outputStream) throws IOException {
            progressListener.prepareFor((int) (getContentLength() / BLOCK_SIZE));
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
                progressListener.finished(!abort);
            }
        }

        public boolean isStreaming() {
            return false;
        }

        public Object clone() throws CloneNotSupportedException {
            return super.clone();
        }
    }

}
