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

package eu.delving.sip.gui;

import eu.delving.groovy.GroovyCodeResource;
import eu.delving.metadata.MetadataModel;
import eu.delving.metadata.MetadataModelImpl;
import eu.delving.metadata.ValidationException;
import eu.delving.sip.FileStore;
import eu.delving.sip.FileStoreException;
import eu.delving.sip.FileStoreImpl;
import eu.delving.sip.desktop.FileStoreFinder;
import eu.europeana.sip.gui.FileMenu;
import eu.europeana.sip.model.SipModel;
import eu.europeana.sip.model.UserNotifier;

import javax.swing.ImageIcon;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

/**
 * The main application
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class Application {
    private ImageIcon logo = new ImageIcon(getClass().getResource("/delving-logo.png"));
    private PopupExceptionHandler exceptionHandler;
    private SipModel sipModel;
    private JFrame frame;
    private JDesktopPane desktop;
    private DataSetMenu dataSetMenu;
    private MappingMenu mappingMenu;
    private CultureHubMenu cultureHubMenu;
    private OAuthClient oauthClient;
    private MappingFrame mappingFrame;
    private RefinementFrame refinementFrame;
    private TransformationFrame transformationFrame;

    private Application(final File fileStoreDirectory) throws FileStoreException {
        MetadataModel metadataModel = loadMetadataModel();
        FileStore fileStore = new FileStoreImpl(fileStoreDirectory, metadataModel);
        GroovyCodeResource groovyCodeResource = new GroovyCodeResource(getClass().getClassLoader());
        this.exceptionHandler = new PopupExceptionHandler();
        this.sipModel = new SipModel(fileStore, metadataModel, groovyCodeResource, this.exceptionHandler);
        frame = new JFrame("Delving SIP Creator");
        final ImageIcon backgroundIcon = new ImageIcon(getClass().getResource("/delving-background.png"));
        desktop = new JDesktopPane() {
            public void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(backgroundIcon.getImage(), 0, 0, desktop);
            }
        };
        desktop.setBackground(new Color(190, 190, 200));
        mappingFrame = new MappingFrame(desktop, sipModel);
        refinementFrame = new RefinementFrame(desktop, sipModel);
        transformationFrame = new TransformationFrame(desktop, sipModel);
        frame.getContentPane().add(desktop);
        frame.setSize(Toolkit.getDefaultToolkit().getScreenSize());
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setIconImage(logo.getImage());
        this.dataSetMenu = new DataSetMenu(sipModel);
        this.mappingMenu = new MappingMenu(sipModel);
        this.oauthClient = new OAuthClient(
                FileStoreFinder.getHostPort(fileStoreDirectory),
                FileStoreFinder.getUser(fileStoreDirectory),
                new PasswordFetcher(fileStoreDirectory)
        );
        this.cultureHubMenu = new CultureHubMenu(desktop, sipModel, new CultureHubClient(new CultureHubClient.Context() {

            @Override
            public String getServerUrl() {
                return String.format("http://%s/dataset", FileStoreFinder.getHostPortUser(fileStoreDirectory));
            }

            @Override
            public String getAccessToken() {
                return oauthClient.getToken();
            }

            @Override
            public void tellUser(String message) {
                exceptionHandler.tellUser(message);
            }
        }));
        frame.setJMenuBar(createMenuBar());
        frame.addWindowListener(new WindowListener() {
            @Override
            public void windowOpened(WindowEvent windowEvent) {
            }

            @Override
            public void windowClosing(WindowEvent windowEvent) {
                putFrameStates();
                System.exit(0);
            }

            @Override
            public void windowClosed(WindowEvent windowEvent) {
            }

            @Override
            public void windowIconified(WindowEvent windowEvent) {
            }

            @Override
            public void windowDeiconified(WindowEvent windowEvent) {
            }

            @Override
            public void windowActivated(WindowEvent windowEvent) {
            }

            @Override
            public void windowDeactivated(WindowEvent windowEvent) {
            }
        });
        osxExtra();
    }

    private void putFrameStates() {
        mappingFrame.putState();
        refinementFrame.putState();
        transformationFrame.putState();
    }

    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.add(new FileMenu(frame, sipModel, new Runnable() {
            @Override
            public void run() {
                // todo: when a new data set is imported...
            }
        }));
        bar.add(dataSetMenu);
        bar.add(mappingMenu);
        bar.add(cultureHubMenu);
        bar.add(createFrameMenu());
        return bar;
    }

    private JMenu createFrameMenu() {
        JMenu menu = new JMenu("Frames");
        menu.add(mappingFrame.getAction());
        menu.add(refinementFrame.getAction());
        menu.add(transformationFrame.getAction());
        return menu;
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

    private class PasswordFetcher implements OAuthClient.PasswordRequest {
        private File fileStoreDirectory;

        private PasswordFetcher(File fileStoreDirectory) {
            this.fileStoreDirectory = fileStoreDirectory;
        }

        @Override
        public String getPassword() {
            JPasswordField passwordField = new JPasswordField(15);
            JLabel labelA = new JLabel("Password");
            labelA.setLabelFor(passwordField);
            JPanel p = new JPanel(new BorderLayout(10, 10));
            p.add(labelA, BorderLayout.WEST);
            p.add(passwordField, BorderLayout.CENTER);
            JPanel wrap = new JPanel();
            wrap.add(p);
            int answer = JOptionPane.showInternalConfirmDialog(
                    desktop,
                    wrap,
                    String.format("Authenticate for %s", FileStoreFinder.getHostPortUser(fileStoreDirectory)),
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    logo
            );
            return answer == JOptionPane.OK_OPTION ? new String(passwordField.getPassword()) : null;
        }
    }

    private class PopupExceptionHandler implements UserNotifier {

        @Override
        public void tellUser(final String message, final Exception exception) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    String html = exception != null ?
                            String.format("<html><h3>%s</h3><p>%s</p></html>", message, exception.getMessage()) :
                            String.format("<html><h3>%s</h3></html>", message);
                    if (exception instanceof ValidationException) {
                        StringBuilder problemHtml = new StringBuilder(String.format("<html><h3>%s</h3><pre>", message));
                        problemHtml.append(exception.getMessage());
                        problemHtml.append("</pre></html>");
                        html = problemHtml.toString();
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
                Object quitHandlerProxy = Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] {quitHandlerInterface}, handler);
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

    public static void main(String[] args) throws FileStoreException {
        final File fileStoreDirectory = FileStoreFinder.getFileStoreDirectory();
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                try {
                    Application application = new Application(fileStoreDirectory);
                    application.frame.setVisible(true);
                }
                catch (FileStoreException e) {
                    JOptionPane.showMessageDialog(null, "Unable to create the file store");
                    e.printStackTrace();
                }
            }
        });
    }
}
