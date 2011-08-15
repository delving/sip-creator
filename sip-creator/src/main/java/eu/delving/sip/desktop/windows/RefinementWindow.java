/*
 * Copyright 2011 DELVING BV
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

import eu.europeana.sip.model.SipModel;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.GridLayout;

/**
 * Refining the mapping interactively
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class RefinementWindow extends DesktopWindow {

    public RefinementWindow(SipModel sipModel) {
        super(sipModel);
        add(createWest(), BorderLayout.WEST);
        add(createCenter(), BorderLayout.CENTER);
    }

    private JComponent createWest() {
        return new JLabel("Mapping List");
    }

    private JComponent createCenter() {
        JPanel p = new JPanel(new GridLayout(0, 1, 5, 5));
        p.add(createCodePane());
        p.add(createOutputPane());
        return p;
    }

    private JComponent createCodePane() {
        // todo: code window contains dictionary mapping buttons
        return new JLabel("Code");
    }

    private JComponent createOutputPane() {
        return new JLabel("Output");
    }

    // todo: dictionary mapping popup
}
