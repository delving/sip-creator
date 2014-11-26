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

import org.apache.http.HttpHost;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * Create the HttpClient correctly
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class HttpClientFactory {

    public static HttpClient createLinkCheckClient() {
        return HttpClientBuilder.create().disableAutomaticRetries().build();
    }

    public static HttpClient createHttpClient(String serverUrl) {
        HttpClientBuilder builder = HttpClientBuilder.create().disableAutomaticRetries();
        builder.setConnectionManager(new PoolingHttpClientConnectionManager());
        handleProxy(serverUrl, builder);
        return builder.build();
    }

    private static void handleProxy(String serverUrl, HttpClientBuilder builder) {
        try {
            List<Proxy> proxies = ProxySelector.getDefault().select(new URI(serverUrl));
            for (Proxy proxy : proxies) {
                if (proxy.type() != Proxy.Type.HTTP) continue;
                InetSocketAddress addr = (InetSocketAddress) proxy.address();
                String host = addr.getHostName();
                int port = addr.getPort();
                builder.setProxy(new HttpHost(host, port));
            }
        }
        catch (URISyntaxException e) {
            throw new RuntimeException("Bad address: " + serverUrl, e);
        }
    }
}