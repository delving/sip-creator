package eu.delving.sip;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.amber.oauth2.client.OAuthClient;
import org.apache.amber.oauth2.client.request.OAuthClientRequest;
import org.apache.amber.oauth2.client.response.OAuthClientResponse;
import org.apache.amber.oauth2.client.response.OAuthClientResponseFactory;
import org.apache.amber.oauth2.client.response.OAuthJSONAccessTokenResponse;
import org.apache.amber.oauth2.common.exception.OAuthProblemException;
import org.apache.amber.oauth2.common.exception.OAuthSystemException;
import org.apache.amber.oauth2.common.message.types.GrantType;
import org.apache.amber.oauth2.common.utils.OAuthUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.Logger;

/**
 * This client uses the "resource owner password credentials" method described at http://tools.ietf.org/html/draft-ietf-oauth-v2-18#section-4.3
 * in order to get access to resources on the CultureHub server.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
public class OAuth2Client {

    private static final String OAUTH2_ENDPOINT_PATH = "/token";

    private Logger log = Logger.getLogger(getClass());

    private Map<String, TokenConnection> connections = new HashMap<String, TokenConnection>();

    private final OAuthClient client = new OAuthClient(new org.apache.amber.oauth2.client.HttpClient() {
                @Override
                public <T extends OAuthClientResponse> T execute(OAuthClientRequest request, Map<String, String> headers, String requestMethod, Class<T> responseClass) throws OAuthSystemException, OAuthProblemException {

                    try {
                        HttpClient httpClient = new DefaultHttpClient();
                        HttpGet get = new HttpGet(request.getLocationUri());
                        HttpResponse httpResponse = httpClient.execute(get);

                        String responseBody = OAuthUtils.saveStreamAsString(httpResponse.getEntity().getContent());
                        return OAuthClientResponseFactory
                                .createCustomResponse(responseBody, httpResponse.getEntity().getContentType().getValue(), httpResponse.getStatusLine().getStatusCode(), responseClass);

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return null;

                }
            });

    public boolean requestAccess(String location, String username, String password) {
        String tokenLocation = toTokenLocation(location);
        try {
            OAuthClientRequest oAuthClientRequest = OAuthClientRequest.tokenLocation(tokenLocation)
                    .setGrantType(GrantType.PASSWORD)
                    .setUsername(username)
                    .setPassword(password)
                    .buildQueryMessage();

            OAuthJSONAccessTokenResponse tokenResponse = client.accessToken(oAuthClientRequest);

            TokenConnection connection = new TokenConnection(tokenResponse.getAccessToken(), tokenResponse.getRefreshToken(), Integer.parseInt(tokenResponse.getExpiresIn()));
            connections.put(connectionKey(username, location), connection);

            return true;

        } catch (OAuthSystemException e) {
            log.error("OAuth2 system error", e);
        } catch (OAuthProblemException e) {
            log.warn("OAuth2 authentication problem", e);
        }
        return false;
    }
    
    private boolean requestRefresh(String connectionKey, String refreshToken) {
        String location = connectionKey.split("#")[1];
        String tokenLocation = toTokenLocation(location);
        try {
            OAuthClientRequest oAuthClientRequest = OAuthClientRequest.tokenLocation(tokenLocation)
                    .setGrantType(GrantType.REFRESH_TOKEN)
                    .setRefreshToken(refreshToken)
                    .buildQueryMessage();

            OAuthJSONAccessTokenResponse tokenResponse = client.accessToken(oAuthClientRequest);

            TokenConnection connection = new TokenConnection(tokenResponse.getAccessToken(), tokenResponse.getRefreshToken(), Integer.parseInt(tokenResponse.getExpiresIn()));
            connections.put(connectionKey, connection);
            return true;

        } catch (Throwable t) {
            log.error("Problem while using refresh token", t);
            connections.remove(connectionKey);
        }
        return false;
    }

    public String getAccessToken(String location, String username) {

        // TODO (Serkan?): we return null here when there is a problem with fetching an access token, in other words when the client needs to login again (with user & password)
        // in case this happens, the login should be triggered ( probably somewhere near SIPCreatorGui#getAccessToken() )

        String tokenLocation = toTokenLocation(location);

        if (!connections.containsKey(connectionKey(username, location))) {
            return null;
        }

        TokenConnection connection = connections.get(connectionKey(username, location));
        if (connection.isTokenExpired()) {
            boolean refreshSuccessful = requestRefresh(connectionKey(username, location), connection.getRefreshToken());
            if(refreshSuccessful) {
                return connection.getAccessToken();
            } else {
                return null;
            }
        } else {
            return connection.getAccessToken();
        }
    }

    private String toTokenLocation(String location) {
        return "http://" + location + OAUTH2_ENDPOINT_PATH;
    }

    private String connectionKey(String username, String location) {
        return String.format("%s.#.%s", username, location);
    }


    private static class TokenConnection {
        private String accessToken = null;
        private String refreshToken = null;
        private Long requestTimeStamp = null;
        private Integer expiresIn = null;

        private TokenConnection(String accessToken, String refreshToken, Integer expiresIn) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.requestTimeStamp = System.currentTimeMillis();
            this.expiresIn = expiresIn;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public boolean isTokenExpired() {
            return System.currentTimeMillis() - requestTimeStamp > expiresIn * 1000;
        }
    }


}