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

package eu.delving.sip.base;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import eu.delving.XStreamFactory;
import eu.delving.metadata.Hasher;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.HomeDirectory;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.Feedback;
import eu.delving.sip.model.SipModel;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;

/**
 * Connect to the culture hub using HTTP for fetching, uploading, and unlocking datasets.
 *
 *
 */

public class NetworkClient {
    private static final int BLOCK_SIZE = 1024;
    private final SipModel sipModel;
    private final HttpClient httpClient;
    private final HttpClientContext httpClientContext;
    public final NarthexCredentials narthexCredentials;
    private volatile boolean loggedIn;

    public enum Code {
        OK(SC_OK, "All is well"),
        NOT_FOUND(SC_NOT_FOUND, "Resource not found"),
        UNAUTHORIZED(SC_UNAUTHORIZED, "Not authorized"),
        SYSTEM_ERROR(SC_INTERNAL_SERVER_ERROR, "Server system error"),
        UNKNOWN_RESPONSE(-1, "Unknown response");

        private int httpCode;
        private String message;

        Code(int httpCode, String message) {
            this.httpCode = httpCode;
            this.message = message;
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

    public interface NarthexCredentials {
        boolean areSet();

        void ask();

        String narthexUrl();

        String narthexUser();

        String narthexPassword();
    }

    public NetworkClient(SipModel sipModel, NarthexCredentials narthexCredentials) {
        this.sipModel = sipModel;
        this.narthexCredentials = narthexCredentials;

        try {
            URL url = new URL(narthexCredentials.narthexUrl());
            String hostname = url.getHost();
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(narthexCredentials.narthexUser(),
                narthexCredentials.narthexPassword());
            AuthScope narthexRealm = new AuthScope(hostname, -1, "narthex");
            credentialsProvider.setCredentials(narthexRealm, credentials);

            AuthCache authCache = new BasicAuthCache();
            authCache.put(new HttpHost(hostname, -1, url.getProtocol()), new BasicScheme());

            // Add AuthCache to the execution context
            final HttpClientContext context = HttpClientContext.create();
            context.setCredentialsProvider(credentialsProvider);
            context.setAuthCache(authCache);
            this.httpClientContext = context;

            HttpClientBuilder builder = HttpClientBuilder.create().setDefaultCredentialsProvider(credentialsProvider);
            builder = HttpClientFactory.handleProxy(narthexCredentials.narthexUrl(), builder);
            this.httpClient = builder.build();

        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public interface NarthexListListener {

        void listReceived(SipZips sipZips);

        void failed(Exception e);
    }

    public interface NarthexLoginFailure {
        void loginFailure(String reason);
    }

    public void afterLogin(Work work, NarthexLoginFailure loginFailure) {
        if (loggedIn) {
            sipModel.exec(work);
        }
        else {
            sipModel.exec(new NarthexLogin(work, loginFailure));
        }
    }

    public void fetchNarthexSipList(final NarthexListListener narthexListListener) {
        afterLogin(new NarthexListFetcher(narthexListListener), reason -> narthexListListener.failed(new Exception(reason)));
    }

    public void downloadNarthexDataset(String fileName, DataSet dataSet, Swing finished) {
        afterLogin(new NarthexDatasetDownloader(fileName, dataSet, finished), reason -> {
        });
    }

    public void uploadNarthex(File sipZipFile, String datasetName, Swing finished) throws StorageException {
        afterLogin(new NarthexUploader(sipZipFile, datasetName, finished), reason -> {
        });
    }

    /**
     * Upload a processed file to Narthex.
     * This uploads the ZSTD-compressed RDF/XML file along with record counts,
     * allowing Narthex to skip its processing step.
     *
     * @param processedFile The processed RDF/XML file (ZSTD compressed)
     * @param datasetSpec The dataset spec (name)
     * @param validCount Number of valid records
     * @param invalidCount Number of invalid records
     * @param finished Callback when upload completes
     */
    public void uploadProcessedToNarthex(File processedFile, String datasetSpec, int validCount, int invalidCount, Swing finished) {
        afterLogin(new NarthexProcessedUploader(processedFile, datasetSpec, validCount, invalidCount, finished), reason -> {
            feedback().alert("Login failed for processed upload: " + reason);
        });
    }

    // NARTHEX ========================================

    private class NarthexLogin implements Work {
        private final Work afterLogin;
        private final NarthexLoginFailure failure;

        private NarthexLogin(Work afterLogin, NarthexLoginFailure failure) {
            this.afterLogin = afterLogin;
            this.failure = failure;
        }

        @Override
        public void run() {
            try {
                // if we can fetch the list of sipzips, we are ok
                String requestUrl = String.format(
                    "%s/sip-app",
                    narthexCredentials.narthexUrl()
                );
                feedback().info("GET " + requestUrl);
                HttpGet get =  new HttpGet(requestUrl);
                HttpResponse response = httpClient.execute(get, httpClientContext);
                Code code = Code.from(response);

                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    switch (code) {
                        case OK:
                            loggedIn = true;
                            sipModel.exec(afterLogin);
                            break;
                        case UNAUTHORIZED:
                            loggedIn = false;
                            failure.loginFailure("Unauthorized");
                            break;
                        default:
                            reportResponse(code, response.getStatusLine());
                            failure.loginFailure(response.getStatusLine().getReasonPhrase());
                            break;
                    }
                    EntityUtils.consume(entity);
                }
                else {
                    throw new IOException("Response was empty");
                }
            }
            catch (Exception e) {
                feedback().alert("Error logging in to Narthex", e);
                failure.loginFailure(e.getMessage());
            }
        }

        @Override
        public Job getJob() {
            return Job.LOGIN;
        }

    }

    private class NarthexListFetcher implements Work {
        private NarthexListListener narthexListListener;

        public NarthexListFetcher(NarthexListListener narthexListListener) {
            this.narthexListListener = narthexListListener;
        }

        @Override
        public void run() {
            try {
                HttpGet get = createNarthexListRequest();
                get.setHeader("Accept", "text/xml");
                HttpResponse response = httpClient.execute(get, httpClientContext);
                Code code = Code.from(response);
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    switch (code) {
                        case OK:
                            SipZips sipZips = (SipZips) XStreamFactory.getStreamFor(SipZips.class).fromXML(entity.getContent());
                            narthexListListener.listReceived(sipZips);
                            break;
                        case UNAUTHORIZED:
                            loggedIn = false;
                            narthexListListener.failed(new Exception("Unauthorized"));
                            break;
                        default:
                            reportResponse(code, response.getStatusLine());
                            narthexListListener.failed(new Exception(String.format(
                                    "Response was %s. Status: [%d] %s",
                                    code,
                                    response.getStatusLine().getStatusCode(),
                                    response.getStatusLine().getReasonPhrase()
                            )));
                            break;
                    }
                    EntityUtils.consume(entity);
                }
                else {
                    throw new IOException("Response was empty");
                }
            }
            catch (Exception e) {
                feedback().alert("Error fetching list from Narthex", e);
                narthexListListener.failed(e);
            }
        }

        @Override
        public Job getJob() {
            return Job.FETCH_LIST;
        }

        private HttpGet createNarthexListRequest() {
            String requestUrl = String.format(
                    "%s/sip-app",
                    narthexCredentials.narthexUrl()
            );
            feedback().info("GET " + requestUrl);
            return new HttpGet(requestUrl);
        }
    }

    private class NarthexDatasetDownloader implements Work.DataSetWork, Work.LongTermWork {
        private final DataSet dataSet;
        private final String fileName;
        private Swing finished;
        private ProgressListener progressListener;

        private NarthexDatasetDownloader(String fileName, DataSet dataSet, Swing finished) {
            this.fileName = fileName;
            this.dataSet = dataSet;
            this.finished = finished;
        }

        @Override
        public void run() {
            boolean success = false;
            try {
                HttpGet get = createSipZipDownloadRequest();
                HttpResponse response = httpClient.execute(get, httpClientContext);
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    Code code = Code.from(response);
                    switch (code) {
                        case OK:
                            dataSet.remove();
                            File sipZipFile = new File(HomeDirectory.getWorkspaceDir(), fileName);
                            sipZipFile.deleteOnExit();
                            FileOutputStream output = new FileOutputStream(sipZipFile);
                            progressListener.prepareFor((int) (entity.getContentLength() / BLOCK_SIZE));
                            InputStream input = entity.getContent();
                            byte[] buffer = new byte[BLOCK_SIZE];
                            int count = 0;
                            int bytesRead;
                            while (-1 != (bytesRead = input.read(buffer))) {
                                output.write(buffer, 0, bytesRead);
                                count++;
                                progressListener.setProgress(count);
                            }
                            output.close();
                            dataSet.fromSipZip(sipZipFile, progressListener);
                            sipZipFile.delete();
                            success = true;
                            break;
                        case UNAUTHORIZED:
                            loggedIn = false;
                            finished = null;
                            success = false;
                            break;
                        default:
                            reportResponse(code, response.getStatusLine());
                            break;
                    }
                    EntityUtils.consume(entity);
                }
                else {
                    throw new IOException("Empty entity");
                }
            }
            catch (Exception e) {
                feedback().alert("Unable to download data set", e);
            }
            finally {
                if (!success) {
                    try {
                        dataSet.remove();
                    }
                    catch (StorageException e1) {
                        feedback().alert("Unable to remove local data set");
                    }
                }
                if (finished != null) Swing.Exec.later(finished);
            }
        }

        @Override
        public Job getJob() {
            return Job.DOWNLOAD;
        }

        @Override
        public DataSet getDataSet() {
            return dataSet;
        }

        @Override
        public void setProgressListener(ProgressListener progressListener) {
            this.progressListener = progressListener;
            progressListener.setProgressMessage("Downloading from Narthex");
        }

        private HttpGet createSipZipDownloadRequest() {
            String requestUrl = String.format(
                    "%s/sip-app/%s",
                    narthexCredentials.narthexUrl(),
                    dataSet.getSpec().trim()
            );
            feedback().info("GET " + requestUrl);
            return new HttpGet(requestUrl);
        }
    }

