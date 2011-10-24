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

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import eu.delving.metadata.Hasher;
import eu.delving.sip.ProgressListener;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.Feedback;
import org.apache.amber.oauth2.common.exception.OAuthProblemException;
import org.apache.amber.oauth2.common.exception.OAuthSystemException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;

/**
 * Connect to the culture hub using HTTP
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class CultureHubClient {
    private static final int CONNECTION_TIMEOUT = 1000 * 60 * 30;
    private static final int BLOCK_SIZE = 4096;
    private static final long MINIMUM_PROGRESS_SIZE = 1024 * 1024;
    private Logger log = Logger.getLogger(getClass());
    private Context context;
    private HttpClient httpClient;

    public interface Context {
        String getUser();

        String getServerUrl();

        String getAccessToken() throws OAuthSystemException, OAuthProblemException;

        void invalidateTokens();

        void dataSetCreated(DataSet dataSet);

        Feedback getFeedback();
    }

    public enum Code {
        OK(SC_OK, "All is well"),
        NOT_FOUND(SC_NOT_FOUND, "Resource not found"),
        UNAUTHORIZED(SC_UNAUTHORIZED, "Not authorized"),
        SYSTEM_ERROR(SC_INTERNAL_SERVER_ERROR, "Server system error"),
        UNKNOWN_RESPONSE(-1, "Unknown response");

        private int httpCode;
        private String say;

        Code(int httpCode, String say) {
            this.httpCode = httpCode;
            this.say = say;
        }

        void notifyUser(Context context) {
            context.getFeedback().alert("Problem communicating with hub: " + say);
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
        HttpParams timeoutParams = new BasicHttpParams();
        HttpConnectionParams.setSoTimeout(timeoutParams, CONNECTION_TIMEOUT);
        HttpConnectionParams.setConnectionTimeout(timeoutParams, CONNECTION_TIMEOUT);
        httpClient = new DefaultHttpClient(timeoutParams);
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public interface ListReceiveListener {

        void listReceived(List<DataSetEntry> entries);

        void failed(Exception e);
    }

    public void fetchDataSetList(ListReceiveListener listReceiveListener) {
        Exec.work(new ListFetcher(listReceiveListener));
    }

    public interface UnlockListener {
        void unlockComplete(boolean successful);
    }

    public void unlockDataSet(DataSet dataSet, UnlockListener unlockListener) {
        Exec.work(new Unlocker(dataSet, unlockListener));
    }

    public void downloadDataSet(DataSet dataSet, ProgressListener progressListener) {
        Exec.work(new DataSetDownloader(dataSet, progressListener));
    }

    public interface UploadListener {
        void uploadRefused(File file);

        void uploadStarted(File file);

        void uploadFinished(File file);

        ProgressListener getProgressListener();

        void finished();
    }

    public void uploadFiles(DataSet dataSet, UploadListener uploadListener) throws StorageException {
        Exec.work(new FileUploader(dataSet, uploadListener));
    }

    private class ListFetcher implements Runnable {
        private ListReceiveListener listReceiveListener;

        public ListFetcher(ListReceiveListener listReceiveListener) {
            this.listReceiveListener = listReceiveListener;
        }

        @Override
        public void run() {
            try {
                String url = String.format(
                        "%s/list?accessKey=%s",
                        context.getServerUrl(),
                        context.getAccessToken()
                );
                log.info("requesting list: " + url);
                say("Requesting data set list from culture hub");
                HttpGet get = new HttpGet(url);
                get.setHeader("Accept", "text/xml");
                HttpResponse response = httpClient.execute(get);
                Code code = Code.from(response);
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    if (code == Code.OK) {
                        DataSetList dataSetList = (DataSetList) listStream().fromXML(entity.getContent());
                        EntityUtils.consume(entity);
                        say("List received");
                        listReceiveListener.listReceived(dataSetList.list);
                    }
                    else {
                        code.notifyUser(context);
                        context.invalidateTokens();
                    }
                }
                else {
                    throw new IOException("Response was empty");
                }
            }
            catch (OAuthProblemException e) {
                reportOAuthProblem(e);
                listReceiveListener.failed(e);
            }
            catch (Exception e) {
                log.error("Unable to fetch list", e);
                context.getFeedback().alert(String.format("Error fetching list from hub: %s", e.getMessage()));
                listReceiveListener.failed(e);
            }
        }
    }

    private class Unlocker implements Runnable {
        private DataSet dataSet;
        private UnlockListener unlockListener;

        public Unlocker(DataSet dataSet, UnlockListener unlockListener) {
            this.dataSet = dataSet;
            this.unlockListener = unlockListener;
        }

        @Override
        public void run() {
            try {
                String url = String.format(
                        "%s/unlock/%s?accessKey=%s",
                        context.getServerUrl(),
                        dataSet.getSpec(),
                        context.getAccessToken()
                );
                HttpGet get = new HttpGet(url);
                get.setHeader("Accept", "text/xml");
                say("Unlocking data set " + dataSet.getSpec());
                HttpResponse response = httpClient.execute(get);
                EntityUtils.consume(response.getEntity());
                Code code = Code.from(response);
                if (code == Code.OK) {
                    unlockListener.unlockComplete(true);
                }
                else {
                    unlockListener.unlockComplete(false);
                    context.invalidateTokens();
                    Code.from(response).notifyUser(context);
                }
            }
            catch (OAuthProblemException e) {
                reportOAuthProblem(e);
            }
            catch (Exception e) {
                log.error("Unable to unlock dataset", e);
                context.getFeedback().alert(String.format("Error unlocking dataset server: %s", e.getMessage()));
                unlockListener.unlockComplete(false);
            }
        }
    }

    private class DataSetDownloader implements Runnable {
        private DataSet dataSet;
        private ProgressListener progressListener;

        private DataSetDownloader(DataSet dataSet, ProgressListener progressListener) {
            this.dataSet = dataSet;
            this.progressListener = progressListener;
        }

        @Override
        public void run() {
            boolean success = false;
            try {
                HttpGet get = new HttpGet(String.format(
                        "%s/fetch/%s-sip.zip?accessKey=%s",
                        context.getServerUrl(),
                        dataSet.getSpec(),
                        context.getAccessToken()
                ));
                get.setHeader("Accept", "application/zip");
                say("Downloading SIP for data set " + dataSet.getSpec());
                HttpResponse response = httpClient.execute(get);
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    Code code = Code.from(response);
                    if (code == Code.OK) {
                        dataSet.fromSipZip(entity.getContent(), entity.getContentLength(), progressListener);
                        success = true;
                        context.dataSetCreated(dataSet);
                        say(String.format("Local data set %s created in workspace", dataSet.getSpec()));
                    }
                    else {
                        context.invalidateTokens();
                        Code.from(response).notifyUser(context);
                    }
                }
                else {
                    throw new IOException("Empty entity");
                }
            }
            catch (OAuthProblemException e) {
                reportOAuthProblem(e);
            }
            catch (Exception e) {
                log.warn("Unable to download data set", e);
                context.getFeedback().alert("Unable to download data set"); // todo: tell them why
            }
            finally {
                if (!success) {
                    try {
                        dataSet.remove();
                    }
                    catch (StorageException e1) {
                        context.getFeedback().alert("Unable to remove local data set");
                    }
                }
                progressListener.finished(success);
            }
        }
    }

    public class FileUploader implements Runnable {
        private DataSet dataSet;
        private List<File> uploadFiles;
        private UploadListener uploadListener;

        public FileUploader(DataSet dataSet, UploadListener uploadListener) throws StorageException {
            this.dataSet = dataSet;
            this.uploadFiles = dataSet.getUploadFiles();
            this.uploadListener = uploadListener;
        }

        @Override
        public void run() {
            try {
                HttpPost listRequest = new HttpPost(createListRequestUrl());
                listRequest.setEntity(createListEntity());
                listRequest.setHeader("Accept", "text/plain");
                HttpResponse listResponse = httpClient.execute(listRequest);
                HttpEntity listEntity = listResponse.getEntity();
                if (listEntity != null) {
                    Code code = Code.from(listResponse);
                    if (code == Code.OK) {
                        String listString = EntityUtils.toString(listEntity);
                        Set<String> requestedFiles = new TreeSet<String>(Arrays.asList(listString.split("\n")));
                        Iterator<File> walk = uploadFiles.iterator();
                        while (walk.hasNext()) {
                            File file = walk.next();
                            if (!requestedFiles.contains(file.getName())) {
                                log.info(String.format("Hub does not want %s", file.getName()));
                                uploadListener.uploadRefused(file);
                                walk.remove();
                            }
                        }
                        EntityUtils.consume(listResponse.getEntity());
                        for (File file : uploadFiles) {
                            log.info("Uploading " + file);
                            HttpPost upload = new HttpPost(createFileRequestUrl(file));
                            upload.setEntity(new FileEntity(file, uploadListener));
                            HttpResponse uploadResponse = httpClient.execute(upload);
                            EntityUtils.consume(uploadResponse.getEntity());
                            code = Code.from(uploadResponse);
                            if (code != Code.OK) {
                                context.invalidateTokens();
                                Code.from(uploadResponse).notifyUser(context);
                                return;
                            }
                        }
                    }
                    else {
                        context.invalidateTokens();
                        code.notifyUser(context);
                    }
                }
                else {
                    throw new IOException("Empty entity");
                }
            }
            catch (OAuthProblemException e) {
                reportOAuthProblem(e);
            }
            catch (Exception e) {
                log.error("Error while connecting", e);
                context.getFeedback().alert("Authorization system problem: " + e.getMessage());
            }
            finally {
                uploadListener.finished();
            }
        }

        private HttpEntity createListEntity() throws UnsupportedEncodingException {
            StringBuilder fileList = new StringBuilder();
            for (File file : uploadFiles) {
                fileList.append(file.getName()).append("\n");
            }
            return new StringEntity(fileList.toString());
        }

        private String createListRequestUrl() throws OAuthSystemException, OAuthProblemException {
            return String.format(
                    "%s/submit/%s?accessKey=%s",
                    context.getServerUrl(),
                    dataSet.getSpec(),
                    context.getAccessToken()
            );
        }

        private String createFileRequestUrl(File file) throws OAuthSystemException, OAuthProblemException {
            return String.format(
                    "%s/submit/%s/%s?accessKey=%s",
                    context.getServerUrl(),
                    dataSet.getSpec(),
                    file.getName(),
                    context.getAccessToken()
            );
        }
    }

    private void reportOAuthProblem(OAuthProblemException e) {
        OAuthClient.Problem problem = OAuthClient.getProblem(e);
        switch (problem) {
            case EXPIRED_TOKEN:
                context.getFeedback().alert(String.format("Expired token for user %s, please try again", context.getUser()));
                break;
            case INVALID_GRANT:
                context.getFeedback().alert(String.format("Invalid password for user %s", context.getUser()));
                break;
            default:
                context.getFeedback().alert("Authorization problem: " + problem);
                break;
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
        else if (name.endsWith(".int")) {
            return "application/octet-stream";
        }
        else {
            throw new RuntimeException("Cannot determine content type of " + file.getAbsolutePath());
        }
    }

    private void say(String message) {
        context.getFeedback().say(message);
    }

    private static class FileEntity extends AbstractHttpEntity implements Cloneable {
        private final File file;
        private final UploadListener uploadListener;
        private long bytesSent;
        private int blocksReported;
        private boolean abort = false;

        public FileEntity(File file, UploadListener uploadListener) {
            this.file = file;
            this.uploadListener = uploadListener;
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
            uploadListener.uploadStarted(file);
            ProgressListener progress = getContentLength() < MINIMUM_PROGRESS_SIZE ? null : uploadListener.getProgressListener();
            if (progress != null) {
                progress.prepareFor((int) (getContentLength() / BLOCK_SIZE));
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
                        if (progress != null && !progress.setProgress(blocksReported)) {
                            abort = true;
                        }
                    }
                }
                outputStream.flush();
            }
            finally {
                inputStream.close();
                if (progress != null) {
                    progress.finished(!abort);
                }
                uploadListener.uploadFinished(file);
            }
        }

        public boolean isStreaming() {
            return false;
        }

        public Object clone() throws CloneNotSupportedException {
            return super.clone();
        }
    }

    private XStream listStream() {
        XStream stream = new XStream(new PureJavaReflectionProvider());
        stream.processAnnotations(DataSetList.class);
        return stream;
    }

    @XStreamAlias("data-set-list")
    public static class DataSetList {
        @XStreamImplicit
        List<DataSetEntry> list;

        public String toString() {
            StringBuilder out = new StringBuilder("data-set-list");
            if (list == null || list.isEmpty()) {
                out.append(" (empty)");
            }
            else {
                out.append('\n');
                for (DataSetEntry entry : list) {
                    out.append('\t');
                    out.append(entry);
                    out.append('\n');
                }
            }
            return out.toString();
        }
    }

    @XStreamAlias("data-set")
    public static class DataSetEntry {
        public String spec;
        public String name;
        public String state;
        public int recordCount;
        public Ownership ownership;
        public LockedBy lockedBy;

        public String toString() {
            return "data-set spec=" + spec;
        }
    }

    @XStreamAlias("ownership")
    public static class Ownership {
        public String username;
        public String fullname;
        public String email;
    }

    @XStreamAlias("lockedBy")
    public static class LockedBy {
        public String username;
        public String fullname;
        public String email;
    }
}
