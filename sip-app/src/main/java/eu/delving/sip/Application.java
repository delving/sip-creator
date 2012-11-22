/*
 * Copyright 2011, 2012 Delving BV
 *
 * Licensed under the EUPL, Version 1.0 or? as soon they
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

package eu.delving.sip;

import eu.delving.groovy.GroovyCodeResource;
import eu.delving.metadata.*;
import eu.delving.schema.Fetcher;
import eu.delving.schema.util.FileSystemFetcher;
import eu.delving.sip.actions.ImportAction;
import eu.delving.sip.actions.SelectAnotherMappingAction;
import eu.delving.sip.actions.UnlockMappingAction;
import eu.delving.sip.actions.ValidateAction;
import eu.delving.sip.base.*;
import eu.delving.sip.files.*;
import eu.delving.sip.frames.AllFrames;
import eu.delving.sip.frames.WorkFrame;
import eu.delving.sip.menus.ExpertMenu;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.MappingModel;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.panels.HelpPanel;
import eu.delving.sip.panels.StatusPanel;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.params.ConnRouteParams;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.prefs.Preferences;

import static eu.delving.sip.base.KeystrokeHelper.*;
import static eu.delving.sip.base.SwingHelper.isDevelopmentMode;
import static eu.delving.sip.files.DataSetState.*;

/**
 * The main application, based on the SipModel and bringing everything together in a big frame with a central
 * desktop pane, with a side panel and panels along the bottom, as well as menus above.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class Application {
    private static final int DEFAULT_RESIZE_INTERVAL = 1000;
    private static final Dimension MINIMUM_DESKTOP_SIZE = new Dimension(800, 600);
    private File storageDirectory;
    private SipModel sipModel;
    private Action importAction;
    private Action validateAction;
    private JFrame home;
    private JDesktopPane desktop;
    private AllFrames allFrames;
    private VisualFeedback feedback;
    private StatusPanel statusPanel;
    private HelpPanel helpPanel;
    private Timer resizeTimer;
    private ExpertMenu expertMenu;
    private UnlockMappingAction unlockMappingAction;
    private SelectAnotherMappingAction selectAnotherMappingAction;

    private Application(final File storageDir) throws StorageException {
        this.storageDirectory = storageDir;
        GroovyCodeResource groovyCodeResource = new GroovyCodeResource(getClass().getClassLoader());
        final ImageIcon backgroundIcon = new ImageIcon(getClass().getResource("/delving-background.png"));
        desktop = new JDesktopPane() {
            public void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(backgroundIcon.getImage(), 0, 0, desktop);
            }
        };
        desktop.setMinimumSize(new Dimension(MINIMUM_DESKTOP_SIZE));
        resizeTimer = new Timer(DEFAULT_RESIZE_INTERVAL, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                resizeTimer.stop();
                for (JInternalFrame frame : desktop.getAllFrames()) {
                    if (frame instanceof FrameBase) {
                        ((FrameBase) frame).ensureOnScreen();
                    }
                }
            }
        });
        desktop.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent componentEvent) {
                resizeTimer.restart();
            }
        });
        Preferences preferences = Preferences.userNodeForPackage(SipModel.class);
        feedback = new VisualFeedback(home, desktop, preferences);
        HttpClient httpClient = createHttpClient(String.format("http://%s", StorageFinder.getHostPort(storageDirectory)));
        Fetcher fetcher = isDevelopmentMode() ? new FileSystemFetcher(false) : new HTTPSchemaFetcher(httpClient);
        ResolverContext context = new ResolverContext();
        Storage storage = new StorageImpl(storageDirectory, fetcher, new CachedResourceResolver(context));
        context.setStorage(storage);
        context.setHttpClient(httpClient);
        sipModel = new SipModel(desktop, storage, groovyCodeResource, feedback, preferences);
        CultureHubClient cultureHubClient = new CultureHubClient(sipModel, httpClient);
        expertMenu = new ExpertMenu(desktop, sipModel, cultureHubClient, allFrames);
        statusPanel = new StatusPanel(sipModel);
        home = new JFrame("Delving SIP Creator");
        home.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent componentEvent) {
                allFrames.getViewSelector().refreshView();
            }
        });
        JPanel content = (JPanel) home.getContentPane();
        content.setFocusable(true);
        allFrames = new AllFrames(sipModel, cultureHubClient, content);
        desktop.setBackground(new Color(190, 190, 200));
        helpPanel = new HelpPanel(sipModel, httpClient);
        content.add(desktop, BorderLayout.CENTER);
        sipModel.getMappingModel().addChangeListener(new MappingModel.ChangeListener() {
            @Override
            public void lockChanged(MappingModel mappingModel, final boolean locked) {
                sipModel.exec(new Swing() {
                    @Override
                    public void run() {
                        unlockMappingAction.setEnabled(locked);
                    }
                });
            }

            @Override
            public void functionChanged(MappingModel mappingModel, MappingFunction function) {
            }

            @Override
            public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping, NodeMappingChange change) {
            }

            @Override
            public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            }

            @Override
            public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            }

            @Override
            public void populationChanged(MappingModel mappingModel, RecDefNode node) {
            }
        });
        importAction = new ImportAction(desktop, sipModel);
        attachAccelerator(importAction, home);
        validateAction = new ValidateAction(sipModel, allFrames.prepareForInvestigation(desktop));
        unlockMappingAction = new UnlockMappingAction(sipModel);
        attachAccelerator(unlockMappingAction, home);
        selectAnotherMappingAction = new SelectAnotherMappingAction(sipModel);
        attachAccelerator(selectAnotherMappingAction, home);
        content.add(createStatePanel(), BorderLayout.SOUTH);
        content.add(allFrames.getSidePanel(), BorderLayout.WEST);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        screen.height -= 30;
        home.setSize(screen);
        home.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        ImageIcon logo = new ImageIcon(getClass().getResource("/sip-creator-logo.png"));
        home.setIconImage(logo.getImage());
        home.setJMenuBar(createMenuBar());
        home.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                quit();
            }
        });
        home.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        sipModel.getDataSetModel().addListener(new DataSetModel.SwingListener() {
            @Override
            public void stateChanged(DataSetModel model, DataSetState state) {
                statusPanel.setState(state);
                switch (state) {
                    case ABSENT:
                        sipModel.exec(new Work() {
                            @Override
                            public void run() {
                                sipModel.getDataSetFacts().set(null);
                                sipModel.getStatsModel().setStatistics(null);
                            }

                            @Override
                            public Job getJob() {
                                return Job.CLEAR_FACTS_STATS;
                            }
                        });
                        home.setTitle("Delving SIP Creator");
                        sipModel.seekReset();
                        break;
                    default:
                        DataSetModel dataSetModel = sipModel.getDataSetModel();
                        home.setTitle(String.format(
                                "Delving SIP Creator - [%s -> %s]",
                                dataSetModel.getDataSet().getSpec(), dataSetModel.getPrefix().toUpperCase()
                        ));
                        sipModel.getReportFileModel().refresh();
                        break;
                }
            }
        });
        attachAccelerator(new QuitAction(), home);
        attachAccelerator(statusPanel.getButtonAction(), home);
    }

    private JPanel createStatePanel() {
        statusPanel.setReaction(ABSENT, allFrames.prepareForNothing());
        statusPanel.setReaction(NO_DATA, importAction);
        statusPanel.setReaction(IMPORTED, new InputAnalyzer());
        statusPanel.setReaction(ANALYZED_IMPORT, allFrames.prepareForDelimiting());
        statusPanel.setReaction(DELIMITED, new ConvertPerformer());
        statusPanel.setReaction(SOURCED, new InputAnalyzer());
        statusPanel.setReaction(ANALYZED_SOURCE, allFrames.prepareForMapping(desktop));
        statusPanel.setReaction(MAPPING, validateAction);
        statusPanel.setReaction(VALIDATED, allFrames.getUploadAction());
        JPanel p = new JPanel(new GridLayout(1, 0, 6, 6));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createBevelBorder(0),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));
        p.add(statusPanel);
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(createWorkPanel(), BorderLayout.CENTER);
        rightPanel.add(createButtonPanel(), BorderLayout.WEST);
        p.add(rightPanel);
        return p;
    }

    private JPanel createButtonPanel() {
        JPanel p = new JPanel(new GridLayout(0, 1));
        JButton b;
        p.add(b = new JButton(unlockMappingAction));
        b.setHorizontalAlignment(JButton.LEFT);
        p.add(b = new JButton(selectAnotherMappingAction));
        b.setHorizontalAlignment(JButton.LEFT);
        p.add(b = new JButton(importAction));
        b.setHorizontalAlignment(JButton.LEFT);
        return p;
    }

    private JPanel createWorkPanel() {
        JPanel workPanel = new JPanel(new BorderLayout());
        final WorkFrame workFrame = allFrames.getWorkFrame();
        workPanel.add(AllFrames.miniScrollV("Work", workFrame.getMiniList()));
        workFrame.getMiniList().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting() || workFrame.getMiniList().isSelectionEmpty()) return;
                workFrame.openFrame();
            }
        });
        return workPanel;
    }

    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.add(allFrames.getViewMenu());
        bar.add(expertMenu);
        bar.add(createHelpMenu());
        return bar;
    }

    private JMenu createHelpMenu() {
        JMenu menu = new JMenu("Help");
        final JCheckBoxMenuItem item = new JCheckBoxMenuItem("Show Help Panel");
        item.setAccelerator(MENU_H);
        item.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent itemEvent) {
                helpPanel.initialize();
                if (item.isSelected()) {
                    home.getContentPane().add(helpPanel, BorderLayout.EAST);
                }
                else {
                    home.getContentPane().remove(helpPanel);
                }
                home.getContentPane().validate();
                allFrames.getViewSelector().refreshView();
            }
        });
        menu.add(item);
        return menu;
    }

    private class InputAnalyzer implements Swing {
        @Override
        public void run() {
            sipModel.analyzeFields();
        }
    }

    private class ConvertPerformer implements Swing {
        @Override
        public void run() {
            sipModel.convertSource();
        }
    }

    private HttpClient createHttpClient(String serverUrl) {
        final int CONNECTION_TIMEOUT = 1000 * 60 * 30;
        HttpParams httpParams = new BasicHttpParams();
        HttpConnectionParams.setSoTimeout(httpParams, CONNECTION_TIMEOUT);
        HttpConnectionParams.setConnectionTimeout(httpParams, CONNECTION_TIMEOUT);
        try {
            java.util.List<Proxy> proxies = ProxySelector.getDefault().select(new URI(serverUrl));
            for (Proxy proxy : proxies) {
                if (proxy.type() != Proxy.Type.HTTP) continue;
                InetSocketAddress addr = (InetSocketAddress) proxy.address();
                String host = addr.getHostName();
                int port = addr.getPort();
                HttpHost httpHost = new HttpHost(host, port);
                ConnRouteParams.setDefaultProxy(httpParams, httpHost);
            }
        }
        catch (URISyntaxException e) {
            throw new RuntimeException("Bad address: " + serverUrl, e);
        }
        ThreadSafeClientConnManager threaded = new ThreadSafeClientConnManager();
        return new DefaultHttpClient(threaded, httpParams);
    }

    private void quit() {
        if (!sipModel.getWorkModel().isEmpty()) {
            boolean exitAnyway = feedback.confirm(
                    "Busy",
                    "There are jobs busy, are you sure you want to exit?"
            );
            if (exitAnyway) return;
        }
        EventQueue.invokeLater(LAUNCH);
    }

    private void destroy() {
        sipModel.shutdown();
        resizeTimer.stop();
        home.setVisible(false);
        home.dispose();
    }

    private class ResolverContext implements CachedResourceResolver.Context {
        private Storage storage;
        private HttpClient httpClient;

        public void setStorage(Storage storage) {
            this.storage = storage;
        }

        public void setHttpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
        }

        @Override
        public String get(String url) {
            try {
                HttpGet get = new HttpGet(url);
                HttpResponse response = httpClient.execute(get);
                StatusLine line = response.getStatusLine();
                if (line.getStatusCode() != HttpStatus.SC_OK) {
                    throw new IOException(String.format(
                            "HTTP Error %s (%s) on %s",
                            line.getStatusCode(), line.getReasonPhrase(), url
                    ));
                }
                return EntityUtils.toString(response.getEntity());
            }
            catch (Exception e) {
                throw new RuntimeException("Fetching problem: "+url, e);
            }
        }

        @Override
        public File file(String systemId) {
            return storage.cache(systemId.replaceAll("[/:]", "_"));
        }
    }

    private class QuitAction extends AbstractAction {

        private QuitAction() {
            super("Quit");
            putValue(Action.ACCELERATOR_KEY, MENU_W);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            quit();
        }
    }

    private static StorageFinder storageFinder = new StorageFinder();

    private static class LaunchAction implements Runnable {
        private Application application;

        @Override
        public void run() {
            File storageDirectory;
            if (application != null) {
                application.destroy();
                storageDirectory = storageFinder.getStorageDirectory(application.storageDirectory);
            }
            else {
                storageDirectory = storageFinder.getStorageDirectory(null);
            }
            if (storageDirectory == null) {
                System.exit(0);
            }
            try {
                application = new Application(storageDirectory);
                application.home.setVisible(true);
                application.allFrames.initiate();
            }
            catch (StorageException e) {
                JOptionPane.showMessageDialog(null, "Unable to create the storage directory");
                e.printStackTrace();
            }
        }
    }

    private static LaunchAction LAUNCH = new LaunchAction();

    public static void main(final String[] args) throws StorageException {
        storageFinder.setArgs(args);
        EventQueue.invokeLater(LAUNCH);
    }
}
