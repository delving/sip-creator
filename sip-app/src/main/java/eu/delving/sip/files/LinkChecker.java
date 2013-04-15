/*
 * Copyright 2011, 2012 Delving BV
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.sip.files;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

/**
 * Check links and use MapDB to maintain link checking results
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class LinkChecker {
    private static Logger log = Logger.getLogger(LinkChecker.class);
    private static final String DB_NAME = "LinkCheckMap";
//    private static final int NODE_SIZE = 126; // seems to be the max
//    private static final boolean VALUES_STORED_OUTSIDE_NODES = true;
//    private static final boolean KEEP_COUNTER = false;
    private HttpClient httpClient;
    private DB db;
    private BTreeMap<String, LinkCheck> map;

    public LinkChecker(HttpClient httpClient, File file) {
        this.httpClient = httpClient;
        this.db = DBMaker.newFileDB(file).make();
        this.map = db.getTreeMap(DB_NAME);
    }

    public LinkCheck get(String url) throws IOException {
        LinkCheck linkCheck = map.get(url);
        if (linkCheck == null) {
//            for (String key : map.keySet()) {
//                log.warn(key);
//            }
            linkCheck = linkCheckRequest(url);
            log.info(String.format("Found %s by requesting: %s", url, linkCheck));
            map.put(url, linkCheck);
            db.commit();
        }
        else {
            log.info(String.format("Found %s in map: %s", url, linkCheck));
        }
        return linkCheck;
    }

    public void close() {
        db.commit();
        log.info("Committed");
        db.close();
    }

    private LinkCheck linkCheckRequest(String url) throws IOException {
        HttpHead head = new HttpHead(url);
        HttpResponse response = httpClient.execute(head);
        StatusLine status = response.getStatusLine();
        LinkCheck linkCheck = new LinkCheck();
        linkCheck.httpStatus = status.getStatusCode();
        linkCheck.reason = status.getReasonPhrase();
        linkCheck.time = System.currentTimeMillis();
        Header contentType = response.getLastHeader("Content-Type");
        linkCheck.mimeType = contentType == null ? null : contentType.getValue();
        Header contentLength = response.getLastHeader("Content-Length");
        linkCheck.fileSize = contentLength == null ? -1 : Integer.parseInt(contentLength.getValue());
        EntityUtils.consume(response.getEntity());
        return linkCheck;
    }

//    public static class LinkCheckSerializer implements Serializable, Serializer<LinkCheck> {
//        @Override
//        public void serialize(DataOutput dataOutput, LinkCheck linkCheck) throws IOException {
//            dataOutput.writeInt(linkCheck.httpStatus);
//            dataOutput.writeUTF(linkCheck.reason);
//            dataOutput.writeLong(linkCheck.time);
//            dataOutput.writeUTF(linkCheck.mimeType);
//            dataOutput.writeLong(linkCheck.fileSize);
//        }
//
//        @Override
//        public LinkCheck deserialize(DataInput dataInput, int i) throws IOException {
//            LinkCheck linkCheck = new LinkCheck();
//            linkCheck.httpStatus = dataInput.readInt();
//            linkCheck.reason = dataInput.readUTF();
//            linkCheck.time = dataInput.readLong();
//            linkCheck.mimeType = dataInput.readUTF();
//            linkCheck.fileSize = dataInput.readInt();
//            return linkCheck;
//        }
//    }
//
    public static class LinkCheck implements Serializable {
        public int httpStatus;
        public String reason;
        public long time;
        public String mimeType;
        public int fileSize;

        public String toString() {
            return String.format(
                    "%d:%s",
                    httpStatus, reason
            );
        }
    }
}
