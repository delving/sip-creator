package eu.delving.sip.base;

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

import java.io.IOException;
import java.util.Map;

/**
 * This client uses the "resource owner password credentials" method described at http://tools.ietf.org/html/draft-ietf-oauth-v2-18#section-4.3
 * in order to get access to resources on the CultureHub server.
 *
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class OAuthClient {
    private Logger log = Logger.getLogger(getClass());
    private String hostPort;
    private String username;
    private PasswordRequest passwordRequest;
    private String password;
    private String accessToken;
    private String refreshToken;
    private long requestTimeStamp;
    private int expirationSeconds;

    public interface PasswordRequest {
        String getPassword();
    }

    public OAuthClient(String hostPort, String username, PasswordRequest passwordRequest) {
        this.hostPort = hostPort;
        this.username = username;
        this.passwordRequest = passwordRequest;
    }

    public String getToken() {
        try {
            if (System.currentTimeMillis() - requestTimeStamp > expirationSeconds * 1000) {
                accessToken = null;
            }
            if (accessToken == null) {
                if (refreshToken != null) {
                    requestWithRefreshToken();
                }
                if (accessToken == null) {
                    if (password == null) {
                        password = passwordRequest.getPassword();
                        if (password != null) {
                            requestWithPassword();
                        }
                    }
                }
            }
            return accessToken;
        }
        catch (OAuthProblemException e) {
            log.warn("OAuth Problem", e);
            return null;
        }
        catch (OAuthSystemException e) {
            log.warn("OAuth System Problem", e);
            return null;
        }

    }

    public void invalidateTokens() {
        accessToken = refreshToken = null;
    }

    private void requestWithPassword() throws OAuthSystemException, OAuthProblemException {
        OAuthClientRequest request = OAuthClientRequest.tokenLocation(getTokenUrl())
                .setGrantType(GrantType.PASSWORD).setUsername(username).setPassword(password)
                .buildQueryMessage();
        acceptResponse(OAUTH_CLIENT.accessToken(request));
    }

    private void requestWithRefreshToken() throws OAuthSystemException, OAuthProblemException {
        OAuthClientRequest request = OAuthClientRequest.tokenLocation(getTokenUrl())
                .setGrantType(GrantType.REFRESH_TOKEN).setRefreshToken(refreshToken)
                .buildQueryMessage();
        acceptResponse(OAUTH_CLIENT.accessToken(request));
    }

    private void acceptResponse(OAuthJSONAccessTokenResponse response) {
        requestTimeStamp = System.currentTimeMillis();
        accessToken = response.getAccessToken();
        refreshToken = response.getRefreshToken();
        expirationSeconds = Integer.parseInt(response.getExpiresIn());
    }

    private String getTokenUrl() {
        return String.format("http://%s/token", hostPort);
    }

    private final org.apache.amber.oauth2.client.OAuthClient OAUTH_CLIENT = new org.apache.amber.oauth2.client.OAuthClient(new org.apache.amber.oauth2.client.HttpClient() {
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
}