    private class NarthexUploader implements Work.LongTermWork {
        private final File sipZipFile;
        private final String datasetName;
        private ProgressListener progressListener;
        private Swing finished;

        NarthexUploader(File sipZipFile, String datasetName, Swing finished) throws StorageException {
            this.sipZipFile = sipZipFile;
            this.datasetName = datasetName;
            this.finished = finished;
        }

        @Override
        public void run() {
            try {
                feedback().info("Uploading SIP-Zip " + sipZipFile.getName() + " to Narthex dataset " + datasetName);
                HttpPost sipZipPost = createSipZipUploadRequest(sipZipFile, progressListener);
                FileEntity fileEntity = (FileEntity) sipZipPost.getEntity();
                HttpResponse sipZipResponse = httpClient.execute(sipZipPost, httpClientContext);
                Code code = Code.from(sipZipResponse);
                if (code != Code.OK && !fileEntity.abort) {
                    reportResponse(Code.from(sipZipResponse), sipZipResponse.getStatusLine());
                }
            }
            catch (Exception e) {
                feedback().alert("Problem connecting", e);
            }
            finally {
                if (finished != null) Swing.Exec.later(finished);
            }
        }

        @Override
        public Job getJob() {
            return Job.UPLOAD;
        }

        @Override
        public void setProgressListener(ProgressListener progressListener) {
            this.progressListener = progressListener;
            progressListener.setProgressMessage("Uploading to the Narthex");
        }

