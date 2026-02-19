/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.sip;

import eu.delving.groovy.GroovyCodeResource;
import eu.delving.metadata.CachedResourceResolver;
import eu.delving.metadata.RecMapping;
import eu.delving.schema.SchemaRepository;
import eu.delving.sip.actions.UnlockMappingAction;
import eu.delving.sip.actions.ValidateAction;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.NetworkClient;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.base.VisualFeedback;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.HomeDirectory;
import eu.delving.sip.files.SchemaFetcher;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.files.StorageImpl;
import eu.delving.sip.frames.AllFrames;
import eu.delving.sip.frames.LogFrame;
import eu.delving.sip.frames.RemoteDataSetFrame;
import eu.delving.sip.menus.ExpertMenu;
import eu.delving.sip.menus.FileMenu;
import eu.delving.sip.menus.HelpMenu;
import eu.delving.sip.menus.ThemeMenu;
import eu.delving.sip.model.*;
import eu.delving.sip.model.MappingModel.ChangeListenerAdapter;
import eu.delving.sip.panels.StatusPanel;
import eu.delving.sip.panels.WorkPanel;
import io.sentry.Sentry;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.apache.jena.query.ARQ;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.apache.jena.sys.JenaSystem;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static eu.delving.sip.base.HttpClientFactory.createHttpClient;
import static eu.delving.sip.base.KeystrokeHelper.MENU_W;
import static eu.delving.sip.base.KeystrokeHelper.attachAccelerator;
import static eu.delving.sip.files.DataSetState.ABSENT;
import static eu.delving.sip.files.DataSetState.ANALYZED_SOURCE;
import static eu.delving.sip.files.DataSetState.MAPPING;
import static eu.delving.sip.files.DataSetState.PROCESSED;
import static eu.delving.sip.files.DataSetState.SOURCED;
import static eu.delving.sip.files.Storage.*;

/**
 * The main application, based on the SipModel and bringing everything together
 * in a big frame with a central
 * desktop pane, with a side panel and panels along the bottom, as well as menus
 * above.
 *
 *
 */

public class Application {
    public static String version;
    public static String build;
    private SipModel sipModel;
    private Action validateAction;
    private JFrame home;
    private JDesktopPane desktop;
    private AllFrames allFrames;
    private VisualFeedback feedback;
    private StatusPanel statusPanel;
    private FileMenu fileMenu;
    private ExpertMenu expertMenu;
    private ThemeMenu themeMenu;
    private HelpMenu helpMenu;
    private CreateSipZipAction createSipZipAction;
    private UnlockMappingAction unlockMappingAction;
    private static SipProperties appProperties = new SipProperties(true);
    private static final boolean isTelemetryIncluded = Application.class.getResource("/sentry.properties") != null;
    private static boolean isInitialLaunch = true;

    private Application() throws StorageException {
        // Differ between the first Application instantiation and re-launches (e.g., for switching projects)
        boolean isInitialLaunch = Application.isInitialLaunch;
        Application.isInitialLaunch = false;

        // Make sure Jena gets initialized properly, including ARQ
        // (avoid problem with ARQ.getContext() being null in executable .jar)
        if (isInitialLaunch) {
            JenaSystem.init();
            ARQ.init();
        }

        // Initialize look-and-feel
        String themeMode = appProperties.getProp().getProperty(THEME_MODE, "light");
        if (isInitialLaunch) {
            try {
                UIManager.setLookAndFeel("dark".equals(themeMode) ? new FlatDarkLaf() : new FlatLightLaf());
            } catch (Exception ex) {
                System.err.println("Failed to initialize FlatLaf look-and-feel");
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception e) {
                    // Ignore
                }
            }
        }

        // Initialize a temporary frame for use with any modal dialogs
        // (e.g., to make sure that there is a taskbar icon)
        JFrame startupFrame = new JFrame();
        startupFrame.setTitle(titleString());
        startupFrame.setUndecorated(true);
        startupFrame.setLocationRelativeTo(null);
        startupFrame.setVisible(true);

