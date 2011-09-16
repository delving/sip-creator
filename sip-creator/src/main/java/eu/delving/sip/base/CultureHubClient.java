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
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
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
    private static final int BLOCK_SIZE = 4096;
    private static final long MINIMUM_PROGRESS_SIZE = 1024*1024;
    private Logger log = Logger.getLogger(getClass());
    private Context context;
    private HttpClient httpClient = new DefaultHttpClient();


    public interface Context {
        String getServerUrl();

        String getAccessToken() throws ClientException;

        void invalidateTokens();

        void dataSetCreated(DataSet dataSet);

        void tellUser(String message);

    }

    public enum Code {
        OK(SC_OK),
        NOT_FOUND(SC_NOT_FOUND),
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

        void uploadEnded(File file);

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
            HttpEntity entity = null;
            try {
                String url = String.format(
                        "%s/list?accessKey=%s",
                        context.getServerUrl(),
                        context.getAccessToken()
                );
                log.info("requesting list: " + url);
                HttpGet get = new HttpGet(url);
                get.setHeader("Accept", "text/xml");
                HttpResponse response = httpClient.execute(get);
                switch (Code.from(response)) {
                    case OK:
                        entity = response.getEntity();
                        DataSetList dataSetList = (DataSetList) listStream().fromXML(entity.getContent());
                        log.info("list received:\n" + dataSetList);
                        listReceiveListener.listReceived(dataSetList.list);
                        break;
                    case UNAUTHORIZED:
                        context.invalidateTokens();
                        notifyUser("Authorization failure, please try again");
                        log.error("Authorization failure, please try again");
                        break;
                    case NOT_FOUND:
                    case SYSTEM_ERROR:
                    case UNKNOWN_RESPONSE:
                        log.warn("Unable to fetch data set list. HTTP response " + response.getStatusLine().getReasonPhrase());
                        context.tellUser(String.format("Error fetching list from server:<br><br>%s", response.getStatusLine().getReasonPhrase()));
                        break;
                }
            }
            catch (Exception e) {
                log.error("Unable to fetch list", e);
                context.tellUser(String.format("Error fetching list from server:<br><br>%s", e.getMessage()));
                listReceiveListener.failed(e);
            }
            finally {
                if (null != entity) {
                    try {
                        entity.consumeContent();
                    }
                    catch (IOException e) {
                        log.warn(String.format("Error consuming entity: %s", e.getMessage()));
                    }
                }
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
                HttpResponse response = httpClient.execute(get);
                response.getEntity().consumeContent();
                switch (Code.from(response)) {
                    case OK:
                        unlockListener.unlockComplete(true);
                        break;
                    case UNAUTHORIZED:
                        context.invalidateTokens();
                        notifyUser("Authorization failure, please try again");
                        log.error("Authorization failure, please try again");
                        break;
                    case NOT_FOUND:
                        notifyUser("Unlocking failure");
                        log.error("Unlocking failure");
                        unlockListener.unlockComplete(false);
                        break;
                    case SYSTEM_ERROR:
                    case UNKNOWN_RESPONSE:
                        log.warn("Unable to unlock dataset. HTTP response " + response.getStatusLine().getReasonPhrase());
                        context.tellUser(String.format("Error unlocking dataset:<br><br>%s", response.getStatusLine().getReasonPhrase()));
                        break;
                }
            }
            catch (Exception e) {
                log.error("Unable to unlock dataset", e);
                context.tellUser(String.format("Error unlocking dataset server:<br><br>%s", e.getMessage()));
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
            HttpEntity entity = null;
            try {
                HttpGet get = new HttpGet(String.format(
                        "%s/fetch/%s-sip.zip?accessKey=%s",
                        context.getServerUrl(),
                        dataSet.getSpec(),
                        context.getAccessToken()
                ));
                get.setHeader("Accept", "application/zip");
                HttpResponse response = httpClient.execute(get);
                entity = response.getEntity();
                switch (Code.from(response)) {
                    case OK:
                        dataSet.fromSipZip(entity.getContent(), entity.getContentLength(), progressListener);
                        success = true;
                        context.dataSetCreated(dataSet);
                        break;
                    case UNAUTHORIZED:
                        context.invalidateTokens();
                        notifyUser("Authorization failure, please try again");
                        break;
                    case SYSTEM_ERROR:
                    case UNKNOWN_RESPONSE:
                        log.warn("Unable to download source. HTTP response " + response.getStatusLine().getReasonPhrase());
                        context.tellUser("Unable to download data set"); // todo: tell them why
                        break;
                }
            }
            catch (Exception e) {
                log.warn("Unable to download data set", e);
                context.tellUser("Unable to download data set"); // todo: tell them why
            }
            finally {
                if (entity != null) {
                    try {
                        entity.consumeContent();
                    }
                    catch (IOException e) {
                        log.error("Cannot consume entity content", e);
                    }
                }
                if (!success) {
                    try {
                        dataSet.remove();
                    }
                    catch (StorageException e1) {
                        context.tellUser("Unable to remove local data set");
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
                HttpPost post = new HttpPost(createListRequestUrl());
                post.setEntity(createListEntity());
                post.setHeader("Accept", "text/plain");
                HttpResponse response = httpClient.execute(post);
                switch (Code.from(response)) {
                    case OK:
                        HttpEntity entity = response.getEntity();
                        String listString = EntityUtils.toString(entity);
                        Set<String> requestedFiles = new TreeSet<String>(Arrays.asList(listString.split("\n")));
                        entity.consumeContent();
                        Iterator<File> walk = uploadFiles.iterator();
                        while (walk.hasNext()) {
                            File file = walk.next();
                            if (!requestedFiles.contains(file.getName())) {
                                log.info(String.format("Hub does not want %s", file.getName()));
                                uploadListener.uploadRefused(file);
                                walk.remove();
                            }
                        }
                        break;
                    case UNAUTHORIZED:
                        context.invalidateTokens();
                        notifyUser("Authorization failure, please try again");
                        break;
                    case SYSTEM_ERROR:
                    case UNKNOWN_RESPONSE:
                        throw new IOException("Unable to fetch file list, response: " + Code.from(response));
                }
                for (File file : uploadFiles) {
                    log.info("Uploading " + file);
                    post = new HttpPost(createFileRequestUrl(file));
                    post.setEntity(new FileEntity(file, uploadListener));
                    response = httpClient.execute(post);
                    response.getEntity().consumeContent();
                    switch (Code.from(response)) {
                        case OK:
                            break;
                        case UNAUTHORIZED:
                            context.invalidateTokens();
                            notifyUser("Authorization failure, please try again");
                            break;
                        case SYSTEM_ERROR:
                        case UNKNOWN_RESPONSE:
                            throw new IOException(String.format("Unable to upload file %s, response: %s", file.getName(), Code.from(response)));
                    }
                }
                uploadListener.finished();
            }
            catch (IOException e) {
                log.error("Error while connecting", e);
                notifyUser(e.getMessage());
            }
            catch (ClientException e) {
                log.error("Error ");
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

        private String createListRequestUrl() throws ClientException {
            return String.format(
                    "%s/submit/%s?accessKey=%s",
                    context.getServerUrl(),
                    dataSet.getSpec(),
                    context.getAccessToken()
            );
        }

        private String createFileRequestUrl(File file) throws ClientException {
            return String.format(
                    "%s/submit/%s/%s?accessKey=%s",
                    context.getServerUrl(),
                    dataSet.getSpec(),
                    file.getName(),
                    context.getAccessToken()
            );
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

    private void notifyUser(final String message) {
        log.warn("Problem communicating with CultureHub: " + message);
        context.tellUser("<html>Sorry, there was a problem communicating with Repository<br>" + message);
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
            ProgressListener progress = getContentLength() < MINIMUM_PROGRESS_SIZE ? null : uploadListener.getProgressListener();
            if (progress == null) {
                uploadListener.uploadStarted(file);
            }
            else {
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
                if (progress == null) {
                    uploadListener.uploadEnded(file);
                }
                else {
                    progress.finished(!abort);
                }
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