        private HttpPost createSipZipUploadRequest(File file, ProgressListener progressListener) {
            String requestUrl = String.format(
                    "%s/sip-app/%s/%s",
                    narthexCredentials.narthexUrl(),
                    datasetName.trim(),
                    file.getName()
            );
            feedback().info("POST " + requestUrl);
            HttpPost post = new HttpPost(requestUrl);
            FileEntity fileEntity = new FileEntity(file, progressListener);
            post.setEntity(fileEntity);
            return post;
        }

    }

    /**
     * Uploader that sends processed RDF/XML file to Narthex's upload-processed endpoint.
     * This allows bypassing Narthex's processing step when the same processing has been done locally.
     */
    private class NarthexProcessedUploader implements Work.LongTermWork {
        private final File processedFile;
        private final String datasetSpec;
        private final int validCount;
        private final int invalidCount;
        private ProgressListener progressListener;
        private Swing finished;

        NarthexProcessedUploader(File processedFile, String datasetSpec, int validCount, int invalidCount, Swing finished) {
            this.processedFile = processedFile;
            this.datasetSpec = datasetSpec;
            this.validCount = validCount;
            this.invalidCount = invalidCount;
            this.finished = finished;
        }

        @Override
        public void run() {
            boolean success = false;
            try {
                String requestUrl = String.format(
                        "%s/app/dataset/%s/upload-processed",
                        narthexCredentials.narthexUrl(),
                        datasetSpec.trim()
                );
                feedback().info("Uploading processed file to Narthex: " + requestUrl);

                HttpPost post = new HttpPost(requestUrl);

                // Build multipart request with file and counts
                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                builder.addPart("file", new FileBody(processedFile, ContentType.APPLICATION_OCTET_STREAM));
                builder.addPart("validCount", new StringBody(String.valueOf(validCount), ContentType.TEXT_PLAIN));
                builder.addPart("invalidCount", new StringBody(String.valueOf(invalidCount), ContentType.TEXT_PLAIN));
                builder.addPart("source", new StringBody("sip-creator", ContentType.TEXT_PLAIN));

                post.setEntity(builder.build());

                HttpResponse response = httpClient.execute(post, httpClientContext);
                Code code = Code.from(response);

                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    switch (code) {
                        case OK:
                            feedback().info(String.format(
                                    "Successfully uploaded processed file to Narthex: %d valid, %d invalid",
                                    validCount, invalidCount
                            ));
                            success = true;
                            break;
                        case UNAUTHORIZED:
                            loggedIn = false;
                            feedback().alert("Unauthorized: Please check your Narthex credentials");
                            break;
                        default:
                            String responseBody = EntityUtils.toString(entity);
                            feedback().alert(String.format(
                                    "Failed to upload processed file. Status: %d. Response: %s",
                                    response.getStatusLine().getStatusCode(),
                                    responseBody
                            ));
                            break;
                    }
                    EntityUtils.consume(entity);
                } else {
                    feedback().alert("Empty response from Narthex");
                }
            } catch (Exception e) {
                feedback().alert("Error uploading processed file to Narthex", e);
            } finally {
                if (finished != null) {
                    final boolean finalSuccess = success;
                    Swing.Exec.later(finished);
                }
            }
        }