        // Initialize telemetry
        String telemetryEnabled = appProperties.getProp().getProperty(TELEMETRY_ENABLED);
        if (isTelemetryIncluded && telemetryEnabled == null && isInitialLaunch) {
            Object[] options = { "Yes, enable", "No, thanks", "Cancel" };
            int telemetryOption = JOptionPane.showOptionDialog(startupFrame,
                    "This application includes telemetry.\n\n" +
                            "This means that telemetry data may be automatically submitted, including errors and metrics. " +
                            "The data may also include personally identifiable information (PII) such as your IP address.\n\n" +
                            "Do you want telemetry to be enabled? The answer can be changed later in the Help menu.",
                    titleString(),
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[2]);
            switch (telemetryOption) {
                case 0:
                    appProperties.getProp().setProperty(TELEMETRY_ENABLED, Boolean.toString(true));
                    appProperties.saveProperties();
                    initSentry("gui");
                    break;
                case 1:
                    appProperties.getProp().setProperty(TELEMETRY_ENABLED, Boolean.toString(false));
                    appProperties.saveProperties();
                    break;
                case 2:
                case JOptionPane.CLOSED_OPTION:
                default:
                    // Decide later
                    break;
            }
        }

        // Initialize project
        if (!showProjectDialog(startupFrame)) return;

        // Transition from pre-startup to actual SIP-Creator startup
        startupFrame.setVisible(false);
        startupFrame.dispose();
        final File storageDir = HomeDirectory.getWorkDir();
        // Share app SipProperties instance for default (single) project to avoid instances overwriting changes
        // (legacy - should normally be multi-project and different files from now on)
        final SipProperties sipProperties = SipProperties.isSameAsAppPropertiesPath(
                SipProperties.getSipPropertiesPath()) ? appProperties : new SipProperties();

        // Initialize UI
        desktop = new JDesktopPane();
        desktop.setMinimumSize(new Dimension(800, 600));
        feedback = new VisualFeedback(home, desktop, sipProperties.getProp());

        // Initialize SipModel
        GroovyCodeResource groovyCodeResource = new GroovyCodeResource(getClass().getClassLoader());
        // todo: be sure to set this
        String serverUrl = sipProperties.getProp().getProperty(NARTHEX_URL, "http://delving.org/narthex");
        HttpClient httpClient = createHttpClient(serverUrl).build();
        SchemaRepository schemaRepository;
        try {
            schemaRepository = new SchemaRepository(new SchemaFetcher(httpClient));
        } catch (IOException e) {
            throw new StorageException("Unable to create Schema Repository", e);
        }
        ResolverContext context = new ResolverContext();
        Storage storage = new StorageImpl(storageDir, sipProperties.getProp(), schemaRepository, new CachedResourceResolver(context));
        context.setStorage(storage);
        context.setHttpClient(httpClient);
        sipModel = new SipModel(desktop, storage, groovyCodeResource, feedback, sipProperties, appProperties);

        NetworkClient networkClient = new NetworkClient(sipModel, new NetworkClient.NarthexCredentials() {
            private Properties props = sipProperties.getProp();

            @Override
            public boolean areSet() {
                return !(narthexUrl().isEmpty() && narthexUser().isEmpty() && narthexPassword().isEmpty());
            }

            @Override
            public void ask() {
                Map<String, String> fields = new TreeMap<>();
                fields.put(NARTHEX_URL, narthexUrl());
                fields.put(NARTHEX_USERNAME, narthexUser());
                fields.put(NARTHEX_PASSWORD, narthexPassword());

                if (sipModel.getFeedback().getNarthexCredentials(fields)) {
                    props.setProperty(NARTHEX_URL, fields.get(NARTHEX_URL));
                    props.setProperty(NARTHEX_USERNAME, fields.get(NARTHEX_USERNAME));
                    props.setProperty(NARTHEX_PASSWORD, fields.get(NARTHEX_PASSWORD));
                    sipProperties.saveProperties();
                }
            }

            @Override
            public String narthexUrl() {
                return props.getProperty(NARTHEX_URL, "http://delving.org/narthex").replaceAll("[/#]+$", "").trim();
            }

            @Override
            public String narthexUser() {
                return props.getProperty(NARTHEX_USERNAME, "admin").trim();
            }

            @Override
            public String narthexPassword() {
                return props.getProperty(NARTHEX_PASSWORD, "").trim();
            }
        });

