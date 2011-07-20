/*
 * Copyright 2010 DELVING BV
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

package eu.europeana.sip.gui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import javax.swing.*;

import eu.delving.sip.AppConfig;
import eu.delving.sip.OAuth2Client;
import eu.europeana.sip.model.AppConfigModel;
import eu.europeana.sip.model.SipModel;

/**
 * @author Gerald de Jong, Delving BV <gerald@delving.eu>
 */

public class RepositoryMenu extends JMenu {
    private Component parent;
    private SipModel sipModel;
    private OAuth2Client oauth2Client;

    public RepositoryMenu(Component parent, SipModel sipModel, OAuth2Client client) {
        super("Repository");
        this.parent = parent;
        this.sipModel = sipModel;
        this.oauth2Client = client;
        sipModel.getAppConfigModel().addListener(new AppConfigModel.Listener() {
            @Override
            public void appConfigUpdated(AppConfig appConfig) {
                refresh();
            }
        });
        refresh();
    }

    private void refresh() {
        removeAll();
        add(new ServerHostPortAction());
        add(new LoginAction());
        addSeparator();
        add(new SaveAction());
        add(new DeleteAction());
        List<AppConfig.RepositoryConnection> connections = sipModel.getAppConfigModel().getRepositoryConnections();
        if (!connections.isEmpty()) {
            JMenu selectMenu = new JMenu("Select");
            add(selectMenu);
            for (AppConfig.RepositoryConnection connection : connections) {
                Action action = new SelectAction(connection.serverHostPort);
                selectMenu.add(action);
                if (connection.serverHostPort.equals(sipModel.getAppConfigModel().getServerHostPort())) {
                    action.setEnabled(false);
                }
            }
        }
    }

    private class ServerHostPortAction extends AbstractAction {

        public ServerHostPortAction() {
            super("Server Network Address");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            String serverHostPort = JOptionPane.showInputDialog(parent, "Server network address host:port (eg. delving.eu:8080).", sipModel.getAppConfigModel().getServerHostPort());
            if (serverHostPort != null && !serverHostPort.isEmpty()) {
                sipModel.getAppConfigModel().setServerHostPort(serverHostPort);
            }
        }
    }

    private class LoginAction extends AbstractAction {

        public LoginAction() {
            super("Login");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            // TODO this location is hijacked for performing the login. It should in fact happen in a different place / at the first connection.
            JPanel loginPanel = new JPanel(new GridLayout(2, 1));
            JLabel usernameLabel = new JLabel("Username");
            JLabel passwordLabel = new JLabel("Password");
            JTextField usernameField = new JTextField(35);
            JPasswordField passwordField = new JPasswordField(35);
            loginPanel.add(usernameLabel);
            loginPanel.add(usernameField);
            loginPanel.add(passwordLabel);
            loginPanel.add(passwordField);
            Object[] msg = { loginPanel };
            int result = JOptionPane.showConfirmDialog(parent, msg, "Permission", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                boolean loginSuccessful = oauth2Client.requestAccess(sipModel.getAppConfigModel().getServerHostPort(), usernameField.getText(), new String(passwordField.getPassword()));
                if(!loginSuccessful) {
                    JOptionPane.showMessageDialog(parent, "Error while logging in", "Permission error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private class SaveAction extends AbstractAction {

        public SaveAction() {
            super("Save");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            sipModel.getAppConfigModel().saveConnection();
        }
    }

    private class DeleteAction extends AbstractAction {

        public DeleteAction() {
            super("Delete");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            sipModel.getAppConfigModel().deleteConnection();
        }
    }

    private class SelectAction extends AbstractAction {
        private String serverHostPort;

        public SelectAction(String serverHostPort) {
            super(serverHostPort);
            this.serverHostPort = serverHostPort;
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            sipModel.getAppConfigModel().selectConnection(serverHostPort);
        }
    }

}