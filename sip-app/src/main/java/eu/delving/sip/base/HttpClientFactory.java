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

import org.apache.http.HttpHost;
import org.apache.http.impl.client.HttpClientBuilder;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public class HttpClientFactory {


    public static HttpClientBuilder createHttpClient(String serverUrl) {
        HttpClientBuilder builder = HttpClientBuilder.create().disableAutomaticRetries();
        handleProxy(serverUrl, builder);
        return builder;
    }

    /**
     *
     * @return the decorated builder
     */
    public static HttpClientBuilder handleProxy(String serverUrl, HttpClientBuilder builder) {
        try {
            List<Proxy> proxies = ProxySelector.getDefault().select(new URI(serverUrl));
            for (Proxy proxy : proxies) {
                if (proxy.type() != Proxy.Type.HTTP) continue;
                InetSocketAddress addr = (InetSocketAddress) proxy.address();
                String host = addr.getHostName();
                int port = addr.getPort();
                builder.setProxy(new HttpHost(host, port));
            }
            return builder;
        }
        catch (URISyntaxException e) {
            throw new RuntimeException("Bad address: " + serverUrl, e);
        }
    }
}
