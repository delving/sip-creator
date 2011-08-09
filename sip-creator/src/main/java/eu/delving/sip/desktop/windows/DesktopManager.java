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

import eu.delving.sip.desktop.listeners.DataSetChangeListener;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.beans.PropertyVetoException;
import java.util.*;


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

    public DesktopManager(DataSetChangeListener dataSetChangeListener) {
        this.dataSetChangeListener = dataSetChangeListener;
        desktop = new JDesktopPane();
        initialize();
    }

    private void initialize() {
        for (WindowId windowId : WindowId.values()) {
            if (null != windowId.getDesktopWindow()) {
                try {
                    DesktopWindow desktopWindow = windowId.getDesktopWindow().newInstance();
                    desktopWindow.setId(windowId);
                    desktopWindow.setDataSetChangeListener(dataSetChangeListener);
                    windows.put(windowId, desktopWindow);
                    LOG.info("Done creating " + windowId);
                }
                catch (InstantiationException e) {
                    LOG.error("Can't instantiate window " + windowId, e);
                }
                catch (IllegalAccessException e) {
                    LOG.error("Illegal access " + windowId.getTitle(), e);
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
            try {
                window.setSelected(true);
            }
            catch (PropertyVetoException e) {
                LOG.error("Error selecting window", e);
            }
            return;
        }
        if (null == window) {
            throw new NoSuchElementException("Window doesn't exist");
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
}
