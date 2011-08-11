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
        if (serverPort != that.serverPort) return false;
        if (password != null ? !password.equals(that.password) : that.password != null) return false;
        if (serverAddress != null ? !serverAddress.equals(that.serverAddress) : that.serverAddress != null)
            return false;
        return !(username != null ? !username.equals(that.username) : that.username != null);
    }

    @Override
    public int hashCode() {
        int result = username != null ? username.hashCode() : 0;
        result = 31 * result + (password != null ? password.hashCode() : 0);
        result = 31 * result + (serverAddress != null ? serverAddress.hashCode() : 0);
        result = 31 * result + serverPort;
        return result;
    }
}
