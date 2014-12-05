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
import eu.delving.metadata.CachedResourceResolver;
import eu.delving.metadata.MappingFunction;
import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.NodeMappingChange;
import eu.delving.metadata.RecDefNode;
import eu.delving.schema.SchemaRepository;
import eu.delving.sip.actions.UnlockMappingAction;
import eu.delving.sip.actions.ValidateAction;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.NetworkClient;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.base.VisualFeedback;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.files.HomeDirectory;
import eu.delving.sip.files.SchemaFetcher;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.files.StorageImpl;
import eu.delving.sip.frames.AllFrames;
import eu.delving.sip.frames.LogFrame;
import eu.delving.sip.frames.RemoteDataSetFrame;
import eu.delving.sip.menus.ExpertMenu;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.MappingModel;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.panels.StatusPanel;
import eu.delving.sip.panels.WorkPanel;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.prefs.Preferences;

import static eu.delving.sip.base.HttpClientFactory.createHttpClient;
import static eu.delving.sip.base.KeystrokeHelper.MENU_W;
import static eu.delving.sip.base.KeystrokeHelper.attachAccelerator;
import static eu.delving.sip.files.DataSetState.ABSENT;
import static eu.delving.sip.files.DataSetState.ANALYZED_SOURCE;
import static eu.delving.sip.files.DataSetState.MAPPING;
import static eu.delving.sip.files.DataSetState.PROCESSED;
import static eu.delving.sip.files.DataSetState.SOURCED;
import static eu.delving.sip.files.Storage.NARTHEX_PASSWORD;
import static eu.delving.sip.files.Storage.NARTHEX_URL;
import static eu.delving.sip.files.Storage.NARTHEX_USERNAME;

