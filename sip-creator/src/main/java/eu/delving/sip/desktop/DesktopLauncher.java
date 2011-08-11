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
import eu.delving.security.AuthenticationClient;
import eu.delving.security.User;
import eu.delving.sip.DataSetClient;
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
import eu.europeana.sip.localization.Constants;
import eu.europeana.sip.model.SipModel;
import eu.europeana.sip.model.UserNotifier;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyVetoException;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * This is the main window of the SIP-Creator.
 *
 * The SIP-Creator contains the following windows:
 *
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
    private DesktopPreferences desktopPreferences;
    private DesktopPreferences.DesktopState desktopState;
    private User user;
    private DesktopPreferences.Credentials credentials;
    private Actions actions;
    private static JFrame main;
    private SipModel sipModel;
    private AuthenticationClient authenticationClient = new AuthenticationClient();
    private FileStore.DataSetStore currentStore; // todo: gone for some reason ...

    private DataSetClient dataSetClient = new DataSetClient(new DataSetClient.Context() {

        @Override
        public String getServerUrl() {
            return sipModel.getAppConfigModel().getServerUrl();
        }

        @Override
        public String getAccessToken() {
            return authenticationClient.getAccessToken(sipModel.getAppConfigModel().getServerHostPort(), sipModel.getAppConfigModel().getUsername());
        }

        @Override
        public void setInfo(DataSetInfo dataSetInfo) {
            // todo: update datasetwindow
            LOG.info("Dataset received " + dataSetInfo);
        }

        @Override
        public void setList(List<DataSetInfo> list) {
            // todo: update datasetwindow
            LOG.info("Datasets received " + list);
        }

        @Override
        public void tellUser(String message) {
            sipModel.getUserNotifier().tellUser(message);
        }

        @Override
        public void disconnected() {
            sipModel.getUserNotifier().tellUser(String.format("Disconnected from Repository at %s", sipModel.getAppConfigModel().getServerHostPort()));
        }
    });

    private File getFileStoreDirectory(String path) throws FileStoreException {
        File fileStore = new File(path);
        if (fileStore.isFile()) {
            try {
                List<String> lines = FileUtils.readLines(fileStore);
                String directory;
                if (lines.size() == 1) {
                    directory = lines.get(0);
                }
                else {
                    directory = (String) JOptionPane.showInputDialog(null,
                            "Please choose file store", "Launch SIP-Creator", JOptionPane.PLAIN_MESSAGE, null,
                            lines.toArray(), "");
                }
                if (directory == null) {
                    System.exit(0);
                }
                fileStore = new File(directory);
                if (fileStore.exists() && !fileStore.isDirectory()) {
                    throw new FileStoreException(String.format("%s is not a directory", fileStore.getAbsolutePath()));
                }
            }
            catch (IOException e) {
                throw new FileStoreException("Unable to read the file " + fileStore.getAbsolutePath());
            }
        }
        LOG.info("File store is now : " + fileStore.getAbsolutePath());
        return fileStore;
    }

    public DesktopLauncher() throws FileStoreException {
        desktopPreferences = new DesktopPreferencesImpl(getClass());
        DesktopPreferences.Workspace workspace = desktopPreferences.loadWorkspace();
        if (null == workspace || StringUtils.isEmpty(workspace.getWorkspacePath())) {
            new Actions.WorkspaceAction(desktopPreferences).actionPerformed(null); // todo: fix!
        }
        MetadataModel metadataModel = loadMetadataModel();
        File fileStoreDirectory = getFileStoreDirectory(desktopPreferences.loadWorkspace().getWorkspacePath()); // todo: fix!
        FileStore fileStore = new FileStoreImpl(fileStoreDirectory, metadataModel);
        GroovyCodeResource groovyCodeResource = new GroovyCodeResource();
        sipModel = new SipModel(fileStore, metadataModel, groovyCodeResource, new UserNotifier() {
            @Override
            public void tellUser(String message) {
                System.err.println(message);
            }

            @Override
            public void tellUser(String message, Exception exception) {
                System.err.printf("%s : %s%n", message, exception.getMessage());
            }
        });
        desktopManager = new DesktopManager(sipModel);
        actions = new Actions(desktopManager);
        DesktopLauncher.this.credentials = desktopPreferences.loadCredentials();
        DesktopLauncher.this.desktopState = desktopPreferences.loadDesktopState();
        buildLayout();
    }

    private void buildLayout() {
        AuthenticationWindow authenticationWindow = new AuthenticationWindow(
                new AuthenticationWindow.Listener() {

                    @Override
                    public void success(User user) {
                        DesktopLauncher.this.user = user;
                        dataSetClient.setListFetchingEnabled(true);
                        actions.setEnabled(true);
                        if (null != desktopState) {
                            restoreWindows(desktopState);
                        }
                    }

                    @Override
                    public void failed(Exception exception) {
                        LOG.error("Authentication failed", exception);
                        JOptionPane.showMessageDialog(null, exception.getMessage(), "Authentication failed", JOptionPane.ERROR_MESSAGE);
                    }

                    @Override
                    public void signedOut() {
                        // todo: display AuthenticationWindow again
                    }
                }, desktopPreferences, authenticationClient
        );
        if (null != credentials) {
            authenticationWindow.setCredentials(credentials);
        }
        desktopManager.add(authenticationWindow);
    }

    private void restoreWindows(DesktopPreferences.DesktopState desktopState) {
        LOG.info("Spec is " + desktopState.getSpec());
        if (null == desktopState.getWindowStates()) {
            LOG.info("No windows found");
            return;
        }
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

    private JComponent buildNavigation() {
        NavigationBar navigationBar = new NavigationBar(actions);
        actions.setEnabled(false);
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

    public static void main(String... args) throws FileStoreException {
        main = new JFrame(Constants.SIP_CREATOR_TITLE);
        final DesktopLauncher desktopLauncher = new DesktopLauncher();
        main.getContentPane().add(desktopLauncher.buildNavigation(), BorderLayout.CENTER);
        main.setExtendedState(Frame.MAXIMIZED_BOTH);
        main.setLocationRelativeTo(null);
        main.setJMenuBar(new NavigationMenu(desktopLauncher.actions));
        main.setVisible(true);
        main.addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent windowEvent) {
                        new Actions.ExitAction(desktopLauncher).actionPerformed(null);
                    }
                }
        );
    }


    public FileStore.DataSetStore getCurrentStore() {
        return currentStore;
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

}