        @Override
        public Job getJob() {
            return Job.UPLOAD;
        }

        @Override
        public void setProgressListener(ProgressListener progressListener) {
            this.progressListener = progressListener;
            progressListener.setProgressMessage("Uploading processed file to Narthex");
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
        else if (name.endsWith(".zip")) {
            return "application/zip";
        }
        else if (name.endsWith(".xml")) {
            return "text/xml";
        }
        else if (name.endsWith(".int")) {
            return "application/octet-stream";
        }
        else {
            throw new RuntimeException("Cannot determine content type of " + file.getAbsolutePath());
        }
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
            setContentType(deriveContentType(file));
        }

        public boolean isRepeatable() {
            return true;
        }

        @Override
        public long getContentLength() {
            return this.file.length();
        }

        @Override
        public InputStream getContent() throws IOException {
            return new FileInputStream(this.file);
        }

        @Override
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
                        progressListener.setProgress(blocksReported);
                    }
                }
                outputStream.flush();
            }
            catch (CancelException e) {
                outputStream.close();
            }
            finally {
                inputStream.close();
            }
        }

        @Override
        public boolean isStreaming() {
            return true;
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone();
        }
    }

    private void reportResponse(Code code, StatusLine statusLine) {
        feedback().alert(String.format(
                "Problem communicating with server: %s. Status [%d] %s",
                code.message,
                statusLine.getStatusCode(),
                statusLine.getReasonPhrase()
        ));
    }

    private Feedback feedback() {
        return sipModel.getFeedback();
    }

    @XStreamAlias("sip-zips")
    public static class SipZips {
        @XStreamAsAttribute
        public String sipAppVersion;

        public List<SipEntry> available;

        public List<SipEntry> uploaded;
    }

    @XStreamAlias("sip-zip")
    public static class SipEntry {
        public String dataset;
        public String file;
    }


}
