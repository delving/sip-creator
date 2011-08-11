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
import eu.delving.sip.FileStore;
import eu.delving.sip.FileStoreException;
import eu.delving.sip.FileStoreImpl;
import eu.delving.sip.desktop.listeners.DataSetChangeListener;
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
    private NavigationBar navigationBar;
    private User user;
    private AuthenticationWindow authenticationWindow;
    private DesktopPreferences.Credentials credentials;
    private FileStore.DataSetStore dataSet;
    private Actions actions;
    private static JFrame main;
    private SipModel sipModel;
    private AuthenticationClient authenticationClient = new AuthenticationClient();

    private DataSetChangeListener dataSetChangeListener = new DataSetChangeListener() {

        @Override
        public void dataSetIsChanging(FileStore.DataSetStore dataSet) {
            actions.setEnabled(false);
        }

        @Override
        public void dataSetHasChanged(FileStore.DataSetStore dataSet) {
            DesktopLauncher.this.dataSet = dataSet;
            LOG.info("Changed to " + dataSet.getSpec());
            actions.setEnabled(true);
            main.setTitle(String.format("%s - Data set %s", Constants.SIP_CREATOR_TITLE, dataSet.getSpec()));
        }

        @Override
        public void dataSetChangeCancelled(FileStore.DataSetStore dataSet) {
            actions.setEnabled(true);
        }
    };

    private GroovyCodeResource groovyCodeResource;
    private MetadataModel metadataModel;
    private FileStore fileStore;
    private UserNotifier userNotifier;

    private File getFileStoreDirectory() throws FileStoreException {
        File fileStore = new File(System.getProperty("user.home"), "/sip-creator-file-store");
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
        return fileStore;
    }

    public DesktopLauncher() throws FileStoreException {
        MetadataModel metadataModel = loadMetadataModel();
        File fileStoreDirectory = getFileStoreDirectory();
        FileStore fileStore = new FileStoreImpl(fileStoreDirectory, metadataModel);
        groovyCodeResource = new GroovyCodeResource();
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
        desktopManager = new DesktopManager(dataSetChangeListener, sipModel);
        actions = new Actions(desktopManager);
        desktopPreferences = new DesktopPreferencesImpl(getClass());
        DesktopLauncher.this.credentials = desktopPreferences.loadCredentials();
        DesktopLauncher.this.desktopState = desktopPreferences.loadDesktopState();
        buildLayout();
    }

    private void buildLayout() {
        authenticationWindow = new AuthenticationWindow(
                new AuthenticationWindow.Listener() {

                    @Override
                    public void success(User user) {
                        DesktopLauncher.this.user = user;
                        actions.setEnabled(true);
                        if (null != desktopState) {
                            restoreWindows(desktopState);
                        }
                        if (null != dataSet) {
                            dataSetChangeListener.dataSetHasChanged(dataSet);
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
                }, desktopPreferences
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
        navigationBar = new NavigationBar(actions);
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
                        switch (JOptionPane.showConfirmDialog(main, Constants.CLOSE, Constants.CLOSE, JOptionPane.YES_NO_OPTION)) {
                            case JOptionPane.NO_OPTION:
                                return;
                            case JOptionPane.YES_OPTION:
                                if (null == desktopLauncher.user) {
                                    System.exit(0);
                                }
                                List<WindowState> allWindowStates = desktopLauncher.desktopManager.getWindowStates();
                                desktopLauncher.getDesktopPreferences().saveDesktopState(new DesktopStateImpl("SPEC", allWindowStates)); // todo: spec name
                                System.exit(0);
                                break;

                        }
                    }
                }
        );
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
