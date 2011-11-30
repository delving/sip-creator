/*
 * Copyright 2011 DELVING BV
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
import eu.delving.sip.base.*;
import eu.delving.sip.files.*;
import eu.delving.sip.frames.AllFrames;
import eu.delving.sip.menus.DataSetMenu;
import eu.delving.sip.menus.EditHistory;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.Feedback;
import eu.delving.sip.model.SipModel;
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

/**
 * The main application
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
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
    private DataSetStateButton dataSetStateButton = new DataSetStateButton();
    private JToggleButton harvestToggleButton = new JToggleButton();
    private Timer resizeTimer;
    private EditHistory editHistory = new EditHistory();

    private Application(final File storageDirectory) throws StorageException {
        Storage storage = new StorageImpl(storageDirectory);
        GroovyCodeResource groovyCodeResource = new GroovyCodeResource(getClass().getClassLoader());
        final ImageIcon backgroundIcon = new ImageIcon(getClass().getResource("/delving-background.png"));
        desktop = new JDesktopPane() {
            public void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(backgroundIcon.getImage(), 0, 0, desktop);
                //g.setColor(Color.BLACK);
                //drawText(g, 4, 4);
                //g.setColor(Color.WHITE);
                //drawText(g, 0, 0);
            }

            private void drawText(Graphics g, int x, int y) {
                g.setFont(new Font("Arial", Font.BOLD, 120));
                g.drawString("Delving", 490 + x, 400 + y);
                g.setFont(new Font("Arial", Font.BOLD, 90));
                g.drawString("SIP-Creator", 530 + x, 500 + y);
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
        sipModel = new SipModel(storage, groovyCodeResource, feedback);
        harvestPool = new HarvestPool(sipModel);
        harvestDialog = new HarvestDialog(desktop, sipModel, harvestPool);
        feedback.setSipModel(sipModel);
        feedback.say("SIP-Creator started");
        home = new JFrame("Delving SIP Creator");
        desktop.setBackground(new Color(190, 190, 200));
        CultureHubClient cultureHubClient = new CultureHubClient(new CultureHubClientContext(storageDirectory));
        allFrames = new AllFrames(desktop, sipModel, editHistory);
        home.getContentPane().add(desktop, BorderLayout.CENTER);
        downloadAction = new DownloadAction(desktop, sipModel, cultureHubClient);
        importAction = new ImportAction(desktop, sipModel, harvestPool);
        validateAction = new ValidateAction(desktop, sipModel, allFrames.prepareForInvestigation(desktop));
        uploadAction = new UploadAction(desktop, sipModel, cultureHubClient);
        deleteAction = new ReleaseAction(desktop, sipModel, cultureHubClient);
        home.getContentPane().add(createStatePanel(), BorderLayout.SOUTH);
        home.getContentPane().add(allFrames.getButtonPanel(), BorderLayout.WEST);
        home.setSize(Toolkit.getDefaultToolkit().getScreenSize());
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
        sipModel.getDataSetModel().addListener(new DataSetModel.Listener() {
            @Override
            public void dataSetChanged(DataSet dataSet) {
                home.setTitle(String.format("Delving SIP Creator [%s]", dataSet.getSpec()));
                dataSetStateChanged(dataSet, dataSet.getState());
            }

            @Override
            public void dataSetRemoved() {
                home.setTitle("Delving SIP Creator");
                Exec.work(new Runnable() {
                    @Override
                    public void run() {
                        sipModel.seekReset();
                        sipModel.getMappingModel().setRecordMapping(null);
                        sipModel.getAnalysisModel().setStatistics(null);
                        sipModel.getDataSetFacts().set(null);
                        sipModel.getRecordCompileModel().updatedRecord(null);
                        Exec.swing(new Runnable() {
                            @Override
                            public void run() {
                                dataSetMenu.refreshAndChoose(null);
                            }
                        });
                    }
                });
            }

            @Override
            public void dataSetStateChanged(DataSet dataSet, DataSetState dataSetState) {
                dataSetStateButton.setState(dataSetState);
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
        harvestToggleButton.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent itemEvent) {
                if (itemEvent.getStateChange() == ItemEvent.SELECTED) {
                    harvestDialog.openAtPosition();
                }
                else {
                    harvestDialog.closeFrame();
                }
            }
        });
    }

    private boolean quit() {
        if (harvestPool.getSize() > 0) {
            if (JOptionPane.YES_OPTION !=
                    JOptionPane.showConfirmDialog(null,
                            String.format("There are %d active harvests, are you sure you want to exit?", harvestPool.getSize()),
                            "Active harvests",
                            JOptionPane.YES_NO_OPTION)) {
                return false;
            }
        }
        System.exit(0);
        return true;
    }

    private JPanel createStatePanel() {
        refreshToggleButton();
        JPanel right = new JPanel(new BorderLayout(6, 6));
        right.add(feedback.getToggle(), BorderLayout.CENTER);
        right.add(harvestToggleButton, BorderLayout.EAST);
        JPanel p = new JPanel(new GridLayout(1, 0, 15, 15));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createBevelBorder(0),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));
        p.add(dataSetStateButton);
        p.add(right);
//        p.setPreferredSize(new Dimension(100, 100));
        return p;
    }

    private void refreshToggleButton() {
        harvestToggleButton.setText(String.format("%d harvests", harvestPool.getSize()));
    }

    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.add(createFileMenu());
        bar.add(dataSetMenu);
        bar.add(editHistory.getEditMenu());
        bar.add(allFrames.getViewMenu());
        bar.add(allFrames.getFrameMenu());
        return bar;
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

    private class DataSetStateButton extends JButton implements ActionListener {
        private Runnable work;
        private Action action;

        private DataSetStateButton() {
            super("Status");
            addActionListener(this);
        }

        public void setState(DataSetState state) {
            setText(state.toHtml());
            work = null;
            action = null;
            switch (state) {
                case ABSENT:
                    work = allFrames.prepareForNothing();
                    break;
                case EMPTY:
                    action = importAction;
                    break;
                case IMPORTED:
                    work = new AnalysisPerformer();
                    break;
                case ANALYZED_IMPORT:
                    work = allFrames.prepareForDelimiting();
                    break;
                case DELIMITED:
                    work = new ConvertPerformer();
                    break;
                case SOURCED:
                    work = new AnalysisPerformer();
                    break;
                case ANALYZED_SOURCE:
                    work = allFrames.prepareForMapping(desktop);
                    break;
                case MAPPING:
                    action = validateAction;
                    break;
                case VALIDATED:
                    action = uploadAction;
                    break;
            }
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (work != null) work.run();
            if (action != null) action.actionPerformed(null);
        }
    }

    private class AnalysisPerformer implements Runnable {
        @Override
        public void run() {
            final ProgressListener progress = sipModel.getFeedback().progressListener("Analyzing");
            progress.setProgressMessage(String.format(
                    "<html><h3>Analyzing data of '%s'</h3>",
                    sipModel.getDataSetModel().getDataSet().getSpec()
            ))  ;
            progress.prepareFor(100);
            sipModel.analyzeFields(new SipModel.AnalysisListener() {
                @Override
                public boolean analysisProgress(final long elementCount) {
                    int value = (int)(elementCount / AnalysisParser.ELEMENT_STEP);
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

    private class ConvertPerformer implements Runnable {
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
            Exec.swing(new Runnable() {
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

    public static void main(String[] args) throws StorageException {
        final File storageDirectory = StorageFinder.getStorageDirectory(args);
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                try {
                    Application application = new Application(storageDirectory);
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
