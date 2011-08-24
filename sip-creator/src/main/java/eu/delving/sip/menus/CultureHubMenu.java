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

package eu.delving.sip.menus;

import eu.delving.sip.base.CultureHubClient;
import eu.delving.sip.model.SipModel;

import javax.swing.JDesktopPane;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * The menu for interfacing with the culture hub
 *
 * @author Gerald de Jong, Delving BV, <geralddejong@gmail.com>
 */

public class CultureHubMenu extends JMenu {
    private JDesktopPane parent;
    private SipModel sipModel;
    private CultureHubClient cultureHubClient;
    private JMenuItem download = new JMenuItem("Download");
    private JMenuItem upload = new JMenuItem("Upload");

    public CultureHubMenu(JDesktopPane parent, SipModel sipModel, CultureHubClient cultureHubClient) {
        super("Culture Hub");
        this.parent = parent;
        this.sipModel = sipModel;
        this.cultureHubClient = cultureHubClient;
        add(download);
        add(upload);
        wireUp();
    }

    private void wireUp() {
        download.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JOptionPane.showInternalMessageDialog(parent, "Not implemented yet");
                // todo: trigger upload of any files of current set not matching hash
            }
        });
        upload.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JOptionPane.showInternalMessageDialog(parent, "Not implemented yet");
                // todo: trigger upload of any files of current set not matching hash
            }
        });
    }
}
