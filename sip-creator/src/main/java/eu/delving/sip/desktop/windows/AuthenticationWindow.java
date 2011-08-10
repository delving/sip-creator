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

import eu.delving.security.OAuth2Client;
import eu.delving.security.User;
import eu.delving.sip.desktop.DesktopPreferences;
import eu.europeana.sip.util.GridBagHelper;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyVetoException;
import java.net.MalformedURLException;
import java.net.URL;

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

    private JLabel usernameLabel = new JLabel(USERNAME_LABEL);
    private JLabel passwordLabel = new JLabel(PASSWORD_LABEL);
    private JTextField username = new JTextField();
    private JPasswordField password = new JPasswordField();
    private JButton login = new JButton(LOGIN_LABEL);
    private JCheckBox rememberMe = new JCheckBox(REMEMBER_LABEL);
    private Listener listener;
    private DesktopPreferences desktopPreferences;

    public interface Listener {

        void success(Object user);

        void failed(Exception exception);
    }

    public AuthenticationWindow(Listener listener, DesktopPreferences desktopPreferences) {
        super(null); // todo: broken
        this.listener = listener;
        this.desktopPreferences = desktopPreferences;
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
        add(rememberMe, gbc);
        gbc.right();
        login.addActionListener(
                new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        SwingUtilities.invokeLater(new Authentication(username.getText(), password.getPassword()));
                    }
                }
        );
        add(login, gbc);
    }

    private class Authentication implements Runnable {

        private String username;
        private char[] password;

        private Authentication(String username, char[] password) {
            this.username = username;
            this.password = password;
        }

        @Override
        public void run() {
            try {
                LOG.info(String.format("Logging in with %s@localhost:9000", username));
                URL serverLocation = new URL("http://localhost:9000");
                OAuth2Client client = new OAuth2Client();
                User user = client.requestAccess(serverLocation, username, new String(password).getBytes());
                LOG.info("User " + user);
                desktopPreferences.saveCredentials(
                        new DesktopPreferences.Credentials() {

                            @Override
                            public String getUsername() {
                                return username;
                            }

                            @Override
                            public String getPassword() {
                                return new String(password);
                            }
                        }
                );
                listener.success(user);
            }
            catch (MalformedURLException e) {
                System.err.println(e);
                listener.failed(e);
            }
        }
    }
}
