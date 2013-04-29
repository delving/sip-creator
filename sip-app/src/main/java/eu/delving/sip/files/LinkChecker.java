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
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;

import java.io.IOException;

/**
 * Check links and use MapDB to maintain link checking results
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class LinkChecker {
    private static Logger log = Logger.getLogger(LinkChecker.class);
    private final HttpClient httpClient;
    private HTreeMap<String, LinkCheck> map;

    public LinkChecker(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.map = DBMaker.newTempHashMap();
    }

    public HTreeMap<String, LinkCheck> getMap() {
        return map;
    }

    public boolean contains(String url) {
        return map.containsKey(url);
    }

    public LinkCheck lookup(String url) {
        return map.get(url);
    }

    public LinkCheck request(String url, String spec, String orgId, String localId) throws IOException {
        LinkCheck linkCheck = linkCheckRequest(url);
        linkCheck.spec = spec;
        linkCheck.orgId = orgId;
        linkCheck.localId = localId;
        log.info(String.format("Found %s by requesting: %s", url, linkCheck));
        map.put(url, linkCheck);
        return linkCheck;
    }

    private LinkCheck linkCheckRequest(String url) throws IOException {
        HttpHead head = new HttpHead(url);
        HttpResponse response = httpClient.execute(head);
        StatusLine status = response.getStatusLine();
        if (status.getStatusCode() == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
            HttpGet get = new HttpGet(url);
            response = httpClient.execute(get);
            status = response.getStatusLine();
        }
        LinkCheck linkCheck = new LinkCheck();
        linkCheck.httpStatus = status.getStatusCode();
        linkCheck.time = System.currentTimeMillis();
        Header contentType = response.getLastHeader("Content-Type");
        linkCheck.mimeType = contentType == null ? null : contentType.getValue();
        Header contentLength = response.getLastHeader("Content-Length");
        linkCheck.fileSize = contentLength == null ? -1 : Integer.parseInt(contentLength.getValue());
        EntityUtils.consume(response.getEntity());
        return linkCheck;
    }
}
