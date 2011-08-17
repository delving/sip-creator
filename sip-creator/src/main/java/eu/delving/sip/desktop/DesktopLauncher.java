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

import eu.delving.groovy.GroovyCodeResource;
import eu.delving.metadata.MetadataModel;
import eu.delving.metadata.MetadataModelImpl;
import eu.delving.security.User;
import eu.delving.sip.DataSetInfo;
import eu.delving.sip.FileStore;
import eu.delving.sip.FileStoreException;
import eu.delving.sip.FileStoreImpl;
import eu.delving.sip.desktop.navigation.Actions;
import eu.delving.sip.desktop.navigation.NavigationBar;
import eu.delving.sip.desktop.navigation.NavigationMenu;
import eu.delving.sip.desktop.windows.AuthenticationWindow;
import eu.delving.sip.desktop.windows.DesktopManager;
import eu.delving.sip.desktop.windows.DesktopWindow;
import eu.delving.sip.gui.AuthenticationClient;
import eu.europeana.sip.localization.Constants;
import eu.europeana.sip.model.SipModel;
import eu.europeana.sip.model.UserNotifier;
import eu.europeana.sip.util.DataSetClient;
import org.apache.amber.oauth2.common.exception.OAuthProblemException;
import org.apache.amber.oauth2.common.exception.OAuthSystemException;
import org.apache.log4j.Logger;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyVetoException;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This is the main window of the SIP-Creator.
 * <p/>
 * The SIP-Creator contains the following windows:
 * <p/>
 * <ul>
 * <li>DataSet window</li>
 * <li>Analysis window</li>
 * <li>Mapping window</li>
 * <li>Log window</li>
 * <ul>
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class DesktopLauncher {
    private static final Logger LOG = Logger.getRootLogger();
    private DesktopManager desktopManager;
    private DesktopPreferences desktopPreferences = new DesktopPreferencesImpl(getClass());
    private Actions actions;
    private SipModel sipModel;
    private AuthenticationClient authenticationClient = new AuthenticationClient();
    private AuthenticationWindow authenticationWindow;

    public DesktopLauncher(File fileStoreDirectory) throws FileStoreException {
        MetadataModel metadataModel = loadMetadataModel();
        FileStore fileStore = new FileStoreImpl(fileStoreDirectory, metadataModel);
        UserNotifier userNotifier = new UserNotifier() {
            @Override
            public void tellUser(String message) {
                JOptionPane.showMessageDialog(null, "Message from SipModel", message, JOptionPane.INFORMATION_MESSAGE);
            }

            @Override
            public void tellUser(String message, Exception exception) {
                JOptionPane.showMessageDialog(null, "Error from SipModel", String.format("%s%n%s", message, exception.getMessage()), JOptionPane.ERROR_MESSAGE);
            }
        };
        sipModel = new SipModel(fileStore, metadataModel, new GroovyCodeResource(getClass().getClassLoader()), userNotifier);
        desktopManager = new DesktopManager(sipModel);
        actions = new Actions(desktopManager);
        authenticationWindow = new AuthenticationWindow(desktopPreferences, authenticationClient,
                new AuthenticationWindow.Listener() {

                    @Override
                    public void credentialsChanged(DesktopPreferences.Credentials credentials) {
                    }

                    @Override
                    public void success(User user) {
                        dataSetClient.setListFetchingEnabled(true);
                        restoreWindows();
                    }

                    @Override
                    public void failed(Exception exception) {
                        LOG.error("Authentication failed", exception);
                        JOptionPane.showMessageDialog(null, exception.getMessage(), "Authentication failed", JOptionPane.ERROR_MESSAGE);
                    }
                },
                getCredentials()
        );
        desktopManager.add(authenticationWindow);
    }

    /**
     * Scans the directories in the workspace. Directories are formatted with host_port pattern. If a directory
     * is found, the stored credentials for a specific host will be used. Unmatched credentials will be ignored.
     *
     * @return The matched credentials.
     */
    private Set<DesktopPreferences.Credentials> getCredentials() {
        Set<DesktopPreferences.Credentials> credentialsSet = new HashSet<DesktopPreferences.Credentials>();
        Set<String> hostDirectories = desktopPreferences.getWorkspace().getHostDirectories();
        for (String directory : hostDirectories) {
            CredentialsImpl empty = new CredentialsImpl("", "", directory.split(":")[0], Integer.parseInt(directory.split(":")[1]));
            if (null == desktopPreferences.getCredentials()) {
                credentialsSet.add(empty);
                continue;
            }
            for (DesktopPreferences.Credentials credentials : desktopPreferences.getCredentials()) {
                if (empty.equals(credentials)) {
                    empty = new CredentialsImpl(credentials.getUsername(), credentials.getPassword(), credentials.getServerAddress(), credentials.getServerPort());
                }
            }
            credentialsSet.add(empty);
        }
        return credentialsSet;
    }

    private void restoreWindows() {
        DesktopPreferences.DesktopState desktopState = desktopPreferences.getDesktopState();
        if (desktopState != null) {
            LOG.info("Spec is " + desktopState.getSpec());
            for (WindowState windowState : desktopState.getWindowStates()) {
                LOG.info("Adding window : " + windowState.getWindowId());
                try {
                    DesktopWindow window = desktopManager.getWindow(windowState.getWindowId());
                    if (null == window) {
                        continue;
                    }
                    desktopManager.add(window);
                    window.setVisible(true);
                    window.setSize(windowState.getSize());
                    window.setLocation(windowState.getPoint());
                    window.setSelected(windowState.isSelected());
                }
                catch (PropertyVetoException e) {
                    LOG.error("Can't select window", e);
                }
            }
        }
    }

    private JComponent buildNavigation() {
        NavigationBar navigationBar = new NavigationBar(actions);
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navigationBar, desktopManager.getDesktop());
        splitPane.setBorder(null);
        splitPane.setSize(new Dimension(400, 400));
        splitPane.setDividerLocation(200);
        splitPane.setVisible(true);
        return splitPane;
    }

    public DesktopPreferences getDesktopPreferences() {
        return desktopPreferences;
    }

    public DesktopManager getDesktopManager() {
        return desktopManager;
    }

    private MetadataModel loadMetadataModel() {
        try {
            MetadataModelImpl metadataModel = new MetadataModelImpl();
            metadataModel.setRecordDefinitionResources(Arrays.asList(
                    "/ese-record-definition.xml",
                    "/icn-record-definition.xml",
                    "/abm-record-definition.xml"
            ));
            metadataModel.setDefaultPrefix("ese");
            return metadataModel;
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
            return null;
        }
    }

    private DataSetClient dataSetClient = new DataSetClient(new DataSetClient.Context() {

        @Override
        public String getServerUrl() {
            DesktopPreferences.Credentials credentials = authenticationWindow.getCredentials();
            return String.format("http://%s:%d/%s/dataset", credentials.getServerAddress(), credentials.getServerPort(), credentials.getUsername());
        }

        @Override
        public String getAccessToken() {
            try {
                DesktopPreferences.Credentials credentials = authenticationWindow.getCredentials();
                return authenticationClient.getAccessToken(String.format("%s:%s", credentials.getServerAddress(), credentials.getServerPort()), credentials.getUsername());
            }
            catch (OAuthSystemException e) {
                authenticationWindow.setVisible(true);
            }
            catch (OAuthProblemException e) {
                authenticationWindow.setVisible(true);
            }
            return null;
        }

        @Override
        public void setInfo(DataSetInfo dataSetInfo) {
            // todo: update datasetwindow
            LOG.info("Dataset received " + dataSetInfo.spec);
        }

        @Override
        public void setList(List<DataSetInfo> list) {
//            dataSetWindow.setData(list);
        }

        @Override
        public void tellUser(String message) {
            sipModel.getUserNotifier().tellUser(message);
        }

        @Override
        public void disconnected() {
            sipModel.getUserNotifier().tellUser(String.format("Disconnected from Repository at %s", getServerUrl()));
        }
    });

    private static UserNotifier USER_NOTIFIER = new UserNotifier() {
        @Override
        public void tellUser(String message) {
            JOptionPane.showMessageDialog(null, "Message from SipModel", message, JOptionPane.INFORMATION_MESSAGE);
        }

        @Override
        public void tellUser(String message, Exception exception) {
            JOptionPane.showMessageDialog(null, "Error from SipModel", String.format("%s%n%s", message, exception.getMessage()), JOptionPane.ERROR_MESSAGE);
        }
    };

    public static void main(String... args) throws FileStoreException {
        File fileStoreDirectory = FileStoreFinder.getFileStoreDirectory();
        final DesktopLauncher desktopLauncher = new DesktopLauncher(fileStoreDirectory);
        JFrame main = new JFrame(Constants.SIP_CREATOR_TITLE);
        main.getContentPane().add(desktopLauncher.buildNavigation(), BorderLayout.CENTER);
        main.setExtendedState(Frame.MAXIMIZED_BOTH);
        main.setLocationRelativeTo(null);
        main.setJMenuBar(new NavigationMenu(desktopLauncher.actions));
        main.setVisible(true);
        main.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                new Actions.ExitAction(desktopLauncher).actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "exitAction"));
            }
        });
    }


}