        createSipZipAction = new CreateSipZipAction();
        statusPanel = new StatusPanel(sipModel);
        home = new JFrame();
        home.setTitle(titleString());
        home.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent componentEvent) {
                allFrames.getViewSelector().refreshView();
            }
        });
        JPanel content = (JPanel) home.getContentPane();
        content.setLayout(new BorderLayout());
        content.setFocusable(true);
        FrameBase dataSetFrame = new RemoteDataSetFrame(sipModel, networkClient);
        LogFrame logFrame = new LogFrame(sipModel);
        feedback.setLog(logFrame.getLog());
        allFrames = new AllFrames(sipModel, content, dataSetFrame, logFrame);
        for (FrameBase frame : allFrames.getFrames()) {
            frame.setTheme(themeMode);
        }
        fileMenu = new FileMenu(sipModel, allFrames, e -> quit(true), e -> quit());
        expertMenu = new ExpertMenu(sipModel, allFrames);
        themeMenu = new ThemeMenu(sipModel, allFrames);
        helpMenu = new HelpMenu(sipModel, allFrames, isTelemetryIncluded);
        content.add(desktop, BorderLayout.CENTER);
        sipModel.getMappingModel().addChangeListener(new ChangeListenerAdapter() {
            @Override
            public void lockChanged(MappingModel mappingModel, final boolean locked) {
            sipModel.exec(() -> {
                unlockMappingAction.setEnabled(locked);
            });
            }
        });
        validateAction = new ValidateAction(sipModel, allFrames.prepareForInvestigation(desktop));
        unlockMappingAction = new UnlockMappingAction(sipModel);
        UnlockMappingAction unlockMappingAction = this.unlockMappingAction;
        attachAccelerator(unlockMappingAction, home);
        content.add(createStatePanel(), BorderLayout.SOUTH);
        content.add(allFrames.getSidePanel(), BorderLayout.WEST);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        screen.height = (int) (screen.height / 1.5f);
        screen.width = (int) (screen.width / 1.5f);
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
        sipModel.getDataSetModel().addListener((model, state) -> {
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
                            dataSetModel.getDataSet().getSpec(), dataSetModel.getPrefix().toUpperCase()));
                    break;
            }
        });
        attachAccelerator(new QuitAction(), home);
        attachAccelerator(statusPanel.getButtonAction(), home);
    }

    private boolean showProjectDialog(JFrame startupFrame) {
        final String pocketMapperDirName = HomeDirectory.POCKETMAPPER_DIR_NAME;
        final String workDirName = HomeDirectory.WORK_DIR_NAME;
        final String sipPropertiesFileName = SipProperties.FILE_NAME;
        boolean isOpeningProject = true;
        while (isOpeningProject) {
            class PocketMapperProject {
                final File dir;
                final String name;

                PocketMapperProject(File dir) {
                    this.dir = dir;
                    this.name = dir.getName();
                }

                PocketMapperProject(File dir, String name) {
                    this.dir = dir;
                    this.name = name;
                }

                @Override
                public String toString() {
                    return name;
                }
            }
            File pocketMapper = HomeDirectory.WORKSPACE_DIR; // use actual, parent PocketMapper dir
            File[] projectDirs = pocketMapper.listFiles(pathname -> pathname.isDirectory() &&
                    (new File(pathname, sipPropertiesFileName).isFile()
                            || new File(pathname, pocketMapperDirName).isDirectory()));
            DefaultListModel<PocketMapperProject> projectListModel = new DefaultListModel<>();
            PocketMapperProject defaultProject = new PocketMapperProject(pocketMapper, "Default project (single-project PocketMapper)");
            PocketMapperProject newProject = new PocketMapperProject(pocketMapper, "New project");
            PocketMapperProject externalProject = new PocketMapperProject(null, "External project");
            if (new File(pocketMapper, workDirName).isDirectory() && !new File(pocketMapper,
                    workDirName + File.separator + sipPropertiesFileName).exists()) {
                // Only show "Default project" if it exists (i.e., there is a work dir which isn't a project dir)
                projectListModel.addElement(defaultProject);
            }
            projectListModel.addAll(Arrays.asList(newProject, externalProject));
            projectListModel.addAll(Arrays.asList(projectDirs).stream().sorted().map(
                    file -> new PocketMapperProject(file)).toList());
            JList<PocketMapperProject> projectList = new JList<>(projectListModel);
            projectList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            projectList.setSelectedIndex(0);
            AtomicBoolean isDoubleClick = new AtomicBoolean(false);
            projectList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() > 1) { // Double-click
                        isDoubleClick.set(true);
                        SwingUtilities.windowForComponent(projectList).dispose();
                    }
                }
            });
            JScrollPane scrollProjectList = new JScrollPane(projectList,
                    JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                    JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            scrollProjectList.setPreferredSize(new Dimension(600, 400));
            JLabel selectProjectLabel = new JLabel("Select a PocketMapper project:");
            selectProjectLabel.setBorder(new EmptyBorder(0,10,10,10));
            JButton folderButton = new JButton("Show in folder");
            folderButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    Desktop desktop = Desktop.getDesktop();
                    PocketMapperProject showProject = projectList.getSelectedValue();
                    if (showProject != null && showProject.dir != null) {
                        try {
                            desktop.open(showProject.dir);
                        } catch (IOException ex) {
                            // Ignore
                        }
                    }
                }
            });
            JPanel message = new JPanel();
            message.setLayout(new GridBagLayout());
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.weightx = 1;
            constraints.gridx = 0;
            message.add(selectProjectLabel, constraints);
            message.add(scrollProjectList, constraints);
            message.add(folderButton, constraints);
            message.addAncestorListener(new AncestorListener() {
                @Override
                public void ancestorAdded(AncestorEvent event) {
                }
                @Override
                public void ancestorRemoved(AncestorEvent event) {
                }
                @Override
                public void ancestorMoved(AncestorEvent event) {
                    // Workaround to ensure the initial focus is on the project list
                    Timer timer = new Timer(10, null);
                    timer.addActionListener(e -> {
                        if (projectList.hasFocus()) {
                            timer.stop();
                        }
                        SwingUtilities.invokeLater(projectList::requestFocusInWindow);
                    });
                    timer.start();
                }
            });
            Object[] options = {"Open", "Edit", "Cancel"};
            int projectOption = JOptionPane.showOptionDialog(startupFrame,
                    message,
                    titleString(),
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    options,
                    options[0]);
            PocketMapperProject selectedProject = projectList.getSelectedValue();
            if (isDoubleClick.get()) {
                projectOption = 0; // project list being double-clicked interpreted as "Open"
            }
            switch (projectOption) {
                case 0:
                case 1:
                    if (selectedProject == null) {
                        return false;
                    }
                    File projectDir = selectedProject.dir;
                    if (selectedProject == externalProject) {
                        // Open an external project
                        JFileChooser chooser = new JFileChooser();
                        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                        if (chooser.showOpenDialog(startupFrame) == JFileChooser.APPROVE_OPTION) {
                            projectDir = chooser.getSelectedFile();
                        } else {
                            break;
                        }
                    }
                    File nestedPocketMapper = new File(projectDir, pocketMapperDirName);
                    File workspaceDir = projectDir;
                    if (selectedProject != defaultProject && nestedPocketMapper.isDirectory()) {
                        // Support old multi-project setup achieved by setting user.home
                        workspaceDir = nestedPocketMapper;
                    }
                    File sipPropertiesFile = new File(workspaceDir, sipPropertiesFileName);
                    if (selectedProject == newProject || projectOption == 1) {
                        // Open new (or edit) project
                        String projectName = "";
                        String propertiesText = "orgID=";
                        boolean isCreatingProject = true;
                        while (isCreatingProject) {
                            JPanel newMessage = new JPanel();
                            newMessage.setLayout(new GridBagLayout());
                            JLabel projectNameLabel = new JLabel("Project name (e.g., \"my-test\"):");
                            projectNameLabel.setBorder(new EmptyBorder(10, 0, 5, 0));
                            JTextField projectNameText = new JTextField(projectName, 30);
                            JLabel propertiesLabel = new JLabel("Provided SIP-Creator properties:");
                            propertiesLabel.setBorder(new EmptyBorder(10, 0, 5, 0));
                            JTextArea propertiesTextArea = new JTextArea(propertiesText);
                            propertiesTextArea.setLineWrap(true);
                            propertiesTextArea.setWrapStyleWord(true);
                            JScrollPane propertiesScrollPane = new JScrollPane(propertiesTextArea,
                                    JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                                    JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
                            propertiesScrollPane.setPreferredSize(new Dimension(600, 200));
                            JButton pasteButton = new JButton("Paste from clipboard");
                            pasteButton.addActionListener(new ActionListener() {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    propertiesTextArea.setText("");
                                    propertiesTextArea.paste();
                                }
                            });
                            if (selectedProject != newProject) {
                                // Editing an existing project
                                projectNameText.setText(projectDir.getName());
                                projectNameText.setEditable(false);
                                Properties currentProperties = new Properties();
                                try {
                                    currentProperties.load(new FileInputStream(sipPropertiesFile));
                                    StringWriter stringWriter = new StringWriter();
                                    currentProperties.store(stringWriter, null);
                                    propertiesTextArea.setText(stringWriter.toString());
                                } catch (IOException e) {
                                    JOptionPane.showMessageDialog(startupFrame,
                                            "Error loading SIP-Creator properties: " + e.getMessage(),
                                            titleString(),
                                            JOptionPane.WARNING_MESSAGE);
                                    break;
                                }
                            }
                            newMessage.add(projectNameLabel, constraints);
                            newMessage.add(projectNameText, constraints);
                            newMessage.add(propertiesLabel, constraints);
                            newMessage.add(propertiesScrollPane, constraints);
                            newMessage.add(pasteButton, constraints);
                            Object[] newOptions = {selectedProject == newProject ? "Create" : "Save", "Cancel"};
                            int newOption = JOptionPane.showOptionDialog(startupFrame,
                                    newMessage,
                                    titleString(),
                                    JOptionPane.DEFAULT_OPTION,
                                    JOptionPane.PLAIN_MESSAGE,
                                    null,
                                    newOptions,
                                    newOptions[0]);
                            if (newOption == 0) {
                                projectName = projectNameText.getText();
                                propertiesText = propertiesTextArea.getText();
                                Properties projectProperties = new Properties();
                                try {
                                    projectProperties.load(new StringReader(propertiesText));
                                } catch (Exception e) {
                                    JOptionPane.showMessageDialog(startupFrame,
                                            "Error reading SIP-Creator properties: " + e.getMessage(),
                                            titleString(),
                                            JOptionPane.WARNING_MESSAGE);
                                }
                                if (!projectName.matches("^[A-Za-z][A-Za-z0-9\\-_]*$")) {
                                    JOptionPane.showMessageDialog(startupFrame,
                                            "Invalid project name. " +
                                                    "Please use A-Z, a-z, 0-9, -, and _, starting with a letter.",
                                            titleString(),
                                            JOptionPane.WARNING_MESSAGE);
                                } else {
                                    try {
                                        if (selectedProject == newProject) {
                                            HomeDirectory.createProject(projectName, projectProperties);
                                        } else {
                                            HomeDirectory.editProject(workspaceDir, projectProperties);
                                            if (SipProperties.isSameAsAppPropertiesPath(sipPropertiesFile)) {
                                                // The app properties were edited (e.g., for the default project)
                                                JOptionPane.showMessageDialog(startupFrame,
                                                        "The application will now close. " +
                                                                "Please start it again for changes to take effect.",
                                                        titleString(),
                                                        JOptionPane.INFORMATION_MESSAGE);
                                                return false; // close the application
                                            }
                                        }
                                        isCreatingProject = false;
                                        if (projectOption == 0) {
                                            // If New project and using the Open button (not Edit) we're done,
                                            // otherwise (if the Edit button was used) go back to selecting a project
                                            isOpeningProject = false;
                                        }
                                    } catch (Exception e) {
                                        JOptionPane.showMessageDialog(startupFrame,
                                                "Error creating project: " + e.getMessage(),
                                                titleString(),
                                                JOptionPane.WARNING_MESSAGE);
                                    }
                                }
                            } else {
                                isCreatingProject = false;
                            }
                        }
                    }  else {
                        // Open existing project
                        try {
                            HomeDirectory.openProject(workspaceDir);
                            isOpeningProject = false;
                        } catch (Exception e) {
                            JOptionPane.showMessageDialog(startupFrame,
                                    "Error opening project: " + e.getMessage(),
                                    titleString(),
                                    JOptionPane.WARNING_MESSAGE);
                        }
                    }
                    break;
                case JOptionPane.CLOSED_OPTION:
                default:
                    // Stop here
                    return false;
            }
        }
        return true; // continue with launch
    }

    public static String titleString() {
        if (version != null) {
            return String.format("SIP-Creator %s", version);
        } else {
            return "SIP-Creator Test";
        }
    }

    public static String buildString() {
        if (build != null) {
            return build;
        } else {
            return "Unknown build";
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
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        p.add(statusPanel);
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(new WorkPanel(sipModel), BorderLayout.CENTER);
        rightPanel.add(createButtonPanel(), BorderLayout.WEST);
        JPanel enforceMinimumHeight = new JPanel();
        enforceMinimumHeight.setPreferredSize(new Dimension(0, 150));
        rightPanel.add(enforceMinimumHeight, BorderLayout.EAST);
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
        bar.add(fileMenu);
        bar.add(allFrames.getViewMenu());
        bar.add(expertMenu);
        bar.add(themeMenu);
        bar.add(helpMenu);
        return bar;
    }

    private class InputAnalyzer implements Swing {
        @Override
        public void run() {
            sipModel.analyzeFields();
            // ensure that the record definition is reloaded
            reloadRecordDefinition(sipModel);
        }
    }

    private void reloadRecordDefinition(SipModel sipModel) {
        if (sipModel.getMappingModel().hasRecMapping()) {
            sipModel.exec(new Work.DataSetPrefixWork() {
                final MappingModel mm = sipModel.getMappingModel();
                final DataSetModel dsm = sipModel.getDataSetModel();

                @Override
                public String getPrefix() {
                    return sipModel.getMappingModel().getPrefix();
                }

                @Override
                public DataSet getDataSet() {
                    return sipModel.getDataSetModel().getDataSet();
                }

                @Override
                public Job getJob() {
                    return Job.RELOAD_MAPPING;
                }

                @Override
                public void run() {
                    try {
                        RecMapping recMapping = dsm.getRecMapping();
                        DataSet dataSet = dsm.getDataSet();
                        recMapping.validateMappings(new StatsModel.SourceTreeImpl(sipModel.getStatsModel()));
                        dataSet.setRecMapping(recMapping, true);
                        mm.setRecMapping(recMapping);
                    } catch (StorageException e) {
                        sipModel.getFeedback().alert("Cannot set the mapping", e);
                    }
                }
            });
        }
    }

    private void quit() {
        quit(false);
    }

    private void quit(boolean isRelaunch) {
        if (!sipModel.getWorkModel().isEmpty()) {
            boolean exitAnyway = feedback.confirm(
                "Busy",
                "There are jobs busy, are you sure you want to exit?");
            if (!exitAnyway)
                return;
        }
        Timer quitTimer = new Timer(0, null);
        quitTimer.setDelay(100); // Between-event delay
        AtomicInteger count = new AtomicInteger(); // Safeguard in case jobs don't stop
        quitTimer.addActionListener(event -> {
            if (!sipModel.getWorkModel().isEmpty() && count.incrementAndGet() < 100) {
                try {
                    WorkModel.JobContext job = sipModel.getWorkModel().getListModel().getElementAt(0);
                    WorkModel.ProgressIndicator progress = job.getProgressIndicator();
                    if (progress != null) {
                        progress.cancel();
                    }
                    return; // Check work model again next time
                } catch (Exception e) {
                    // E.g. index out of bounds
                }
            }
            quitTimer.stop();
            appProperties.saveProperties();
            sipModel.shutdown();
            if (isRelaunch) {
                EventQueue.invokeLater(LAUNCH);
            } else {
                System.exit(0);
            }
        });
        quitTimer.start();
    }

    private void destroy() {
        sipModel.shutdown();
        home.setVisible(false);
        home.dispose();
    }

    public static class ResolverContext implements CachedResourceResolver.Context {
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
                            line.getStatusCode(), line.getReasonPhrase(), url));
                }
                return EntityUtils.toString(response.getEntity());
            } catch (Exception e) {
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
            if (application != null)
                application.destroy();
            try {
                application = new Application();
                if (application.home == null) {
                    // Startup aborted - exit
                    System.exit(0);
                } else {
                    application.home.setVisible(true);
                    application.allFrames.initiate();
                }
            } catch (StorageException e) {
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
            if (sipModel.getDataSetModel().isEmpty())
                return;
            try {
                SipProperties sipProperties = new SipProperties();
                boolean sourceIncluded = Boolean.parseBoolean(
                    sipProperties.getProp().getProperty(SOURCE_INCLUDED, "false"));
                sipModel.getDataSetModel().createSipZip(sourceIncluded);
                sipModel.getFeedback().alert("A new SIP file has been created and is ready for upload.");
                allFrames.initiate();
            } catch (StorageException e) {
                sipModel.getFeedback().alert("Unable to create sip zip", e);
            }
        }
    }

    private static void readArtifact() {
        // Try to read the Maven artifact version
        URL resource = Application.class.getResource("/META-INF/maven/eu.delving/sip-app/pom.properties");
        if (resource != null) {
            try {
                Properties pomProperties = new Properties();
                pomProperties.load(resource.openStream());
                String pomVersion = pomProperties.getProperty("version");

                // Try to read build properties (set by the automatic build, see _scripts/prepare_build.sh)
                String gitCommitHash = null;
                resource = Application.class.getResource("/sip-app.properties");
                if (resource != null) {
                    try {
                        Properties buildProperties = new Properties();
                        buildProperties.load(resource.openStream());
                        gitCommitHash = buildProperties.getProperty("git-commit-hash-short");
                        build = buildProperties.getProperty("build-info");
                    } catch (Exception e) {
                        // Not important
                    }
                }

                if (pomVersion != null && pomVersion.endsWith("-SNAPSHOT") && gitCommitHash != null) {
                    version = String.format("%s (%s)", pomVersion, gitCommitHash);
                } else {
                    version = pomVersion;
                }
            } catch (Exception e) {
                System.err.println("Cannot read maven resource");
            }
        }
    }

    public static void init(String runMode) {
        readArtifact();
        
        // Initialize Jena subsystems to prevent RIOT.getContext() being null
        JenaSystem.init();
        ARQ.init();
        
        initSentry(runMode);
    }

    public static void initSentry(String runMode) {
        if (isTelemetryIncluded) {
            if ("true".equals(appProperties.getProp().getProperty(TELEMETRY_ENABLED))) {
                // Initialize Sentry using sentry.properties in classpath
                Sentry.init(options -> {
                    options.setEnableExternalConfiguration(true);
                    options.setRelease(version);
                    options.setTag("mode", runMode);
                });
            }
        }
    }

    public static void main(final String[] args) throws Exception {
        init("gui");

        String lcOSName = System.getProperty("os.name").toLowerCase();
        final boolean isMac = lcOSName.startsWith("mac os x");
        if (isMac) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.application.appearance", "system");
            System.setProperty("apple.eawt.quitStrategy", "CLOSE_ALL_WINDOWS");
            System.setProperty("apple.awt.application.name", "SIP-Creator");
            System.setProperty("com.apple.mrj.application.apple.menu.about.name", "SIP-Creator");
            // Workaround for JDK-8349701: Metal pipeline causes progressive font
            // rendering corruption on Apple Silicon (VolatileImage glyph cache bug).
            // Fall back to OpenGL which does not have this issue.
            if ("aarch64".equals(System.getProperty("os.arch"))) {
                System.setProperty("sun.java2d.metal", "false");
            }
        }

        // In (at least) the GTK system look and feel, for JDesktopPane there is a task
        // bar at the bottom
        // which blocks view of the frames - disable it
        UIManager.put("InternalFrame.useTaskBar", Boolean.FALSE);
        // UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        EventQueue.invokeLater(LAUNCH);
    }

}
