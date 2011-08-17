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
import eu.europeana.sip.gui.ImportMenu;
import eu.europeana.sip.model.SipModel;
import eu.europeana.sip.model.UserNotifier;

import javax.swing.ImageIcon;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.io.File;
import java.util.Arrays;

/**
 * The main application
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class Application {
    private SipModel sipModel;
    private JFrame frame;
    private JDesktopPane desktop;
    private DataSetMenu dataSetMenu;
    private MappingMenu mappingMenu;
    private CultureHubMenu cultureHubMenu;

    private Application(File fileStoreDirectory) throws FileStoreException {
        MetadataModel metadataModel = loadMetadataModel();
        FileStore fileStore = new FileStoreImpl(fileStoreDirectory, metadataModel);
        GroovyCodeResource groovyCodeResource = new GroovyCodeResource(getClass().getClassLoader());
        this.sipModel = new SipModel(fileStore, metadataModel, groovyCodeResource, new PopupExceptionHandler());
        frame = new JFrame("Delving SIP Creator");
        final ImageIcon backgroundIcon = new ImageIcon(getClass().getResource("/delving-background.png"));
        desktop = new JDesktopPane() {
            public void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(backgroundIcon.getImage(), 0, 0, desktop);
            }
        };
        desktop.setBackground(new Color(190, 190, 200));
        frame.getContentPane().add(desktop);
        frame.setSize(Toolkit.getDefaultToolkit().getScreenSize());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.dataSetMenu = new DataSetMenu(sipModel);
        this.mappingMenu = new MappingMenu(sipModel);
        this.cultureHubMenu = new CultureHubMenu(desktop, sipModel, null); // todo: datasetclient
        frame.setJMenuBar(createMenuBar());
    }

    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();
        bar.add(new ImportMenu(frame, sipModel, new Runnable() {
            @Override
            public void run() {
                // todo: when a new data set is imported...
            }
        }));
        bar.add(dataSetMenu);
        bar.add(mappingMenu);
        bar.add(cultureHubMenu);
        return bar;
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
