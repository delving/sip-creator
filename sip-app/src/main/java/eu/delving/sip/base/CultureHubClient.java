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

package eu.delving.sip.base;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import eu.delving.metadata.Hasher;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.Feedback;
import eu.delving.sip.model.SipModel;
import org.apache.amber.oauth2.common.exception.OAuthProblemException;
import org.apache.amber.oauth2.common.exception.OAuthSystemException;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import java.io.*;
import java.util.*;

import static org.apache.http.HttpStatus.*;

/**
 * Connect to the culture hub using HTTP for fetching, uploading, and unlocking datasets.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class CultureHubClient {
    private static final int BLOCK_SIZE = 1024;
    private Logger log = Logger.getLogger(getClass());
    private SipModel sipModel;
    private HttpClient httpClient;
    private OAuthClient oauthClient;

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

    public CultureHubClient(SipModel sipModel, HttpClient httpClient) {
        this.sipModel = sipModel;
        this.httpClient = httpClient;
        oauthClient = new OAuthClient(
                httpClient,
                sipModel.getStorage().getHostPort(),
                sipModel.getStorage().getUsername(),
                new OAuthClient.PasswordRequest() {
                    @Override
                    public String getPassword() {
                        return feedback().getPassword();
                    }
                }
        );

    }

    public interface ListReceiveListener {

        void listReceived(List<DataSetEntry> entries);

        void failed(Exception e);
    }

    public void fetchDataSetList(ListReceiveListener listReceiveListener) {
        sipModel.exec(new ListFetcher(1, listReceiveListener));
    }

    public interface UnlockListener {
        void unlockComplete(boolean successful);
    }

    public void unlockDataSet(DataSet dataSet, UnlockListener unlockListener) {
        sipModel.exec(new Unlocker(1, dataSet, unlockListener));
    }

    public void downloadDataSet(DataSet dataSet, Swing finished) {
        sipModel.exec(new DataSetDownloader(1, dataSet, finished));
    }

    public void uploadFiles(DataSet dataSet, Swing finished) throws StorageException {
        sipModel.exec(new DataUploader(1, dataSet, finished));
    }

    public void uploadMedia(DataSet dataSet, Swing finished) throws StorageException {
        sipModel.exec(new MediaUploader(1, dataSet, finished));
    }

    private abstract class Attempt implements Work {
        protected int attempt;

        protected Attempt(int attempt) {
            this.attempt = attempt;
        }

        private boolean shouldRetry() {
            return attempt <= 3;
        }

        protected boolean reactToUnauthorized(Work retry) {
            if (shouldRetry()) {
                feedback().alert("Authorization failed, retrying...");
                oauthClient.invalidateTokens();
                sipModel.exec(retry);
                return true;
            }
            else {
                return false;
            }
        }
    }

    private class ListFetcher extends Attempt {
        private ListReceiveListener listReceiveListener;

        public ListFetcher(int attempt, ListReceiveListener listReceiveListener) {
            super(attempt);
            this.listReceiveListener = listReceiveListener;
        }

        @Override
        public void run() {
            try {
                HttpGet get = createListRequest();
                get.setHeader("Accept", "text/xml");
                HttpResponse response = httpClient.execute(get);
                Code code = Code.from(response);
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    switch (code) {
                        case OK:
                            DataSetList dataSetList = (DataSetList) listStream().fromXML(entity.getContent());
                            listReceiveListener.listReceived(dataSetList.list);
                            break;
                        case UNAUTHORIZED:
                            if (!reactToUnauthorized(new ListFetcher(attempt + 1, listReceiveListener))) {
                                listReceiveListener.failed(new Exception("Unable to fetch list"));
                            }
                            break;
                        default:
                            reportResponse(code, response.getStatusLine());
                            listReceiveListener.failed(new Exception(String.format(
                                    "Response was %s. Status: [%d] %s",
                                    code,
                                    response.getStatusLine().getStatusCode(),
                                    response.getStatusLine().getReasonPhrase()
                            )));
                            oauthClient.invalidateTokens();
                            break;
                    }
                    EntityUtils.consume(entity);
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
                feedback().alert(String.format("Error fetching list from hub: %s", e.getMessage()));
                listReceiveListener.failed(e);
            }
        }

        @Override
        public Job getJob() {
            return Job.FETCH_LIST;
        }
    }

    private class Unlocker extends Attempt implements Work.DataSetWork {
        private DataSet dataSet;
        private UnlockListener unlockListener;

        public Unlocker(int attempt, DataSet dataSet, UnlockListener unlockListener) {
            super(attempt);
            this.dataSet = dataSet;
            this.unlockListener = unlockListener;
        }

        @Override
        public void run() {
            try {
                HttpGet get = createUnlockRequest(dataSet);
                HttpResponse response = httpClient.execute(get);
                EntityUtils.consume(response.getEntity());
                Code code = Code.from(response);
                switch (code) {
                    case OK:
                        unlockListener.unlockComplete(true);
                        break;
                    case UNAUTHORIZED:
                        if (!reactToUnauthorized(new Unlocker(attempt + 1, dataSet, unlockListener))) {
                            unlockListener.unlockComplete(false);
                        }
                        break;
                    default:
                        unlockListener.unlockComplete(false);
                        oauthClient.invalidateTokens();
                        reportResponse(code, response.getStatusLine());
                        break;
                }
            }
            catch (OAuthProblemException e) {
                reportOAuthProblem(e);
                unlockListener.unlockComplete(false);
            }
            catch (Exception e) {
                log.error("Unable to unlock dataset", e);
                feedback().alert(String.format("Error unlocking dataset server: %s", e.getMessage()));
                unlockListener.unlockComplete(false);
            }
        }

        @Override
        public Job getJob() {
            return Job.UNLOCK;
        }

        @Override
        public DataSet getDataSet() {
            return dataSet;
        }
    }

    private class DataSetDownloader extends Attempt implements Work.DataSetWork, Work.LongTermWork {
        private DataSet dataSet;
        private ProgressListener progressListener;
        private Swing finished;

        private DataSetDownloader(int attempt, DataSet dataSet, Swing finished) {
            super(attempt);
            this.dataSet = dataSet;
            this.finished = finished;
        }

        @Override
        public void run() {
            boolean success = false;
            try {
                HttpGet get = createDownloadRequest(dataSet);
                HttpResponse response = httpClient.execute(get);
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    Code code = Code.from(response);
                    switch (code) {
                        case OK:
                            dataSet.remove();
                            dataSet.fromSipZip(entity.getContent(), entity.getContentLength(), progressListener);
                            success = true;
                            break;
                        case UNAUTHORIZED:
                            if (reactToUnauthorized(new DataSetDownloader(attempt + 1, dataSet, finished))) {
                                finished = null;
                            }
                            else {
                                success = false;
                            }
                            break;
                        default:
                            oauthClient.invalidateTokens();
                            reportResponse(code, response.getStatusLine());
                            break;
                    }
                    EntityUtils.consume(entity);
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
            progressListener.setProgressMessage(String.format("Downloading the data of '%s' from the culture hub.", dataSet.getSpec()));
        }
    }

    private class DataUploader extends Attempt implements Work.DataSetWork, Work.LongTermWork {
        private DataSet dataSet;
        private List<File> uploadFiles;
        private ProgressListener progressListener;
        private Swing finished;

        DataUploader(int attempt, DataSet dataSet, Swing finished) throws StorageException {
            super(attempt);
            this.dataSet = dataSet;
            this.uploadFiles = dataSet.getUploadFiles();
            this.finished = finished;
        }

        @Override
        public void run() {
            try {
                HttpPost listRequest = createSubmitRequest(dataSet, uploadFiles);
                HttpResponse listResponse = httpClient.execute(listRequest);
                HttpEntity listEntity = listResponse.getEntity();
                if (listEntity != null) {
                    Code code = Code.from(listResponse);
                    switch (code) {
                        case OK:
                            String listString = EntityUtils.toString(listEntity);
                            Set<String> requestedFiles = new TreeSet<String>(Arrays.asList(listString.split("\n")));
                            Iterator<File> walk = uploadFiles.iterator();
                            while (walk.hasNext()) {
                                File file = walk.next();
                                if (!requestedFiles.contains(file.getName())) walk.remove();
                            }
                            for (File file : uploadFiles) {
                                HttpPost upload = createUploadRequest(dataSet, file, progressListener);
                                FileEntity fileEntity = (FileEntity) upload.getEntity();
                                log.info("Uploading " + file);
                                HttpResponse uploadResponse = httpClient.execute(upload);
                                EntityUtils.consume(uploadResponse.getEntity());
                                code = Code.from(uploadResponse);
                                if (code != Code.OK && !fileEntity.abort) {
                                    oauthClient.invalidateTokens();
                                    reportResponse(Code.from(uploadResponse), uploadResponse.getStatusLine());
                                    return;
                                }
                            }
                            break;
                        case UNAUTHORIZED:
                            if (reactToUnauthorized(new DataUploader(attempt + 1, dataSet, finished))) {
                                finished = null;
                            }
                            else {
                                feedback().alert("Unable to complete upload");
                            }
                            break;
                        default:
                            oauthClient.invalidateTokens();
                            reportResponse(code, listResponse.getStatusLine());
                            break;
                    }
                    EntityUtils.consume(listResponse.getEntity());
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
                feedback().alert("Authorization system problem: " + e.getMessage());
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
        public DataSet getDataSet() {
            return dataSet;
        }

        @Override
        public void setProgressListener(ProgressListener progressListener) {
            this.progressListener = progressListener;
            progressListener.setProgressMessage(String.format(
                    "Uploading the data of '%s' to the culture hub",
                    dataSet.getSpec()
            ));
        }
    }

    private class MediaUploader extends Attempt implements Work.DataSetWork, Work.LongTermWork {
        private DataSet dataSet;
        private ProgressListener progressListener;
        private Swing finished;

        MediaUploader(int attempt, DataSet dataSet, Swing finished) throws StorageException {
            super(attempt);
            this.dataSet = dataSet;
            this.finished = finished;
        }

        @Override
        public void run() {
            try {
                FileInputStream inputStream = new FileInputStream(new File(dataSet.getMediaDirectory(), Storage.INDEX_FILE));
                MediaFiles mediaFiles = MediaFiles.read(inputStream);
                inputStream.close();
                HttpPost listRequest = createSubmitRequest(dataSet, mediaFiles);
                HttpResponse listResponse = httpClient.execute(listRequest);
                HttpEntity listEntity = listResponse.getEntity();
                if (listEntity != null) {
                    Code code = Code.from(listResponse);
                    switch (code) {
                        case OK:
                            String listString = EntityUtils.toString(listEntity);
                            Set<String> requestedFiles = new TreeSet<String>(Arrays.asList(listString.split("\n")));
                            List<File> uploadFiles = new ArrayList<File>();
                            for (MediaFiles.MediaFile file : mediaFiles.mediaFiles) {
                                if (!requestedFiles.contains(file.name)) continue;
                                uploadFiles.add(new File(dataSet.getMediaDirectory(), file.name));
                            }
                            for (File file : uploadFiles) {
                                HttpPost upload = createUploadRequest(dataSet, file, progressListener);
                                FileEntity fileEntity = (FileEntity) upload.getEntity();
                                log.info("Uploading media " + file);
                                HttpResponse uploadResponse = httpClient.execute(upload);
                                EntityUtils.consume(uploadResponse.getEntity());
                                code = Code.from(uploadResponse);
                                if (code != Code.OK && !fileEntity.abort) {
                                    oauthClient.invalidateTokens();
                                    reportResponse(Code.from(uploadResponse), uploadResponse.getStatusLine());
                                    return;
                                }
                            }
                            break;
                        case UNAUTHORIZED:
                            if (reactToUnauthorized(new DataUploader(attempt + 1, dataSet, finished))) {
                                finished = null;
                            }
                            else {
                                feedback().alert("Unable to complete upload");
                            }
                            break;
                        default:
                            oauthClient.invalidateTokens();
                            reportResponse(code, listResponse.getStatusLine());
                            break;
                    }
                    EntityUtils.consume(listResponse.getEntity());
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
                feedback().alert("Authorization system problem: " + e.getMessage());
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
        public DataSet getDataSet() {
            return dataSet;
        }

        @Override
        public void setProgressListener(ProgressListener progressListener) {
            this.progressListener = progressListener;
            progressListener.setProgressMessage(String.format(
                    "Uploading the data of '%s' to the culture hub",
                    dataSet.getSpec()
            ));
        }
    }

    private void reportOAuthProblem(OAuthProblemException e) {
        OAuthClient.Problem problem = OAuthClient.getProblem(e);
        switch (problem) {
            case EXPIRED_TOKEN:
                feedback().alert(String.format("Expired token for user %s, please try again", sipModel.getStorage().getUsername()));
                break;
            case INVALID_GRANT:
                feedback().alert(String.format("Invalid password for user %s", sipModel.getStorage().getUsername()));
                break;
            default:
                feedback().alert("Authorization problem: " + e);
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
            if (progressListener != null) progressListener.prepareFor((int) (getContentLength() / BLOCK_SIZE));
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
                        if (progressListener != null && !progressListener.setProgress(blocksReported)) abort = true;
                    }
                }
                outputStream.flush();
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

    private XStream listStream() {
        XStream stream = new XStream(new PureJavaReflectionProvider());
        stream.processAnnotations(DataSetList.class);
        return stream;
    }

    private HttpGet createListRequest() throws OAuthSystemException, OAuthProblemException {
        return createGet("list");
    }

    private HttpGet createDownloadRequest(DataSet dataSet) throws OAuthSystemException, OAuthProblemException {
        HttpGet get = createGet(String.format(
                "fetch/%s/%s-sip.zip",
                dataSet.getOrganization(), dataSet.getSpec()
        ));
        get.setHeader("Accept", "application/zip");
        return get;
    }

    private HttpPost createSubmitRequest(DataSet dataSet, List<File> uploadFiles) throws OAuthSystemException, OAuthProblemException, UnsupportedEncodingException {
        HttpPost post = createPost(String.format(
                "submit/%s/%s",
                dataSet.getOrganization(), dataSet.getSpec()
        ));
        post.setEntity(createListEntityFiles(uploadFiles));
        post.setHeader("Accept", "text/plain");
        return post;
    }

    private HttpPost createSubmitRequest(DataSet dataSet, MediaFiles mediaFiles) throws OAuthSystemException, OAuthProblemException, UnsupportedEncodingException {
        HttpPost post = createPost(String.format(
                "submit/%s/%s",
                dataSet.getOrganization(), dataSet.getSpec()
        ));
        List<String> names = new ArrayList<String>();
        for (MediaFiles.MediaFile file : mediaFiles.mediaFiles) names.add(file.name);
        post.setEntity(createListEntityStrings(names));
        post.setHeader("Accept", "text/plain");
        return post;
    }

    private HttpPost createUploadRequest(DataSet dataSet, File file, ProgressListener progressListener) throws OAuthSystemException, OAuthProblemException {
        HttpPost post = createPost(String.format(
                "submit/%s/%s/%s",
                dataSet.getOrganization(), dataSet.getSpec(), file.getName()
        ));
        FileEntity fileEntity = new FileEntity(file, progressListener);
        post.setEntity(fileEntity);
        return post;
    }

    private HttpGet createUnlockRequest(DataSet dataSet) throws OAuthSystemException, OAuthProblemException {
        HttpGet get = createGet(String.format(
                "unlock/%s/%s",
                dataSet.getOrganization(), dataSet.getSpec()
        ));
        get.setHeader("Accept", "text/xml");
        return get;
    }

    private HttpPost createPost(String path) throws OAuthSystemException, OAuthProblemException {
        return new HttpPost(String.format("%s/%s?accessKey=%s", getServerUrl(), path, oauthClient.getToken()));
    }

    private HttpGet createGet(String path) throws OAuthSystemException, OAuthProblemException {
        return new HttpGet(String.format("%s/%s?accessKey=%s", getServerUrl(), path, oauthClient.getToken()));
    }

    private HttpEntity createListEntityFiles(List<File> uploadFiles) throws UnsupportedEncodingException {
        List<String> names = new ArrayList<String>(uploadFiles.size());
        for (File file : uploadFiles) names.add(file.getName());
        return createListEntityStrings(names);
    }

    private HttpEntity createListEntityStrings(List<String> uploadFiles) throws UnsupportedEncodingException {
        return new StringEntity(StringUtils.join(uploadFiles, '\n'));
    }

    private void reportResponse(Code code, StatusLine statusLine) {
        feedback().alert(String.format(
                "Problem communicating with the CultureHub: %s. Status [%d] %s",
                code.message,
                statusLine.getStatusCode(),
                statusLine.getReasonPhrase()
        ));
    }

    private Feedback feedback() {
        return sipModel.getFeedback();
    }

    private String getServerUrl() {
        return String.format("http://%s/api/sip-creator", sipModel.getStorage().getHostPort());
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
        public String orgId;
        public String state;
        public int recordCount;
        public LockedBy lockedBy;
        public CreatedBy createdBy;

        public String getDirectoryName() {
            return String.format("%s_%s", spec, orgId);
        }

        public int getRecordCount() {
            return recordCount;
        }

        public LockedBy getLockedBy() {
            return lockedBy;
        }

        public CreatedBy getCreatedBy() {
            return createdBy;
        }

        public String toString() {
            return "data-set spec=" + spec;
        }
    }

    @XStreamAlias("lockedBy")
    public static class LockedBy {
        public String username;
        public String fullname;
        public String email;

        public String getUsername() {
            return username;
        }

        public String getFullname() {
            return fullname;
        }

        public String getEmail() {
            return email;
        }

        public String toString() {
            return String.format("%s <%s>", fullname, email);
        }
    }

    @XStreamAlias("createdBy")
    public static class CreatedBy {
        public String username;
        public String fullname;
        public String email;

        public String getUsername() {
            return username;
        }

        public String getFullname() {
            return fullname;
        }

        public String getEmail() {
            return email;
        }
    }
}
