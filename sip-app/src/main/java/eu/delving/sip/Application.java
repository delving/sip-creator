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
import eu.delving.metadata.MappingFunction;
import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.NodeMappingChange;
import eu.delving.metadata.RecDefNode;
import eu.delving.sip.actions.DownloadAction;
import eu.delving.sip.actions.ImportAction;
import eu.delving.sip.actions.ReleaseAction;
import eu.delving.sip.actions.ValidateAction;
import eu.delving.sip.base.*;
import eu.delving.sip.files.*;
import eu.delving.sip.frames.AllFrames;
import eu.delving.sip.menus.DataSetMenu;
import eu.delving.sip.menus.ExpertMenu;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.Feedback;
import eu.delving.sip.model.MappingModel;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.panels.HelpPanel;
import eu.delving.sip.panels.StatusPanel;
import org.apache.amber.oauth2.common.exception.OAuthProblemException;
import org.apache.amber.oauth2.common.exception.OAuthSystemException;
import org.apache.commons.lang.StringUtils;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

import static eu.delving.sip.files.DataSetState.NO_DATA;

/**
 * The main application
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class Application {
    private static final int DEFAULT_RESIZE_INTERVAL = 1000;
    private static final Dimension MINIMUM_DESKTOP_SIZE = new Dimension(800, 600);
    private SipModel sipModel;
    private Action downloadAction, importAction, uploadAction, releaseAction, validateAction;
    private JFrame home;
    private JDesktopPane desktop;
    private DataSetMenu dataSetMenu;
    private OAuthClient oauthClient;
    private AllFrames allFrames;
    private VisualFeedback feedback;
    private StatusPanel statusPanel;
    private HelpPanel helpPanel;
    private Timer resizeTimer;
    private String instance;
    private ExpertMenu expertMenu;

    private Application(final File storageDirectory, String instance) throws StorageException {
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
        feedback = new VisualFeedback(desktop);
        CultureHubClient cultureHubClient = new CultureHubClient(new CultureHubClientContext(storageDirectory));
        Storage storage = new StorageImpl(storageDirectory, cultureHubClient.getHttpClient());
        sipModel = new SipModel(storage, groovyCodeResource, feedback, instance);
        expertMenu = new ExpertMenu(sipModel);
        statusPanel = new StatusPanel(sipModel);
        home = new JFrame("Delving SIP Creator");
        home.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent componentEvent) {
                allFrames.rebuildView();
            }
        });
        desktop.setBackground(new Color(190, 190, 200));
        allFrames = new AllFrames(desktop, sipModel, cultureHubClient);
        helpPanel = new HelpPanel(sipModel, cultureHubClient.getHttpClient());
        home.getContentPane().add(desktop, BorderLayout.CENTER);
        sipModel.getMappingModel().addChangeListener(new MappingModel.ChangeListener() {
            @Override
            public void lockChanged(MappingModel mappingModel, final boolean locked) {
                sipModel.exec(new Swing() {
                    @Override
                    public void run() {
                        dataSetMenu.getUnlockMappingAction().setEnabled(locked);
                        expertMenu.setEnabled(!locked);
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
        });
        downloadAction = new DownloadAction(desktop, sipModel, cultureHubClient);
        importAction = new ImportAction(desktop, sipModel);
        dataSetMenu = new DataSetMenu(sipModel);
        validateAction = new ValidateAction(desktop, sipModel, dataSetMenu, allFrames.prepareForInvestigation(desktop));
        uploadAction = allFrames.getUploadAction();
        releaseAction = new ReleaseAction(desktop, sipModel, cultureHubClient);
        home.getContentPane().add(createStatePanel(), BorderLayout.SOUTH);
        home.getContentPane().add(allFrames.getSidePanel(), BorderLayout.WEST);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        screen.height -= 30;
        home.setSize(screen);
        home.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        ImageIcon logo = new ImageIcon(getClass().getResource("/sip-creator-logo.png"));
        home.setIconImage(logo.getImage());
        oauthClient = new OAuthClient(
                cultureHubClient.getHttpClient(),
                StorageFinder.getHostPort(storageDirectory),
                StorageFinder.getUser(storageDirectory),
                new PasswordFetcher()
        );
        home.setJMenuBar(createMenuBar());
        home.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                quit();
            }
        });
        home.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
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
                        dataSetMenu.refreshAndChoose(null, null);
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
    }

    private boolean quit() {
        if (!sipModel.getWorkModel().isEmpty()) {
            boolean exitAnyway = feedback.confirm(
                    "Busy",
                    "There are jobs busy, are you sure you want to exit?"
            );
            if (exitAnyway) return false;
        }
        System.exit(0);
        return true;
    }

    private JPanel createStatePanel() {
        JPanel p = new JPanel(new BorderLayout(10,10));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createBevelBorder(0),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));
        statusPanel.setReaction(DataSetState.ABSENT, allFrames.prepareForNothing());
        statusPanel.setReaction(NO_DATA, importAction);
        statusPanel.setReaction(DataSetState.IMPORTED, new InputAnalyzer());
        statusPanel.setReaction(DataSetState.ANALYZED_IMPORT, allFrames.prepareForDelimiting());
        statusPanel.setReaction(DataSetState.DELIMITED, new ConvertPerformer());
        statusPanel.setReaction(DataSetState.SOURCED, new InputAnalyzer());
        statusPanel.setReaction(DataSetState.ANALYZED_SOURCE, allFrames.prepareForMapping(desktop));
        statusPanel.setReaction(DataSetState.MAPPING, validateAction);
        statusPanel.setReaction(DataSetState.VALIDATED, allFrames.getUploadAction());
        p.add(statusPanel, BorderLayout.CENTER);
        p.add(allFrames.getBigWindowsPanel(), BorderLayout.EAST);
        p.setPreferredSize(new Dimension(80, 80));
        return p;
    }

    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.add(createFileMenu());
        bar.add(dataSetMenu);
        bar.add(allFrames.getViewMenu());
        bar.add(allFrames.getFrameMenu());
        bar.add(expertMenu);
        bar.add(createHelpMenu());
        return bar;
    }

    private JMenu createHelpMenu() {
        JMenu menu = new JMenu("Help");
        final JCheckBoxMenuItem item = new JCheckBoxMenuItem("Show Help Panel");
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H, KeyEvent.CTRL_MASK));
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
                allFrames.rebuildView();
            }
        });
        menu.add(item);
        return menu;
    }

    private JMenu createFileMenu() {
        JMenu menu = new JMenu("File");
        menu.add(downloadAction);
        menu.add(importAction);
        menu.add(validateAction);
        menu.add(uploadAction);
        menu.add(releaseAction);
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

    private class CultureHubClientContext implements CultureHubClient.Context {

        private File storageDirectory;

        public CultureHubClientContext(File storageDirectory) {
            this.storageDirectory = storageDirectory;
        }

        @Override
        public String getUser() {
            return StorageFinder.getUser(storageDirectory);
        }

        @Override
        public String getServerUrl() {
            return String.format("http://%s/api/sip-creator", StorageFinder.getHostPort(storageDirectory));
        }

        @Override
        public String getAccessToken() throws OAuthSystemException, OAuthProblemException {
            return oauthClient.getToken();
        }

        @Override
        public void invalidateTokens() {
            oauthClient.invalidateTokens();
        }

        @Override
        public void dataSetCreated(final DataSet dataSet) {
            sipModel.exec(new Swing() {
                @Override
                public void run() {
                    dataSetMenu.refreshAndChoose(dataSet, null);
                }
            });
        }

        @Override
        public Feedback getFeedback() {
            return feedback;
        }
    }

    private class PasswordFetcher implements OAuthClient.PasswordRequest, ActionListener {

        private static final String PASSWORD = "Password";
        private JDialog dialog = new JDialog(home, "Culture Hub", true);
        private JPasswordField passwordField = new JPasswordField(15);
        private JCheckBox savePassword = new JCheckBox("Save password");
        private StringBuilder password = new StringBuilder();
        private JButton ok = new JButton("Ok");

        private PasswordFetcher() {
            // We disable the submit button by default and if the content != empty
            ok.addActionListener(this);
            ok.setEnabled(false);
            String savedPassword = sipModel.getPreferences().get(PASSWORD, "");
            savePassword.setSelected(!savedPassword.isEmpty());
            passwordField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent documentEvent) {
                    ok.setEnabled(!StringUtils.isWhitespace(new String(passwordField.getPassword())));
                }

                @Override
                public void removeUpdate(DocumentEvent documentEvent) {
                    insertUpdate(documentEvent);
                }

                @Override
                public void changedUpdate(DocumentEvent documentEvent) {
                    insertUpdate(documentEvent);
                }
            });
            passwordField.setText(savedPassword);
            JLabel labelA = new JLabel("Password: ");
            labelA.setLabelFor(passwordField);
            passwordField.addActionListener(this);

            JPanel fieldPanel = new JPanel(new BorderLayout(10, 10));
            fieldPanel.add(labelA, BorderLayout.WEST);
            fieldPanel.add(passwordField, BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.add(ok);

            JPanel wrap = new JPanel(new GridLayout(0, 1, 5, 5));
            wrap.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            wrap.add(fieldPanel);
            wrap.add(savePassword);
            wrap.add(buttonPanel);

            dialog.getContentPane().add(wrap, BorderLayout.CENTER);
            dialog.pack();
        }

        @Override
        public String getPassword() {
            dialog.setLocation(
                    (Toolkit.getDefaultToolkit().getScreenSize().width - dialog.getSize().width) / 2,
                    (Toolkit.getDefaultToolkit().getScreenSize().height - dialog.getSize().height) / 2
            );
            dialog.setVisible(true);
            return password.length() == 0 ? null : password.toString();
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            password.setLength(0);
            password.append(new String(passwordField.getPassword()));
            sipModel.getPreferences().put(PASSWORD, savePassword.isSelected() ? password.toString() : "");
            dialog.setVisible(false);
        }
    }

//    private static class Launcher extends AbstractAction implements Swing {
//        private String[] args;
//        private Set<String> instances = new TreeSet<String>();
//
//    // todo: this is for later.  just one instance now
//
//        @Override
//        public void actionPerformed(ActionEvent e) {
//            new Thread(this).start(); // todo: this whole launcher should change
//        }
//    }

    public static void main(final String[] args) throws StorageException {
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                final File storageDirectory = StorageFinder.getStorageDirectory(args);
                if (storageDirectory == null) return;
                try {
                    int instance = 1;
                    Application application = new Application(storageDirectory, String.valueOf(instance));
                    application.home.setVisible(true);
                    application.allFrames.restore();
                }
                catch (StorageException e) {
                    JOptionPane.showMessageDialog(null, "Unable to create the storage directory");
                    e.printStackTrace();
                }
            }
        });
    }
}
