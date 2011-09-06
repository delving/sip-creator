package eu.delving.sip.desktop;

import eu.delving.sip.desktop.security.User;
import org.apache.amber.oauth2.client.OAuthClient;
import org.apache.amber.oauth2.client.request.OAuthClientRequest;
import org.apache.amber.oauth2.client.response.OAuthClientResponse;
import org.apache.amber.oauth2.client.response.OAuthClientResponseFactory;
import org.apache.amber.oauth2.client.response.OAuthJSONAccessTokenResponse;
import org.apache.amber.oauth2.common.exception.OAuthProblemException;
import org.apache.amber.oauth2.common.exception.OAuthRuntimeException;
import org.apache.amber.oauth2.common.exception.OAuthSystemException;
import org.apache.amber.oauth2.common.message.types.GrantType;
import org.apache.amber.oauth2.common.utils.OAuthUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * This client uses the "resource owner password credentials" method described at http://tools.ietf.org/html/draft-ietf-oauth-v2-18#section-4.3
 * in order to get access to resources on the CultureHub server.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
public class AuthenticationClient {

    private static final String OAUTH2_ENDPOINT_PATH = "/token";
    private Logger log = Logger.getLogger(getClass());
    private Map<String, TokenConnection> connections = new HashMap<String, TokenConnection>();

    public User requestAccess(String location, String username, String password) throws OAuthSystemException, OAuthProblemException {
        String tokenLocation = toTokenLocation(location);
        OAuthClientRequest request = OAuthClientRequest.tokenLocation(tokenLocation)
                .setGrantType(GrantType.PASSWORD)
                .setUsername(username)
                .setPassword(password)
                .buildQueryMessage();
        OAuthJSONAccessTokenResponse tokenResponse = OAUTH_CLIENT.accessToken(request);
        TokenConnection connection = new TokenConnection(
                tokenResponse.getAccessToken(),
                tokenResponse.getRefreshToken(),
                Integer.parseInt(tokenResponse.getExpiresIn())
        );
        connections.put(connectionKey(username, location), connection);
        // todo: user should contain preferences and permission which are sent by the services module
        User user = new User();
        user.setUsername(username);
        return user;
    }

    private String requestRefresh(String connectionKey, String refreshToken) throws OAuthSystemException, OAuthProblemException {
        String location = connectionKey.split("#")[1];
        String tokenLocation = toTokenLocation(location);
        OAuthClientRequest request = OAuthClientRequest.tokenLocation(tokenLocation)
                .setGrantType(GrantType.REFRESH_TOKEN)
                .setRefreshToken(refreshToken)
                .buildQueryMessage();
        OAuthJSONAccessTokenResponse response = OAUTH_CLIENT.accessToken(request);
        TokenConnection connection = new TokenConnection(
                response.getAccessToken(),
                response.getRefreshToken(),
                Integer.parseInt(response.getExpiresIn())
        );
        connections.put(connectionKey, connection);
        return connection.getAccessToken();
    }

    public String getAccessToken(String location, String username) throws OAuthSystemException, OAuthProblemException {
        if (!connections.containsKey(connectionKey(username, location))) {
            throw new OAuthRuntimeException("Key not found");
        }
        TokenConnection connection = connections.get(connectionKey(username, location));
        if (!connection.isTokenExpired()) {
            return connection.getAccessToken();
        }
        return requestRefresh(connectionKey(username, location), connection.getRefreshToken());
    }


    private String toTokenLocation(String location) {
        return "http://" + location + OAUTH2_ENDPOINT_PATH;
    }

    private String connectionKey(String username, String location) {
        return String.format("%s.#.%s", username, location);
    }

    private final OAuthClient OAUTH_CLIENT = new OAuthClient(new org.apache.amber.oauth2.client.HttpClient() {
        @Override
        public <T extends OAuthClientResponse> T execute(OAuthClientRequest request, Map<String, String> headers, String requestMethod, Class<T> responseClass) throws OAuthSystemException, OAuthProblemException {
            try {
                HttpClient httpClient = new DefaultHttpClient();
                HttpGet get = new HttpGet(request.getLocationUri());
                HttpResponse httpResponse = httpClient.execute(get);
                String responseBody = OAuthUtils.saveStreamAsString(httpResponse.getEntity().getContent());
                return OAuthClientResponseFactory.createCustomResponse(
                        responseBody,
                        httpResponse.getEntity().getContentType().getValue(),
                        httpResponse.getStatusLine().getStatusCode(),
                        responseClass
                );
            }
            catch (IOException e) {
                log.error("OAuth Client problem", e); // todo: something sensible
            }
            return null;
        }
    });

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