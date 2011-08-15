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

package eu.delving.sip.desktop.windows;

import eu.delving.security.AuthenticationClient;
import eu.delving.security.User;
import eu.delving.sip.desktop.CredentialsImpl;
import eu.delving.sip.desktop.DesktopPreferences;
import eu.europeana.sip.util.GridBagHelper;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Set;

/**
 * OAuth2 login window. The
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 *         todo: make it a modal popup
 */
public class AuthenticationWindow extends DesktopWindow {

    private static final Logger LOG = Logger.getRootLogger();
    private static final String LOGIN_LABEL = "Login";
    private static final String REMEMBER_LABEL = "Remember me";
    private static final String USERNAME_LABEL = "Username";
    private static final String PASSWORD_LABEL = "Password";
    private static final String SERVER_LABEL = "Server address";
    private static final String SERVER_PORT = "Port";

    private JLabel usernameLabel = new JLabel(USERNAME_LABEL);
    private JLabel passwordLabel = new JLabel(PASSWORD_LABEL);
    private JLabel serverLabel = new JLabel(SERVER_LABEL);
    private JLabel serverPortLabel = new JLabel(SERVER_PORT);
    private JTextField username = new JTextField();
    private JTextField serverAddress = new JTextField();
    private JTextField serverPort = new JTextField();
    private JPasswordField password = new JPasswordField();
    private JButton login = new JButton(LOGIN_LABEL);
    private JCheckBox rememberMe = new JCheckBox(REMEMBER_LABEL);
    private DesktopPreferences desktopPreferences;
    private AuthenticationClient authenticationClient;
    private JComboBox servers = new JComboBox();
    private Listener listener;
    private DesktopPreferences.Credentials credentials;

    public interface Listener {

        void credentialsChanged(DesktopPreferences.Credentials credentials);

        void success(User user);

        void failed(Exception e);
    }

    public AuthenticationWindow(DesktopPreferences desktopPreferences, AuthenticationClient authenticationClient, Listener listener, Set<DesktopPreferences.Credentials> credentials) {
        super(null);
        this.desktopPreferences = desktopPreferences;
        this.authenticationClient = authenticationClient;
        this.listener = listener;
        setLayout(new GridBagLayout());
        setResizable(false);
        setSize(new Dimension(600, 400));
        buildLayout();
        setCredentials(credentials);
    }

    private void setCredentials(Set<DesktopPreferences.Credentials> credentialsSet) {
        if (null == credentialsSet) {
            return;
        }
        servers.addActionListener(
                new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        // todo: update fields when another server is selected
                    }
                }
        );
        for (DesktopPreferences.Credentials credentials : credentialsSet) { // todo: set credentials, not in a loop
            servers.addItem(String.format("%s:%s", credentials.getServerAddress(), credentials.getServerPort()));
            username.setText(credentials.getUsername());
            password.setText(credentials.getPassword());
            serverAddress.setText(credentials.getServerAddress());
            serverPort.setText("" + credentials.getServerPort());
            rememberMe.setSelected(true);
            listener.credentialsChanged(credentials);
            break;
        }
    }

    // todo: not implemented yet
    private class ServerListModel extends AbstractListModel implements ComboBoxModel {

        List<DesktopPreferences.Credentials> credentialsList;

        private ServerListModel(List<DesktopPreferences.Credentials> credentialsList) {
            this.credentialsList = credentialsList;
            setSelectedItem(credentialsList.get(0));
        }

        private String selection;

        @Override
        public void setSelectedItem(Object o) {
            DesktopPreferences.Credentials credentials = (DesktopPreferences.Credentials) o;
            selection = String.format("%s:%s", credentials.getServerAddress(), credentials.getServerPort());
        }

        @Override
        public Object getSelectedItem() {
            return selection;
        }

        @Override
        public int getSize() {
            return credentialsList.size();
        }

        @Override
        public Object getElementAt(int i) {
            return credentialsList.get(i);
        }
    }

    private void buildLayout() {
        GridBagHelper gbc = new GridBagHelper();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.reset();
        add(usernameLabel, gbc);
        gbc.right();
        add(username, gbc);
        gbc.line();
        add(passwordLabel, gbc);
        gbc.right();
        add(password, gbc);
        gbc.line();
        add(serverLabel, gbc);
        gbc.right();
        add(serverAddress, gbc);
        gbc.line();
        add(serverPortLabel, gbc);
        gbc.right();
        add(serverPort, gbc);
        gbc.line();
        gbc.right();
        add(rememberMe, gbc);
        gbc.line();
        gbc.right();
        add(login, gbc);
        gbc.line();
        if (desktopPreferences.getWorkspace().getHostDirectories().size() > 1) {
            gbc.gridwidth = 2;
            add(servers, gbc);
        }
        login.addActionListener(
                new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        try {
                            String serverAddressText = serverAddress.getText();
                            String serverPortText = serverPort.getText();
                            String usernameText = username.getText();
                            char[] passwordText = password.getPassword();
                            User user = authenticationClient.requestAccess(String.format("%s:%s", serverAddressText, serverPortText), usernameText, new String(passwordText));
                            AuthenticationWindow.this.credentials = new CredentialsImpl(usernameText, new String(passwordText), serverAddressText, Integer.parseInt(serverPortText));
                            listener.success(user);
                            desktopPreferences.saveCredentials(credentials);
                            setVisible(false);
                        }
                        catch (Exception e) {
                            listener.failed(e);
                        }
                    }
                }
        );
    }

    public DesktopPreferences.Credentials getCredentials() {
        return credentials;
    }
}
