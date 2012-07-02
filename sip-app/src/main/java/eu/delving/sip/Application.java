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
import eu.delving.sip.actions.*;
import eu.delving.sip.base.*;
import eu.delving.sip.files.*;
import eu.delving.sip.frames.AllFrames;
import eu.delving.sip.frames.HarvestDialog;
import eu.delving.sip.menus.DataSetMenu;
import eu.delving.sip.menus.ExpertMenu;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.Feedback;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.panels.HelpPanel;
import eu.delving.sip.panels.StatusPanel;
import eu.delving.sip.xml.AnalysisParser;
import org.apache.amber.oauth2.common.exception.OAuthProblemException;
import org.apache.amber.oauth2.common.exception.OAuthSystemException;
import org.apache.commons.lang.StringUtils;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.Set;
import java.util.TreeSet;

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
    private Action downloadAction, importAction, uploadAction, deleteAction, validateAction;
    private JFrame home;
    private JDesktopPane desktop;
    private DataSetMenu dataSetMenu;
    private OAuthClient oauthClient;
    private AllFrames allFrames;
    private VisualFeedback feedback;
    private HarvestDialog harvestDialog;
    private HarvestPool harvestPool;
    private StatusPanel statusPanel;
    private HelpPanel helpPanel;
    private Timer resizeTimer;
    private Launcher launcher;
    private String instance;

    private Application(final File storageDirectory, Launcher launcher, String instance) throws StorageException {
        this.launcher = launcher;
        this.launcher.instances.add(this.instance = instance);
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
        statusPanel = new StatusPanel(sipModel);
        harvestPool = new HarvestPool(sipModel);
        harvestDialog = new HarvestDialog(desktop, sipModel, harvestPool);
        feedback.setSipModel(sipModel);
        feedback.say("SIP-Creator started");
        home = new JFrame("Delving SIP Creator");
        home.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent componentEvent) {
                allFrames.rebuildView();
            }
        });
        desktop.setBackground(new Color(190, 190, 200));
        allFrames = new AllFrames(desktop, sipModel);
        helpPanel = new HelpPanel(sipModel, cultureHubClient.getHttpClient());
        home.getContentPane().add(desktop, BorderLayout.CENTER);
        downloadAction = new DownloadAction(desktop, sipModel, cultureHubClient);
        importAction = new ImportAction(desktop, sipModel, harvestPool);
        validateAction = new ValidateAction(desktop, sipModel, allFrames.prepareForInvestigation(desktop));
        uploadAction = new UploadAction(desktop, sipModel, cultureHubClient);
        deleteAction = new ReleaseAction(desktop, sipModel, cultureHubClient);
        home.getContentPane().add(createStatePanel(), BorderLayout.SOUTH);
        home.getContentPane().add(allFrames.getArrangementsPanel(), BorderLayout.WEST);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        screen.height -= 30;
        home.setSize(screen);
        home.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        ImageIcon logo = new ImageIcon(getClass().getResource("/sip-creator-logo.png"));
        home.setIconImage(logo.getImage());
        dataSetMenu = new DataSetMenu(sipModel);
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
                                sipModel.getMappingModel().setRecMapping(null);
                                sipModel.getDataSetFacts().set(null);
                                sipModel.getStatsModel().setStatistics(null);
                            }
                        });
                        home.setTitle("Delving SIP Creator");
                        sipModel.seekReset();
                        dataSetMenu.refreshAndChoose(null);
                        break;
                    default:
                        DataSetModel dataSetModel = sipModel.getDataSetModel();
                        home.setTitle(String.format(
                                "Delving SIP Creator - [%s -> %s]",
                                dataSetModel.getDataSet().getSpec(), dataSetModel.getPrefix().toUpperCase()
                        ));
                        break;
                }
            }
        });
        harvestPool.addListDataListener(new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent listDataEvent) {
                refreshToggleButton();
            }

            @Override
            public void intervalRemoved(ListDataEvent listDataEvent) {
                refreshToggleButton();
            }

            @Override
            public void contentsChanged(ListDataEvent listDataEvent) {
                refreshToggleButton();
            }
        });
    }

    private boolean quit() {
        launcher.instances.remove(this.instance);
        if (harvestPool.getSize() > 0) {
            boolean exitAnyway = feedback.confirm(
                    "Active harvests",
                    String.format("There are %d active harvests, are you sure you want to exit?", harvestPool.getSize())
            );
            if (exitAnyway) return false;
        }
        if (!launcher.instances.isEmpty()) return false;
        System.exit(0);
        return true;
    }

    private JPanel createStatePanel() {
        refreshToggleButton();
        JPanel right = new JPanel(new BorderLayout(6, 6));
        right.add(allFrames.getBigWindowsPanel(), BorderLayout.WEST);
        right.add(feedback.getToggle(), BorderLayout.CENTER);
        right.add(new JButton(harvestDialog.getAction()), BorderLayout.EAST);
        JPanel p = new JPanel(new GridLayout(1, 0, 15, 15));
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
        statusPanel.setReaction(DataSetState.VALIDATED, uploadAction);
        p.add(statusPanel);
        p.add(right);
        p.setPreferredSize(new Dimension(80, 80));
        return p;
    }

    private void refreshToggleButton() {
        harvestDialog.getAction().putValue(Action.NAME, String.format("%d harvests", harvestPool.getSize()));
    }

    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.add(createFileMenu());
        bar.add(dataSetMenu);
        bar.add(allFrames.getViewMenu());
        bar.add(allFrames.getFrameMenu());
        bar.add(new ExpertMenu(sipModel, launcher));
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
        menu.add(deleteAction);
        return menu;
    }

    private class InputAnalyzer implements Swing {
        @Override
        public void run() {
            final ProgressListener progress = sipModel.getFeedback().progressListener("Analyzing");
            progress.setProgressMessage(String.format(
                    "<html><h3>Analyzing data of '%s'</h3>",
                    sipModel.getDataSetModel().getDataSet().getSpec()
            ));
            progress.prepareFor(100);
            sipModel.analyzeFields(new SipModel.AnalysisListener() {
                @Override
                public boolean analysisProgress(final long elementCount) {
                    int value = (int) (elementCount / AnalysisParser.ELEMENT_STEP);
                    progress.setProgressString(String.format("%d elements", elementCount));
                    return progress.setProgress(value % 100);
                }

                @Override
                public void analysisComplete() {
                    progress.finished(true);
                }
            });
        }
    }

    private class ConvertPerformer implements Swing {
        @Override
        public void run() {
            ProgressListener listener = sipModel.getFeedback().progressListener("Converting");
            listener.setProgressMessage(String.format(
                    "<html><h3>Converting source data of '%s' to standard form</h3>",
                    sipModel.getDataSetModel().getDataSet().getSpec()
            ));
            sipModel.convertSource(listener);
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
                    dataSetMenu.refreshAndChoose(dataSet);
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

    private static class Launcher extends AbstractAction implements Swing {
        private String[] args;
        private Set<String> instances = new TreeSet<String>();

        private Launcher(String[] args) {
            super("New Application Instance");
            this.args = args;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            new Thread(this).start(); // todo: this whole launcher should change
        }

        @Override
        public void run() {
            final File storageDirectory = StorageFinder.getStorageDirectory(args);
            if (storageDirectory == null) return;
            try {
                int instance = 1;
                while (instances.contains(String.valueOf(instance))) instance++;
                Application application = new Application(storageDirectory, this, String.valueOf(instance));
                application.home.setVisible(true);
                application.allFrames.restore();
            }
            catch (StorageException e) {
                JOptionPane.showMessageDialog(null, "Unable to create the storage directory");
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws StorageException {
        EventQueue.invokeLater(new Launcher(args));
    }
}
