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

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import eu.delving.XStreamFactory;
import eu.delving.metadata.Hasher;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.Feedback;
import eu.delving.sip.model.SipModel;
import org.apache.amber.oauth2.common.exception.OAuthProblemException;
import org.apache.amber.oauth2.common.exception.OAuthSystemException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;

/**
 * Connect to the culture hub using HTTP for fetching, uploading, and unlocking datasets.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class NetworkClient {
    private static final int BLOCK_SIZE = 1024;
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

    public NetworkClient(SipModel sipModel, HttpClient httpClient) {
        this.sipModel = sipModel;
        this.httpClient = httpClient;
        oauthClient = new OAuthClient(
                httpClient,
                sipModel.getStorage().getHostPort(),
                sipModel.getStorage().getUsername(),
                new OAuthClient.PasswordRequest() {
                    @Override
                    public String getPassword() {
                        return feedback().getHubPassword();
                    }
                }
        );

    }

    public interface HubListListener {

        void listReceived(List<DataSetEntry> entries);

        void failed(Exception e);
    }

    public void fetchHubDatasetList(HubListListener hubListListener) {
        sipModel.exec(new HubListFetcher(1, hubListListener));
    }

    public interface ReleaseListener {
        void unlockComplete(boolean successful);
    }

    public void unlockHubDataset(DataSet dataSet, ReleaseListener releaseListener) {
        sipModel.exec(new HubRelease(1, dataSet, releaseListener));
    }

    public void downloadHubDataset(String fileName, DataSet dataSet, Swing finished) {
        sipModel.exec(new HubDownloader(1, fileName, dataSet, finished));
    }

    public interface NarthexListListener {

        void listReceived(List<Sip> entries);

        void failed(Exception e);
    }


    public void fetchNarthexSipList(String url, String apiKey, NarthexListListener narthexListListener) {
        sipModel.exec(new NarthexListFetcher(1, narthexListListener, url, apiKey));
    }

    public void downloadNarthexDataset(String fileName, DataSet dataSet, String url, String apiKey, Swing finished) {
        sipModel.exec(new NarthexDatasetDownloader(1, fileName, dataSet, url, apiKey, finished));
    }

    public void uploadNarthex(DataSet dataSet, String url, String apiKey, Swing finished) throws StorageException {
        sipModel.exec(new NarthexUploader(1, dataSet, url, apiKey, finished));
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

    // HUB ============================

    private class HubListFetcher extends Attempt {
        private HubListListener hubListListener;

        public HubListFetcher(int attempt, HubListListener hubListListener) {
            super(attempt);
            this.hubListListener = hubListListener;
        }

        @Override
        public void run() {
            try {
                HttpGet get = createHubListRequest();
                get.setHeader("Accept", "text/xml");
                HttpResponse response = httpClient.execute(get);
                Code code = Code.from(response);
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    switch (code) {
                        case OK:
                            DataSetList dataSetList = (DataSetList) XStreamFactory.getStreamFor(DataSetList.class).fromXML(entity.getContent());
                            hubListListener.listReceived(dataSetList.list);
                            break;
                        case UNAUTHORIZED:
                            if (!reactToUnauthorized(new HubListFetcher(attempt + 1, hubListListener))) {
                                hubListListener.failed(new Exception("Unable to fetch list"));
                            }
                            break;
                        default:
                            reportResponse(code, response.getStatusLine());
                            hubListListener.failed(new Exception(String.format(
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
                hubListListener.failed(e);
            }
            catch (Exception e) {
                feedback().alert("Error fetching list from hub", e);
                hubListListener.failed(e);
            }
        }

        @Override
        public Job getJob() {
            return Job.FETCH_LIST;
        }

        private HttpGet createHubListRequest() throws OAuthSystemException, OAuthProblemException {
            return createHubGet("list");
        }

    }

    private class HubRelease extends Attempt implements Work.DataSetWork {
        private DataSet dataSet;
        private ReleaseListener releaseListener;

        public HubRelease(int attempt, DataSet dataSet, ReleaseListener releaseListener) {
            super(attempt);
            this.dataSet = dataSet;
            this.releaseListener = releaseListener;
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
                        releaseListener.unlockComplete(true);
                        break;
                    case UNAUTHORIZED:
                        if (!reactToUnauthorized(new HubRelease(attempt + 1, dataSet, releaseListener))) {
                            releaseListener.unlockComplete(false);
                        }
                        break;
                    default:
                        releaseListener.unlockComplete(false);
                        oauthClient.invalidateTokens();
                        reportResponse(code, response.getStatusLine());
                        break;
                }
            }
            catch (OAuthProblemException e) {
                reportOAuthProblem(e);
                releaseListener.unlockComplete(false);
            }
            catch (Exception e) {
                feedback().alert("Error unlocking dataset", e);
                releaseListener.unlockComplete(false);
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

        private HttpGet createUnlockRequest(DataSet dataSet) throws OAuthSystemException, OAuthProblemException {
            HttpGet get = createHubGet(String.format(
                    "unlock/%s/%s",
                    dataSet.getOrganization(), dataSet.getSpec()
            ));
            get.setHeader("Accept", "text/xml");
            return get;
        }
    }

    private class HubDownloader extends Attempt implements Work.DataSetWork, Work.LongTermWork {
        private final String fileName;
        private final DataSet dataSet;
        private ProgressListener progressListener;
        private Swing finished;

        private HubDownloader(int attempt, String fileName, DataSet dataSet, Swing finished) {
            super(attempt);
            this.fileName = fileName;
            this.dataSet = dataSet;
            this.finished = finished;
        }

        @Override
        public void run() {
            boolean success = false;
            try {
                HttpGet get = createHubDownloadRequest(dataSet);
                HttpResponse response = httpClient.execute(get);
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    Code code = Code.from(response);
                    switch (code) {
                        case OK:
                            dataSet.remove();
                            File sipZipFile = dataSet.sipZipFile(fileName);
                            FileOutputStream output = new FileOutputStream(sipZipFile);
                            progressListener.prepareFor((int)(entity.getContentLength()/BLOCK_SIZE));
                            InputStream input = entity.getContent();
                            byte[] buffer = new byte[BLOCK_SIZE];
                            int count = 0;
                            int bytesRead = 0;
                            while (-1 != (bytesRead = input.read(buffer))) {
                                output.write(buffer, 0, bytesRead);
                                count++;
                                progressListener.setProgress(count);
                            }
                            output.close();
                            dataSet.fromSipZip(sipZipFile, progressListener);
                            success = true;
                            break;
                        case UNAUTHORIZED:
                            if (reactToUnauthorized(new HubDownloader(attempt + 1, fileName, dataSet, finished))) {
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
            progressListener.setProgressMessage("Downloading from the hub.");
        }

        private HttpGet createHubDownloadRequest(DataSet dataSet) throws OAuthSystemException, OAuthProblemException {
            HttpGet get = createHubGet(String.format(
                    "fetch/%s/%s-sip.zip",
                    dataSet.getOrganization(), dataSet.getSpec()
            ));
            get.setHeader("Accept", "application/zip");
            return get;
        }
    }

    // NARTHEX ========================================

    private class NarthexListFetcher extends Attempt {
        private NarthexListListener narthexListListener;
        private String url, apiKey;

        public NarthexListFetcher(int attempt, NarthexListListener narthexListListener, String url, String apiKey) {
            super(attempt);
            this.narthexListListener = narthexListListener;
            this.url = url;
            this.apiKey = apiKey;
        }

        @Override
        public void run() {
            try {
                HttpGet get = createNarthexListRequest();
                get.setHeader("Accept", "text/xml");
                HttpResponse response = httpClient.execute(get);
                Code code = Code.from(response);
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    switch (code) {
                        case OK:
                            SipList sipList = (SipList) XStreamFactory.getStreamFor(SipList.class).fromXML(entity.getContent());
                            narthexListListener.listReceived(sipList.list);
                            break;
                        case UNAUTHORIZED:
                            if (!reactToUnauthorized(new NarthexListFetcher(attempt + 1, narthexListListener, url, apiKey))) {
                                narthexListListener.failed(new Exception("Unable to fetch list"));
                            }
                            break;
                        default:
                            reportResponse(code, response.getStatusLine());
                            narthexListListener.failed(new Exception(String.format(
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
//            catch (OAuthProblemException e) {
//                reportOAuthProblem(e);
//                narthexListListener.failed(e);
//            }
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
            return new HttpGet(String.format(
                    "%s/sip-creator/%s/sip-zip",
                    url, apiKey
            ));
        }
    }

    private class NarthexDatasetDownloader extends Attempt implements Work.DataSetWork, Work.LongTermWork {
        private final String apiKey;
        private final String url;
        private final DataSet dataSet;
        private final String fileName;
        private Swing finished;
        private ProgressListener progressListener;

        private NarthexDatasetDownloader(int attempt, String fileName, DataSet dataSet, String url, String apiKey, Swing finished) {
            super(attempt);
            this.fileName = fileName;
            this.dataSet = dataSet;
            this.url = url;
            this.apiKey = apiKey;
            this.finished = finished;
        }

        @Override
        public void run() {
            boolean success = false;
            try {
                HttpGet get = createSipZipDownloadRequest();
                HttpResponse response = httpClient.execute(get);
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    Code code = Code.from(response);
                    switch (code) {
                        case OK:
                            dataSet.remove();
                            File sipZipFile = dataSet.sipZipFile(fileName);
                            FileOutputStream output = new FileOutputStream(sipZipFile);
                            progressListener.prepareFor((int)(entity.getContentLength()/BLOCK_SIZE));
                            InputStream input = entity.getContent();
                            byte[] buffer = new byte[BLOCK_SIZE];
                            int count = 0;
                            int bytesRead = 0;
                            while (-1 != (bytesRead = input.read(buffer))) {
                                output.write(buffer, 0, bytesRead);
                                count++;
                                progressListener.setProgress(count);
                            }
                            output.close();
                            dataSet.fromSipZip(sipZipFile, progressListener);
                            success = true;
                            break;
                        case UNAUTHORIZED:
                            if (reactToUnauthorized(new NarthexDatasetDownloader(attempt + 1, fileName, dataSet, url, apiKey, finished))) {
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
            progressListener.setProgressMessage("Downloading from the hub.");
        }

        private HttpGet createSipZipDownloadRequest() throws OAuthSystemException, OAuthProblemException {
            return new HttpGet(String.format(
                    "%s/sip-creator/%s/sip-zip/%s",
                    url, apiKey, fileName
            ));
        }
    }

    private class NarthexUploader extends Attempt implements Work.DataSetWork, Work.LongTermWork {
        private final DataSet dataSet;
        private final String url;
        private final String apiKey;
        private ProgressListener progressListener;
        private Swing finished;

        NarthexUploader(int attempt, DataSet dataSet, String url, String apiKey, Swing finished) throws StorageException {
            super(attempt);
            this.dataSet = dataSet;
            this.url = url;
            this.apiKey = apiKey;
            this.finished = finished;
        }

        @Override
        public void run() {
            try {
                for (File file : dataSet.getUploadFiles()) {
                    HttpPost outputPost = createNarthexUploadRequest(file, progressListener);
                    FileEntity fileEntity = (FileEntity) outputPost.getEntity();
                    feedback().info("Uploading Output " + file);
                    HttpResponse outputResponse = httpClient.execute(outputPost);
                    System.out.println(EntityUtils.toString(outputResponse.getEntity())); // otherwise consume!
                    Code code = Code.from(outputResponse);
                    if (code != Code.OK && !fileEntity.abort) {
                        oauthClient.invalidateTokens();
                        reportResponse(Code.from(outputResponse), outputResponse.getStatusLine());
                        return;
                    }
                }
                File sipZip = dataSet.toSipZip();
                feedback().info("Uploading SIP-Zip " + sipZip.getName());
                HttpPost sipZipPost = createSipZipUploadRequest(sipZip, progressListener);
                FileEntity fileEntity = (FileEntity) sipZipPost.getEntity();
                HttpResponse sipZipResponse = httpClient.execute(sipZipPost);
                System.out.println(EntityUtils.toString(sipZipResponse.getEntity())); // otherwise consume!
                Code code = Code.from(sipZipResponse);
                if (code != Code.OK && !fileEntity.abort) {
                    oauthClient.invalidateTokens();
                    reportResponse(Code.from(sipZipResponse), sipZipResponse.getStatusLine());
                }
            }
            catch (OAuthProblemException e) {
                reportOAuthProblem(e);
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
        public DataSet getDataSet() {
            return dataSet;
        }

        @Override
        public void setProgressListener(ProgressListener progressListener) {
            this.progressListener = progressListener;
            progressListener.setProgressMessage("Uploading to the Narthex");
        }

        private HttpPost createNarthexUploadRequest(File file, ProgressListener progressListener) throws OAuthSystemException, OAuthProblemException {
            HttpPost post = new HttpPost(String.format(
                    "%s/sip-creator/%s/upload/%s",
                    url, apiKey, file.getName()
            ));
            FileEntity fileEntity = new FileEntity(file, progressListener);
            post.setEntity(fileEntity);
            return post;
        }

        private HttpPost createSipZipUploadRequest(File file, ProgressListener progressListener) throws OAuthSystemException, OAuthProblemException {
            HttpPost post = new HttpPost(String.format(
                    "%s/sip-creator/%s/sip-zip/%s",
                    url, apiKey, file.getName()
            ));
            FileEntity fileEntity = new FileEntity(file, progressListener);
            post.setEntity(fileEntity);
            return post;
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

    private HttpGet createHubGet(String path) throws OAuthSystemException, OAuthProblemException {
        return new HttpGet(String.format("%s/%s?accessKey=%s", getServerUrl(), path, oauthClient.getToken()));
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

    private String getServerUrl() {
        return String.format("http://%s/api/sip-creator", sipModel.getStorage().getHostPort());
    }

    @XStreamAlias("data-set-list")
    public static class DataSetList {
        @XStreamImplicit
        public List<DataSetEntry> list;

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
        public List<SchemaVersionTag> schemaVersions;

        public String getDirectoryName() {
            return String.format("%s_%s", spec, orgId);
        }

        public int getRecordCount() {
            return recordCount;
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

        public String toString() {
            return String.format("%s <%s>", fullname, email);
        }
    }

    @XStreamAlias("createdBy")
    public static class CreatedBy {
        public String username;
        public String fullname;
        public String email;
    }

    @XStreamAlias("schemaVersion")
    public static class SchemaVersionTag {
        public String prefix;
        public String version;
    }

    @XStreamAlias("sip-list")
    public static class SipList {
        @XStreamImplicit
        public List<Sip> list;
    }

    @XStreamAlias("sip")
    public static class Sip {
        public String file;
        public Facts facts;
    }

    @XStreamAlias("facts")
    public static class Facts {
        public String spec;
        public String name;
        public String dataProvider;
        public String provider;
        public String country;
        public String orgId;
        public String uploadedBy;
        public String uploadedOn;
        public List<SchemaVersionTag> schemaVersions;
    }
}
