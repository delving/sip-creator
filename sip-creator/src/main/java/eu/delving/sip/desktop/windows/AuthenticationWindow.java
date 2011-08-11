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
import java.beans.PropertyVetoException;

/**
 * OAuth2 login window.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
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
    private Listener listener;
    private DesktopPreferences desktopPreferences;
    private AuthenticationClient authenticationClient;

    public interface Listener {

        void success(User user);

        void failed(Exception exception);

        void signedOut();
    }

    public AuthenticationWindow(Listener listener, DesktopPreferences desktopPreferences, AuthenticationClient authenticationClient) {
        super(null); // todo: not needed, we don't have the SipModel yet at this point.
        this.listener = listener;
        this.desktopPreferences = desktopPreferences;
        this.authenticationClient = authenticationClient;
        setLayout(new GridBagLayout());
        buildLayout();
        setResizable(false);
        setClosable(false);
        try {
            setIcon(false);
        }
        catch (PropertyVetoException e) {
            LOG.error("Can't change property", e);
        }
        setSize(new Dimension());
        setPreferencesTransient(true);
    }

    public void setCredentials(DesktopPreferences.Credentials credentials) {
        username.setText(credentials.getUsername());
        password.setText(credentials.getPassword());
        serverAddress.setText(credentials.getServerAddress());
        serverPort.setText("" + credentials.getServerPort());
        rememberMe.setSelected(true);
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
                            desktopPreferences.saveCredentials(
                                    new CredentialsImpl(usernameText, new String(passwordText), serverAddressText, Integer.parseInt(serverPortText)));
                            setVisible(false);
                            listener.success(user);
                        }
                        catch (Exception e) {
                            listener.failed(e);
                        }
                    }
                }
        );
        add(rememberMe, gbc);
        gbc.line();
        gbc.right();
        add(login, gbc);
    }
}
