/*
 * Copyright 2010 DELVING BV
 *
 * Licensed under the EUPL, Version 1.0 or as soon they
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

package eu.delving.sip.desktop.windows;

import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * The identifier of a window and additional properties.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public enum WindowId {

    WORKSPACE("Select workspace", KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.ALT_MASK), MenuGroup.FILE,
            new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    // todo: add body and return void;
                }
            }
    ),
    ANALYZE("Analyze", KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.ALT_MASK), AnalyzeWindow.class),
    WELCOME("Welcome", KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.ALT_MASK), WelcomeWindow.class, MenuGroup.HELP),
    MAPPING("Mapping", KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.ALT_MASK), MappingWindow.class),
    PREVIEW("Preview", KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.ALT_MASK), PreviewWindow.class),
    UPLOAD("Upload", KeyStroke.getKeyStroke(KeyEvent.VK_U, KeyEvent.ALT_MASK), UploadWindow.class),
    DOCUMENTATION("Documentation", KeyStroke.getKeyStroke(KeyEvent.VK_D, KeyEvent.ALT_MASK), MenuGroup.HELP,
            new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    if (Desktop.isDesktopSupported()) {
                        Desktop desktop = Desktop.getDesktop();
                        try {
                            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                                desktop.browse(new URI("http://www.delving.eu"));
                            }
                            else {
                                // todo: display JOption pane with link
                            }
                        }
                        catch (IOException e) {
                            LOG.error("Error opening URL", e);
                        }
                        catch (URISyntaxException e) {
                            LOG.error("Error opening URL", e);
                        }
                    }
                    else {
                        // todo: display JOption pane with link
                    }
                }
            }
    ),
    SIGN_OUT("Sign Out", KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.ALT_MASK), MenuGroup.ACCOUNT,
            new AbstractAction() {

                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    // todo: say bye and sign out using AuthenticationClient (set token to null and stop polling?)
                    throw new NoSuchMethodError("Method not implemented yet");
                }
            }
    ),
    EXIT("Exit", KeyStroke.getKeyStroke(KeyEvent.VK_Q, KeyEvent.ALT_MASK), MenuGroup.FILE,
            new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    // todo: add body and return void;
                }
            }
    ),
    NORMALIZE("Normalize", KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.ALT_MASK), NormalizeWindow.class);

    private static final Logger LOG = Logger.getRootLogger();
    private String title;
    private KeyStroke accelerator;
    private MenuGroup menuGroup;
    private Action action;
    private Class<? extends DesktopWindow> desktopWindow;

    WindowId(String title, KeyStroke accelerator, Class<? extends DesktopWindow> desktopWindow) {
        this.title = title;
        this.accelerator = accelerator;
        this.desktopWindow = desktopWindow;
    }

    WindowId(String title, KeyStroke accelerator, Class<? extends DesktopWindow> desktopWindow, MenuGroup menuGroup) {
        this.title = title;
        this.accelerator = accelerator;
        this.desktopWindow = desktopWindow;
        this.menuGroup = menuGroup;
    }

    WindowId(String title, KeyStroke accelerator, MenuGroup menuGroup, Action action) {
        this.title = title;
        this.accelerator = accelerator;
        this.menuGroup = menuGroup;
        this.action = action;
    }

    public String getTitle() {
        return title;
    }

    public KeyStroke getAccelerator() {
        return accelerator;
    }

    public MenuGroup getMenuGroup() {
        return menuGroup;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public Action getAction() {
        return action;
    }

    public Class<? extends DesktopWindow> getDesktopWindow() {
        return desktopWindow;
    }
}
