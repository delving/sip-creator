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
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * The identifier of a window and additional properties.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public enum WindowId {

    ANALYZE("Analyze", KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.ALT_MASK), AnalyzeWindow.class),
    DATA_SET("Open data set", KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.ALT_MASK), DataSetWindow.class, MenuGroup.FILE),
    WELCOME("Welcome", KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.ALT_MASK), WelcomeWindow.class, MenuGroup.ACCOUNT),
    AUTHENTICATION("Authentication", KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.ALT_MASK), null, MenuGroup.ACCOUNT),
    SIGN_IN("Sign In", KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.ALT_MASK), null, MenuGroup.ACCOUNT),
    SIGN_OUT("Sign Out", KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.ALT_MASK), null, MenuGroup.ACCOUNT),
    MAPPING("Mapping", KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.ALT_MASK), MappingWindow.class, MenuGroup.SIP),
    PREVIEW("Preview", KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.ALT_MASK), PreviewWindow.class),
    UPLOAD("Upload", KeyStroke.getKeyStroke(KeyEvent.VK_U, KeyEvent.ALT_MASK), UploadWindow.class),
    SAVE_STATE("Save desktop state", KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.ALT_MASK), MenuGroup.FILE,
            new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    // todo: save state to preferences
                    LOG.info("Save dekstop state to preferences");
                }
            }
    ),
    EXIT("Exit", KeyStroke.getKeyStroke(KeyEvent.VK_Q, KeyEvent.ALT_MASK), MenuGroup.FILE,
            new AbstractAction() {

                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    System.exit(0);
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

    public Action getAction() {
        return action;
    }

    public Class<? extends DesktopWindow> getDesktopWindow() {
        return desktopWindow;
    }
}