/**
 * The main application, based on the SipModel and bringing everything together in a big frame with a central
 * desktop pane, with a side panel and panels along the bottom, as well as menus above.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class Application {
    private static final int DEFAULT_RESIZE_INTERVAL = 1000;
    private static final Dimension MINIMUM_DESKTOP_SIZE = new Dimension(800, 600);
    private static String version;
    private SipModel sipModel;
    private Action validateAction;
    private JFrame home;
    private JDesktopPane desktop;
    private AllFrames allFrames;
    private VisualFeedback feedback;
    private StatusPanel statusPanel;
    private Timer resizeTimer;
    private ExpertMenu expertMenu;
    private CreateSipZipAction createSipZipAction;
    private UnlockMappingAction unlockMappingAction;

    private Application(final File storageDir) throws StorageException {
        GroovyCodeResource groovyCodeResource = new GroovyCodeResource(getClass().getClassLoader());
        desktop = new JDesktopPane();
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
        // todo: be sure to set this
        String serverUrl = preferences.get("serverUrl", "http://localhost:9000/narthex");
        HttpClient httpClient = createHttpClient(serverUrl);
        SchemaRepository schemaRepository;
        try {
            schemaRepository = new SchemaRepository(new SchemaFetcher(httpClient));
        }
        catch (IOException e) {
            throw new StorageException("Unable to create Schema Repository", e);
        }
        ResolverContext context = new ResolverContext();
        Storage storage = new StorageImpl(storageDir, schemaRepository, new CachedResourceResolver(context));
        context.setStorage(storage);
        context.setHttpClient(httpClient);
        sipModel = new SipModel(desktop, storage, groovyCodeResource, feedback, preferences);
        NetworkClient networkClient = new NetworkClient(sipModel, httpClient, new NetworkClient.NarthexCredentials() {

            @Override
            public boolean areSet() {
                return !(narthexUrl().isEmpty() || narthexUser().isEmpty());
            }

            @Override
            public void ask() {
                Map<String, String> fields = new TreeMap<String, String>();
                fields.put(NARTHEX_URL, narthexUrl());
                fields.put(NARTHEX_USERNAME, narthexUser());
                fields.put(NARTHEX_PASSWORD, narthexPassword());
                if (sipModel.getFeedback().getNarthexCredentials(fields)) {
                    sipModel.getPreferences().put(NARTHEX_URL, fields.get(NARTHEX_URL));
                    sipModel.getPreferences().put(NARTHEX_USERNAME, fields.get(NARTHEX_USERNAME));
                    sipModel.getPreferences().put(NARTHEX_PASSWORD, fields.get(NARTHEX_PASSWORD));
                }
            }

            @Override
            public String narthexUrl() {
                return sipModel.getPreferences().get(NARTHEX_URL, "").trim();
            }

            @Override
            public String narthexUser() {
                return sipModel.getPreferences().get(NARTHEX_USERNAME, "").trim();
            }

            @Override
            public String narthexPassword() {
                return sipModel.getPreferences().get(NARTHEX_PASSWORD, "").trim();
            }
        });
        createSipZipAction = new CreateSipZipAction();
        expertMenu = new ExpertMenu(desktop, sipModel, networkClient, allFrames);
        statusPanel = new StatusPanel(sipModel);
        home = new JFrame(titleString());
        home.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent componentEvent) {
                allFrames.getViewSelector().refreshView();
            }
        });
        JPanel content = (JPanel) home.getContentPane();
        content.setFocusable(true);
        FrameBase dataSetFrame = new RemoteDataSetFrame(sipModel, networkClient);
        LogFrame logFrame = new LogFrame(sipModel);
        feedback.setLog(logFrame.getLog());
        allFrames = new AllFrames(sipModel, content, dataSetFrame, logFrame);
        desktop.setBackground(new Color(190, 190, 200));
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
        validateAction = new ValidateAction(sipModel, allFrames.prepareForInvestigation(desktop));
        unlockMappingAction = new UnlockMappingAction(sipModel);
        attachAccelerator(unlockMappingAction, home);
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
                        home.setTitle(titleString());
                        sipModel.seekReset();
                        break;
                    default:
                        DataSetModel dataSetModel = sipModel.getDataSetModel();
                        home.setTitle(String.format(
                                titleString() + " - [%s -> %s]",
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

    private String titleString() {
        if (version != null) {
            return String.format("SIP-App %s", version);
        }
        else {
            return "SIP-App Test";
        }
    }

    private JPanel createStatePanel() {
        statusPanel.setReaction(ABSENT, allFrames.prepareForNothing());
        statusPanel.setReaction(SOURCED, new InputAnalyzer());
        statusPanel.setReaction(ANALYZED_SOURCE, allFrames.prepareForMapping(desktop));
        statusPanel.setReaction(MAPPING, validateAction);
        statusPanel.setReaction(PROCESSED, createSipZipAction);
        JPanel p = new JPanel(new GridLayout(1, 0, 6, 6));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createBevelBorder(0),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));
        p.add(statusPanel);
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(new WorkPanel(sipModel), BorderLayout.CENTER);
        rightPanel.add(createButtonPanel(), BorderLayout.WEST);
        p.add(rightPanel);
        return p;
    }

    private JPanel createButtonPanel() {
        JPanel p = new JPanel(new GridLayout(0, 1));
        JButton b;
        p.add(b = new JButton(unlockMappingAction));
        b.setHorizontalAlignment(JButton.LEFT);
        return p;
    }

    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.add(allFrames.getViewMenu());
        bar.add(expertMenu);
        return bar;
    }

    private class InputAnalyzer implements Swing {
        @Override
        public void run() {
            sipModel.analyzeFields();
        }
    }

    private void quit() {
        if (!sipModel.getWorkModel().isEmpty()) {
            boolean exitAnyway = feedback.confirm(
                    "Busy",
                    "There are jobs busy, are you sure you want to exit?"
            );
            if (exitAnyway) return;
        }
        System.exit(0);
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
                throw new RuntimeException("Fetching problem: " + url, e);
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

    private static class LaunchAction implements Runnable {
        private Application application;

        @Override
        public void run() {
            if (application != null) application.destroy();
            try {
                application = new Application(HomeDirectory.WORK_DIR);
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

    public class CreateSipZipAction extends AbstractAction {

        public CreateSipZipAction() {
            super("Create");
            putValue(Action.SMALL_ICON, SwingHelper.ICON_UPLOAD);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            setEnabled(false);
            if (sipModel.getDataSetModel().isEmpty()) return;
            try {
                sipModel.getDataSetModel().createSipZip();
                sipModel.getFeedback().alert("A new SIP file has been created and is ready for upload.");
                allFrames.initiate();
            }
            catch (StorageException e) {
                sipModel.getFeedback().alert("Unable to create sip zip", e);
            }
        }
    }

    private static String getPomVersion() {
        URL resource = Application.class.getResource("/META-INF/maven/eu.delving/sip-app/pom.properties");
        if (resource != null) {
            try {
                Properties pomProperties = new Properties();
                pomProperties.load(resource.openStream());
                return pomProperties.getProperty("version");
            }
            catch (Exception e) {
                System.err.println("Cannot read maven resource");
            }
        }
        return null;
    }

    public static void main(final String[] args) throws StorageException {
        version = getPomVersion();
        EventQueue.invokeLater(LAUNCH);
    }

}
