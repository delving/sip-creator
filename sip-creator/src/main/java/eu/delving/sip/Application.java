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
import eu.delving.sip.base.ClientException;
import eu.delving.sip.base.CultureHubClient;
import eu.delving.sip.base.DownloadAction;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.ImportAction;
import eu.delving.sip.base.OAuthClient;
import eu.delving.sip.base.UploadAction;
import eu.delving.sip.base.ValidateAction;
import eu.delving.sip.base.VisualFeedback;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.files.StorageFinder;
import eu.delving.sip.files.StorageImpl;
import eu.delving.sip.frames.AllFrames;
import eu.delving.sip.menus.DataSetMenu;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.Feedback;
import eu.delving.sip.model.SipModel;
import org.apache.commons.lang.StringUtils;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static eu.delving.sip.files.DataSetState.IMPORTED;

/**
 * The main application
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class Application {
    private SipModel sipModel;
    private Action[] actions;
    private JFrame home;
    private JDesktopPane desktop;
    private DataSetMenu dataSetMenu;
    private OAuthClient oauthClient;
    private AllFrames allFrames;
    private VisualFeedback feedback;
    private JLabel statusLabel = new JLabel("No dataset", JLabel.CENTER);

    private Application(final File storageDirectory) throws StorageException {
        Storage storage = new StorageImpl(storageDirectory);
        GroovyCodeResource groovyCodeResource = new GroovyCodeResource(getClass().getClassLoader());
        final ImageIcon backgroundIcon = new ImageIcon(getClass().getResource("/delving-background.png"));
        desktop = new JDesktopPane() {
            public void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(backgroundIcon.getImage(), 0, 0, desktop);
                g.setColor(Color.BLACK);
                drawText(g, 4, 4);
                g.setColor(Color.WHITE);
                drawText(g, 0, 0);
            }

            private void drawText(Graphics g, int x, int y) {
                g.setFont(new Font("Serif", Font.BOLD, 120));
                g.drawString("Delving", 490 + x, 400 + y);
                g.setFont(new Font("Serif", Font.BOLD, 90));
                g.drawString("SIP-Creator", 530 + x, 500 + y);
            }
        };
        feedback = new VisualFeedback(desktop);
        sipModel = new SipModel(storage, groovyCodeResource, feedback);
        feedback.setSipModel(sipModel);
        feedback.say("SIP-Creator started");
        home = new JFrame("Delving SIP Creator");
        desktop.setBackground(new Color(190, 190, 200));
        CultureHubClient cultureHubClient = new CultureHubClient(new CultureHubClientContext(storageDirectory));
        allFrames = new AllFrames(desktop, sipModel);
        home.getContentPane().add(desktop, BorderLayout.CENTER);
        actions = new Action[]{
                new DownloadAction(desktop, sipModel, cultureHubClient),
                new ImportAction(desktop, sipModel),
                new ValidateAction(desktop, sipModel),
                new UploadAction(desktop, sipModel, cultureHubClient)
        };
        home.getContentPane().add(createStatePanel(), BorderLayout.SOUTH);
        home.setSize(Toolkit.getDefaultToolkit().getScreenSize());
        home.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        ImageIcon logo = new ImageIcon(getClass().getResource("/sip-creator-logo.png"));
        home.setIconImage(logo.getImage());
        dataSetMenu = new DataSetMenu(sipModel);
        oauthClient = new OAuthClient(
                StorageFinder.getHostPort(storageDirectory),
                StorageFinder.getUser(storageDirectory),
                new PasswordFetcher()
        );
        home.setJMenuBar(createMenuBar());
        home.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                allFrames.putState();
                System.exit(0);
            }
        });
        sipModel.getDataSetModel().addListener(new DataSetModel.Listener() {
            @Override
            public void dataSetChanged(DataSet dataSet) {
                home.setTitle(String.format("Delving SIP Creator [%s - %s]", dataSet.getSpec(), dataSet.getDataSetFacts().get("name")));
                dataSetStateChanged(dataSet, dataSet.getState());
            }

            @Override
            public void dataSetRemoved() {
                home.setTitle("Delving SIP Creator");
            }

            @Override
            public void dataSetStateChanged(DataSet dataSet, DataSetState dataSetState) {
                statusLabel.setText(dataSetState.toHtml());
                if (dataSetState == IMPORTED) {
                    sipModel.analyzeFields(new SipModel.AnalysisListener() {
                        @Override
                        public void analysisProgress(final long elementCount) {
                            Exec.swing(new Runnable() {
                                @Override
                                public void run() {
                                    statusLabel.setText(String.format("Analyzed %d elements", elementCount));
                                }
                            });
                        }
                    });
                }
            }
        });
        osxExtra();
    }

    private JPanel createStatePanel() {
        JPanel bp = new JPanel(new GridLayout(2, 2));
        for (Action action : actions) {
            bp.add(new JButton(action));
        }
        JPanel p = new JPanel(new GridLayout(1, 0, 15, 15));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createBevelBorder(0),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createBevelBorder(0),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));
        p.add(statusLabel);
        p.add(bp);
        p.add(feedback.getToggle());
        return p;
    }

    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.add(dataSetMenu);
        bar.add(allFrames.getViewMenu());
        bar.add(allFrames.getFrameMenu());
        return bar;
    }

    private class CultureHubClientContext implements CultureHubClient.Context {

        private File storageDirectory;

        public CultureHubClientContext(File storageDirectory) {
            this.storageDirectory = storageDirectory;
        }

        @Override
        public String getServerUrl() {
            return String.format("http://%s/dataset/sip-creator", StorageFinder.getHostPortUser(storageDirectory));
        }

        @Override
        public String getAccessToken() throws ClientException {
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
                    dataSetMenu.setPreference(dataSet);
                    sipModel.setDataSet(dataSet);
                }
            });
        }

        @Override
        public Feedback getNotifier() {
            return feedback;
        }
    }

    private class PasswordFetcher implements OAuthClient.PasswordRequest, ActionListener {

        private JDialog dialog = new JDialog(home, "Culture Hub", true);
        private JPasswordField passwordField = new JPasswordField(15);
        private StringBuilder password = new StringBuilder();
        private JButton ok = new JButton("Ok");

        private PasswordFetcher() {
            // We disable the submit button by default and if the content != empty
            ok.addActionListener(this);
            ok.setEnabled(false);
            passwordField.getDocument().addDocumentListener(
                    new DocumentListener() {
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
                    }
            );
            JLabel labelA = new JLabel("Password: ");
            labelA.setLabelFor(passwordField);
            passwordField.addActionListener(this);

            JPanel fieldPanel = new JPanel(new BorderLayout(10, 10));
            fieldPanel.add(labelA, BorderLayout.WEST);
            fieldPanel.add(passwordField, BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.add(ok);

            JPanel wrap = new JPanel(new BorderLayout(5, 5));
            wrap.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            wrap.add(fieldPanel, BorderLayout.CENTER);
            wrap.add(buttonPanel, BorderLayout.SOUTH);

            dialog.getContentPane().add(wrap, BorderLayout.CENTER);
            dialog.pack();
        }

        @Override
        public String getPassword() {
            passwordField.setText(null);
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
            dialog.setVisible(false);
        }
    }

    private void osxExtra() {
// todo: #17 replace osx specific code with this shutdown hook.¬
//        Runtime.getRuntime().addShutdownHook(
//                new Thread() {
//
//                    @Override
//                    public void run() {
//                        putFrameStates();
//                    }
//                }
//        );
        boolean osx = (System.getProperty("os.name").toLowerCase().startsWith("mac os x"));
        if (osx) {
            try {
                InvocationHandler handler = new InvocationHandler() {
                    @Override
                    public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
                        if (method.getName().equals("handleQuitRequestWith")) {
                            allFrames.putState();
                            Method performQuitMethod = objects[1].getClass().getDeclaredMethod("performQuit");
                            performQuitMethod.invoke(objects[1]);
                        }
                        return null;
                    }
                };
                Class quitHandlerInterface = Class.forName("com.apple.eawt.QuitHandler");
                Object quitHandlerProxy = Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{quitHandlerInterface}, handler);
                Class applicationClass = Class.forName("com.apple.eawt.Application");
                Method getApplication = applicationClass.getDeclaredMethod("getApplication");
                Object applicationInstance = getApplication.invoke(null);
                Method setQuitHandlerMethod = applicationClass.getDeclaredMethod("setQuitHandler", quitHandlerInterface);
                setQuitHandlerMethod.invoke(applicationInstance, quitHandlerProxy);
            }
            catch (ClassNotFoundException cnfe) {
                System.err.println("This version of Mac OS X does not support the Apple EAWT.  ApplicationEvent handling has been disabled (" + cnfe + ")");
            }
            catch (Exception ex) {  // Likely a NoSuchMethodException or an IllegalAccessException loading/invoking eawt.Application methods
                System.err.println("Mac OS X Adapter could not talk to EAWT:");
                ex.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws StorageException {
        final File storageDirectory = StorageFinder.getStorageDirectory();
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
