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
import eu.delving.metadata.ValidationException;
import eu.delving.sip.base.ClientException;
import eu.delving.sip.base.CultureHubClient;
import eu.delving.sip.base.DataSetActions;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.OAuthClient;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.files.Storage;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.files.StorageFinder;
import eu.delving.sip.files.StorageImpl;
import eu.delving.sip.frames.AnalysisFrame;
import eu.delving.sip.frames.CreateFrame;
import eu.delving.sip.frames.FieldMappingFrame;
import eu.delving.sip.frames.FrameArranger;
import eu.delving.sip.frames.InputFrame;
import eu.delving.sip.frames.OutputFrame;
import eu.delving.sip.frames.RecordMappingFrame;
import eu.delving.sip.frames.StatisticsFrame;
import eu.delving.sip.frames.StatusFrame;
import eu.delving.sip.menus.DataSetMenu;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.model.UserNotifier;
import org.apache.commons.lang.StringUtils;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
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
import java.util.HashMap;
import java.util.Map;

/**
 * The main application
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class Application {
    private ImageIcon logo = new ImageIcon(getClass().getResource("/delving-logo.png"));
    private PopupExceptionHandler exceptionHandler;
    private SipModel sipModel;
    private DataSetActions actions;
    private JFrame home;
    private JDesktopPane desktop;
    private DataSetMenu dataSetMenu;
    private OAuthClient oauthClient;
    private Map<String, FrameBase> frames = new HashMap<String, FrameBase>();
    private FrameArranger frameArranger;

    private Application(final File storageDirectory) throws StorageException {
        Storage storage = new StorageImpl(storageDirectory);
        GroovyCodeResource groovyCodeResource = new GroovyCodeResource(getClass().getClassLoader());
        exceptionHandler = new PopupExceptionHandler();
        sipModel = new SipModel(storage, groovyCodeResource, this.exceptionHandler);
        home = new JFrame("Delving SIP Creator");
        final ImageIcon backgroundIcon = new ImageIcon(getClass().getResource("/delving-background.png"));
        desktop = new JDesktopPane() {
            public void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(backgroundIcon.getImage(), 0, 0, desktop);
            }
        };
        desktop.setBackground(new Color(190, 190, 200));
        CultureHubClient cultureHubClient = new CultureHubClient(new CultureHubClientContext(storageDirectory));
        frames.put("status", new StatusFrame(desktop, sipModel));
        frames.put("analysis", new AnalysisFrame(desktop, sipModel));
        frames.put("create", new CreateFrame(desktop, sipModel));
        frames.put("statistics", new StatisticsFrame(desktop, sipModel));
        frames.put("input", new InputFrame(desktop, sipModel));
        frames.put("field-mapping", new FieldMappingFrame(desktop, sipModel));
        frames.put("record-mapping", new RecordMappingFrame(desktop, sipModel));
        frames.put("output", new OutputFrame(desktop, sipModel));
        desktop.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createBevelBorder(0),
                BorderFactory.createBevelBorder(0)
        ));
        home.getContentPane().add(desktop, BorderLayout.CENTER);
        actions = new DataSetActions(desktop, sipModel, cultureHubClient, new Runnable() {
            @Override
            public void run() {
                int index = 0;
                for (FrameBase frame : frames.values()) {
                    if (index > 1) {
                        frame.show();
                    }
                    else {
                        frame.closeFrame();
                    }
                    index++;
                }
            }
        });
        home.getContentPane().add(createStatePanel(), BorderLayout.SOUTH);
        home.setSize(Toolkit.getDefaultToolkit().getScreenSize());
        home.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        home.setIconImage(logo.getImage());
        dataSetMenu = new DataSetMenu(sipModel);
        oauthClient = new OAuthClient(
                StorageFinder.getHostPort(storageDirectory),
                StorageFinder.getUser(storageDirectory),
                new PasswordFetcher()
        );
        frameArranger = new FrameArranger(desktop, frames);
        home.setJMenuBar(createMenuBar());
        home.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                putFrameStates();
                System.exit(0);
            }
        });
        sipModel.getDataSetModel().addListener(new DataSetModel.Listener() {
            @Override
            public void dataSetChanged(DataSet dataSet) {
                home.setTitle(String.format("Delving SIP Creator [%s - %s]", dataSet.getSpec(), dataSet.getDataSetFacts().get("name")));
            }

            @Override
            public void dataSetStateChanged(DataSet dataSet, DataSetState dataSetState) {
                switch (dataSetState) {
                    case IMPORTED_ANALYZED:
                        frames.get(1).show();
                        break;
                }
            }
        });
        osxExtra();
    }

    private JPanel createStatePanel() {
        JPanel p = new JPanel(new GridLayout(1, 0, 5, 5));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(5, 5, 5, 5),
                BorderFactory.createTitledBorder("States")
        ));
        for (Action action : actions.getActions()) {
            p.add(new JButton(action));
        }
        return p;
    }

    private void putFrameStates() {
        for (FrameBase frame : frames.values()) {
            frame.putState();
        }
    }

    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.add(dataSetMenu);
        bar.add(createViewMenu());
        bar.add(createFrameMenu());
        return bar;
    }

    private JMenu createFrameMenu() {
        JMenu menu = new JMenu("Frames");
        int index = 1;
        for (FrameBase frame : frames.values()) {
            frame.setAccelerator(index++);
            menu.add(frame.getAction());
        }
        return menu;
    }

    private JMenu createViewMenu() {
        JMenu menu = new JMenu("View");
        for (Action action : frameArranger.getActions()) {
            menu.add(action);
        }
        return menu;
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
        public void tellUser(String message) {
            exceptionHandler.tellUser(message);
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

    private class PopupExceptionHandler implements UserNotifier {

        @Override
        public void tellUser(final String message, final Exception exception) {
            Exec.swing(new Runnable() {
                @Override
                public void run() {
                    String html;
                    if (exception != null) {
                        html = String.format("<html><h3>%s</h3><p>%s</p></html>", message, exception.getMessage().replaceAll("<", "&lt;"));
                        if (exception instanceof ValidationException) {
                            StringBuilder problemHtml = new StringBuilder(String.format("<html><h3>%s</h3><pre>", message));
                            problemHtml.append(exception.getMessage().replaceAll("<", "&lt;"));
                            problemHtml.append("</pre></html>");
                            html = problemHtml.toString();
                        }
                        exception.printStackTrace();
                    }
                    else {
                        html = String.format("<html><h3>%s</h3></html>", message);
                    }
                    JOptionPane.showMessageDialog(null, html);
                }
            });
        }

        @Override
        public void tellUser(String message) {
            tellUser(message, null);
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
                            putFrameStates();
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
                    for (FrameBase frame : application.frames.values()) {
                        if (frame.wasVisible()) {
                            frame.show();
                        }
                    }
                }
                catch (StorageException e) {
                    JOptionPane.showMessageDialog(null, "Unable to create the storage directory");
                    e.printStackTrace();
                }
            }
        });
    }
}
