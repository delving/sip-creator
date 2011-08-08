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

package eu.europeana.sip.desktop.windows;

import org.apache.log4j.Logger;

import javax.swing.*;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Control the desktop.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class DesktopManager {

    private static final Logger LOG = Logger.getRootLogger();
    private static DesktopManager instance;
    private Map<DesktopWindow.WindowId, DesktopWindow> windows = new HashMap<DesktopWindow.WindowId, DesktopWindow>();
    private JDesktopPane desktop;

    {
        windows.put(DesktopWindow.WindowId.ANALYZE, new AnalyzeWindow(DesktopWindow.WindowId.ANALYZE));
        windows.put(DesktopWindow.WindowId.MAPPING, new MappingWindow(DesktopWindow.WindowId.MAPPING));
        windows.put(DesktopWindow.WindowId.PREVIEW, new PreviewWindow(DesktopWindow.WindowId.PREVIEW));
        windows.put(DesktopWindow.WindowId.WELCOME, new WelcomeWindow(DesktopWindow.WindowId.WELCOME));
        windows.put(DesktopWindow.WindowId.UPLOAD, new UploadWindow(DesktopWindow.WindowId.UPLOAD));
        windows.put(DesktopWindow.WindowId.NORMALIZE, new NormalizeWindow(DesktopWindow.WindowId.NORMALIZE));
    }

    private DesktopManager() {
        desktop = new JDesktopPane();
    }

    public static DesktopManager getInstance() {
        if (null == instance) {
            instance = new DesktopManager();
        }
        return instance;
    }

    public JDesktopPane getDesktop() {
        return getInstance().desktop;
    }

    public static DesktopWindow getWindow(DesktopWindow.WindowId windowId) {
        return getInstance().windows.get(windowId);
    }

    public void add(DesktopWindow desktopWindow) {
        if (!windows.containsKey(desktopWindow.getId())) {
            windows.put(desktopWindow.getId(), desktopWindow);
        }
        add(desktopWindow.getId());
    }

    public void add(DesktopWindow.WindowId windowId) {
        DesktopWindow window = getWindow(windowId);
        if (getAllWindows().contains(window)) {
            window.moveToFront();
            try {
                window.setSelected(true);
            }
            catch (PropertyVetoException e) {
                LOG.error("Error selecting window", e);
            }
            return;
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
