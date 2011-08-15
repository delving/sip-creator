/*
 * Copyright 2010 DELVING BV
 *
 * Licensed under the EUPL, Version 1.0 or as soon they
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

package eu.delving.sip.desktop;

/**
 * todo: add description
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class CredentialsImpl implements DesktopPreferences.Credentials {

    private String username;
    private String password;
    private String serverAddress;
    private int serverPort;

    public CredentialsImpl(String username, String password, String serverAddress, int serverPort) {
        this.username = username;
        this.password = password;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getServerAddress() {
        return serverAddress;
    }

    @Override
    public int getServerPort() {
        return serverPort;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CredentialsImpl that = (CredentialsImpl) o;
        return serverPort == that.serverPort && serverAddress.equals(that.serverAddress);
    }

    @Override
    public int hashCode() {
        int result = serverAddress.hashCode();
        result = 31 * result + serverPort;
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("CredentialsImpl");
        sb.append("{username='").append(username).append('\'');
        sb.append(", password='").append(password).append('\'');
        sb.append(", serverAddress='").append(serverAddress).append('\'');
        sb.append(", serverPort=").append(serverPort);
        sb.append('}');
        return sb.toString();
    }
}
