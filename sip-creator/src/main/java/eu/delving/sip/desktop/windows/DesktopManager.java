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

import eu.delving.security.AuthenticationClient;
import eu.delving.sip.desktop.WindowState;
import eu.delving.sip.desktop.listeners.DataSetChangeListener;
import eu.europeana.sip.model.SipModel;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;


/**
 * Control the desktop.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class DesktopManager {

    private static final Logger LOG = Logger.getRootLogger();
    private DataSetChangeListener dataSetChangeListener;
    private Map<WindowId, DesktopWindow> windows = new HashMap<WindowId, DesktopWindow>();
    private JDesktopPane desktop;
    private SipModel sipModel;
    private static AuthenticationClient authenticationClient = new AuthenticationClient();

    public static AuthenticationClient getAuthenticationClient() {
        return authenticationClient;
    }

    public DesktopManager(DataSetChangeListener dataSetChangeListener, SipModel sipModel) {
        this.dataSetChangeListener = dataSetChangeListener;
        this.sipModel = sipModel;
        desktop = new JDesktopPane();
        buildWindows();
    }

    private void buildWindows() {
        for (WindowId windowId : WindowId.values()) {
            if (null != windowId.getDesktopWindow()) {
                try {
                    DesktopWindow desktopWindow = windowId.getDesktopWindow().getConstructor(SipModel.class).newInstance(sipModel);
                    desktopWindow.setId(windowId);
                    desktopWindow.setDataSetChangeListener(dataSetChangeListener);
                    windows.put(windowId, desktopWindow);
                }
                catch (Exception e) {
                    LOG.error("Can't instantiate window : " + windowId, e);
                }
            }
        }
    }

    public JDesktopPane getDesktop() {
        return desktop;
    }

    public DesktopWindow getWindow(WindowId windowId) {
        return windows.get(windowId);
    }

    public void add(DesktopWindow desktopWindow) {
        if (!windows.containsKey(desktopWindow.getId())) {
            windows.put(desktopWindow.getId(), desktopWindow);
        }
        add(desktopWindow.getId());
    }

    public void add(WindowId windowId) {
        DesktopWindow window = getWindow(windowId);
        if (getAllWindows().contains(window)) {
            window.moveToFront();
            window.setVisible(true);
            if (window.getLocation().x < 0) {
                window.setLocation(new Point(0, window.getLocation().y));
            }
            if (window.getLocation().y < 0) {
                window.setLocation(new Point(window.getLocation().x, 0));
            }
            try {
                window.setSelected(true);
            }
            catch (PropertyVetoException e) {
                LOG.error("Error selecting window", e);
            }
            return;
        }
        if (null == window) {
            throw new NoSuchElementException(String.format("Window %s doesn't exist", windowId));
        }
        desktop.add(window);
        window.setVisible(true);
        window.setSize(DesktopWindow.DEFAULT_SIZE);
    }

    public List<DesktopWindow> getAllWindows() {
        JInternalFrame[] allFrames = desktop.getAllFrames();
        List<DesktopWindow> windows = new ArrayList<DesktopWindow>();
        for (JInternalFrame frame : allFrames) {
            if (frame instanceof DesktopWindow) {
                DesktopWindow window = (DesktopWindow) frame;
                windows.add(window);
            }
        }
        return windows;
    }

    public List<WindowState> getWindowStates() {
        List<DesktopWindow> allWindows = getAllWindows();
        if (null == allWindows) {
            LOG.info("Nothing to save");
            return null;
        }
        List<WindowState> windowStates = new ArrayList<WindowState>();
        for (DesktopWindow window : allWindows) {
            if (!window.isPreferencesTransient()) {
                windowStates.add(new WindowState(window));
            }
        }
        return windowStates;
    }
}
