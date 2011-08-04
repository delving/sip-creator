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

package eu.europeana.sip.desktop;

import eu.europeana.sip.util.GridBagAdapter;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;

/**
 * OAuth2 login window.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 *         todo: should behave differently, like a popup.
 */
public class AuthenticationWindow extends JInternalFrame {

    private static final String TITLE_LABEL = "Authentication";
    private static final String LOGIN_LABEL = "Login";
    private static final String REMEMBER_LABEL = "Remember me";
    private static final String USERNAME_LABEL = "Username";
    private static final String PASSWORD_LABEL = "Password";
    private static final Logger LOG = Logger.getRootLogger();

    private JLabel usernameLabel = new JLabel(USERNAME_LABEL);
    private JLabel passwordLabel = new JLabel(PASSWORD_LABEL);
    private JTextField username = new JTextField();
    private JPasswordField password = new JPasswordField();
    private JButton login = new JButton(LOGIN_LABEL);
    private JCheckBox rememberMe = new JCheckBox(REMEMBER_LABEL);

    public AuthenticationWindow() {
        super(TITLE_LABEL, true, true, true);
        setLayout(new GridBagLayout());
        buildLayout();
    }

    private void buildLayout() {
        GridBagAdapter gbc = new GridBagAdapter();
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
        add(login, gbc);
    }
}
