/*
 * Copyright 2011 DELVING BV
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

package eu.delving.sip.base;

import org.apache.amber.oauth2.client.request.OAuthClientRequest;
import org.apache.amber.oauth2.client.response.OAuthClientResponse;
import org.apache.amber.oauth2.client.response.OAuthClientResponseFactory;
import org.apache.amber.oauth2.client.response.OAuthJSONAccessTokenResponse;
import org.apache.amber.oauth2.common.exception.OAuthProblemException;
import org.apache.amber.oauth2.common.exception.OAuthSystemException;
import org.apache.amber.oauth2.common.message.types.GrantType;
import org.apache.amber.oauth2.common.utils.OAuthUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
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
    private String accessToken;
    private String refreshToken;
    private long requestTimeStamp;
    private int expirationSeconds;
    private HttpClient httpClient;

    public interface PasswordRequest {
        String getPassword();
    }

    public OAuthClient(HttpClient httpClient, String hostPort, String username, PasswordRequest passwordRequest) {
        this.httpClient = httpClient;
        this.hostPort = hostPort;
        this.username = username;
        this.passwordRequest = passwordRequest;
    }

    public String getToken() throws OAuthSystemException, OAuthProblemException {
        try {
            if (System.currentTimeMillis() - requestTimeStamp > expirationSeconds * 1000) {
                accessToken = null;
            }
            if (accessToken == null) {
                if (refreshToken != null) {
                    if (!requestWithRefreshToken()) {
                        refreshToken = null;
                        throw OAuthProblemException.error(Problem.EXPIRED_TOKEN.string, "Refresh token failed");
                    }
                }
                else if (!requestWithPassword(passwordRequest.getPassword())) {
                    throw OAuthProblemException.error(Problem.INVALID_GRANT.string, "Password failed");
                }
            }
            return accessToken;
        }
        catch (OAuthProblemException e) {
            invalidateTokens();
            throw e;
        }
        catch (OAuthSystemException e) {
            invalidateTokens();
            throw e;
        }

    }

    public void invalidateTokens() {
        if (accessToken != null) {
            accessToken = null;
        }
        else {
            refreshToken = null;
        }
    }

    private boolean requestWithPassword(String password) throws OAuthSystemException, OAuthProblemException {
        if (password == null) return false;
        OAuthClientRequest request = OAuthClientRequest.tokenLocation(getTokenUrl())
                .setGrantType(GrantType.PASSWORD).setUsername(username).setPassword(password)
                .buildQueryMessage();
        acceptResponse(OAUTH_CLIENT.accessToken(request));
        return accessToken != null;
    }

    private boolean requestWithRefreshToken() throws OAuthSystemException, OAuthProblemException {
        OAuthClientRequest request = OAuthClientRequest.tokenLocation(getTokenUrl())
                .setGrantType(GrantType.REFRESH_TOKEN).setRefreshToken(refreshToken)
                .buildQueryMessage();
        acceptResponse(OAUTH_CLIENT.accessToken(request));
        return accessToken != null;
    }

    private void acceptResponse(OAuthJSONAccessTokenResponse response) {
        if (null == response) {
            throw new RuntimeException("Response is null!");
        }
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
                HttpGet get = new HttpGet(request.getLocationUri());
                HttpResponse httpResponse = httpClient.execute(get);
                HttpEntity entity = httpResponse.getEntity();
                if (entity == null) {
                    throw new OAuthSystemException(String.format("Received null entity"));
                }
                String responseBody = OAuthUtils.saveStreamAsString(entity.getContent());
                EntityUtils.consume(entity);
                return OAuthClientResponseFactory.createCustomResponse(
                        responseBody,
                        entity.getContentType().getValue(),
                        httpResponse.getStatusLine().getStatusCode(),
                        responseClass
                );
            }
            catch (IOException e) {
                log.error("OAuth Client problem", e);
                throw new OAuthSystemException(String.format("Can't connect to server : %s", e.getMessage()), e);
            }
        }

        @Override
        public void shutdown() {
            // todo: not sure what is to be done here
        }
    });

    public static Problem getProblem(OAuthProblemException ex) {
        String error = ex.getError();
        for (Problem p : Problem.values()) {
            if (p.string.equals(error)) {
                return p;
            }
        }
        return Problem.UNKNOWN;
    }

    public enum Problem {
        INVALID_REQUEST("invalid_request"),
        INVALID_CLIENT("invalid_client"),
        UNAUTHORIZED_CLIENT("unauthorized_client"),
        REDIRECT_URI_MISMATCH("redirect_uri_mismatch"),
        ACCESS_DENIED("access_denied"),
        UNSUPPORTED_RESPONSE_TYPE("unsupported_response_type"),
        INVALID_GRANT("invalid_grant"),
        UNSUPPORTED_GRANT_TYPE("unsupported_grant_type"),
        INVALID_SCOPE("invalid_scope"),
        EXPIRED_TOKEN("expired_token"),
        INSUFFICIENT_SCOPE("insufficient_scope"),
        INVALID_TOKEN("invalid_token"),
        UNKNOWN("???");

        private String string;

        Problem(String string) {
            this.string = string;
        }
    }

}