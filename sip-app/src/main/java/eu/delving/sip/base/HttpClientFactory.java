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

import eu.delving.sip.files.StorageFinder;
import org.apache.http.HttpHost;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.params.ConnRouteParams;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import java.io.File;
import java.net.*;
import java.util.List;

import static eu.delving.sip.files.StorageFinder.getHostPort;

/**
 * Create the HttpClient correctly
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class HttpClientFactory {

    public static HttpClient createLinkCheckClient() {
        HttpParams httpParams = createConnectionParams();
        ThreadSafeClientConnManager threaded = new ThreadSafeClientConnManager();
        return new DefaultHttpClient(threaded, httpParams);
    }

    public static HttpClient createHttpClient(File storageDirectory) {
        HttpParams httpParams = createConnectionParams();
        if (!StorageFinder.isStandalone(storageDirectory)) {
            String serverUrl = String.format("http://%s", getHostPort(storageDirectory));
            handleProxy(serverUrl, httpParams);
        }
        ThreadSafeClientConnManager threaded = new ThreadSafeClientConnManager();
        return new DefaultHttpClient(threaded, httpParams);
    }

    private static HttpParams createConnectionParams() {
        final int CONNECTION_TIMEOUT = 1000 * 60 * 30;
        HttpParams httpParams = new BasicHttpParams();
        HttpConnectionParams.setSoTimeout(httpParams, CONNECTION_TIMEOUT);
        HttpConnectionParams.setConnectionTimeout(httpParams, CONNECTION_TIMEOUT);
        return httpParams;
    }

    private static void handleProxy(String serverUrl, HttpParams httpParams) {
        try {
            List<Proxy> proxies = ProxySelector.getDefault().select(new URI(serverUrl));
            for (Proxy proxy : proxies) {
                if (proxy.type() != Proxy.Type.HTTP) continue;
                InetSocketAddress addr = (InetSocketAddress) proxy.address();
                String host = addr.getHostName();
                int port = addr.getPort();
                HttpHost httpHost = new HttpHost(host, port);
                ConnRouteParams.setDefaultProxy(httpParams, httpHost);
            }
        }
        catch (URISyntaxException e) {
            throw new RuntimeException("Bad address: " + serverUrl, e);
        }
    }
}