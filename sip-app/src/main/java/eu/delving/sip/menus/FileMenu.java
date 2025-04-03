/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.sip.menus;

import eu.delving.sip.frames.AllFrames;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import java.awt.event.ActionListener;

/**
 * A menu for app-related options and actions.
 *
 */
public class FileMenu extends JMenu {
    private final SipModel sipModel;
    private final AllFrames allFrames;
    private final ActionListener projectListener;
    private final ActionListener closeListener;

    public FileMenu(SipModel sipModel, AllFrames allFrames, ActionListener projectListener,
                    ActionListener closeListener) {
        super("File");
        this.sipModel = sipModel;
        this.allFrames = allFrames;
        this.projectListener = projectListener;
        this.closeListener = closeListener;
        add(new ProjectMenuItem());
        add(new JSeparator());
        add(new CloseMenuItem());
    }

    private class ProjectMenuItem extends JMenuItem {
        public ProjectMenuItem() {
            super("Switch Project");
            addActionListener(projectListener);
        }
    }

    private class CloseMenuItem extends JMenuItem {
        public CloseMenuItem() {
            super("Close");
            addActionListener(closeListener);
        }
    }

}
