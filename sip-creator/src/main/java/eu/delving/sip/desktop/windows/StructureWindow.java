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
import javax.swing.JTabbedPane;
import java.awt.GridLayout;

/**
 * The structure of the input data, tree, variables and statistics.
 * From here mappings are made.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class StructureWindow extends DesktopWindow {

    public StructureWindow(SipModel sipModel) {
        super(sipModel);
        setLayout(new GridLayout(1, 0, 5, 5));
        add(createLeft());
        add(createRight());
    }

    private JComponent createLeft() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Input Document", createTreePanel());
        tabs.add("Variables / Mapping", createVarsPanel());
        return tabs;
    }

    private JComponent createTreePanel() {
        return new JLabel("Tree");
    }

    private JComponent createVarsPanel() {
        // todo: vars panel: constant field, obvious button, map button
        return new JLabel("Vars");
    }

    private JComponent createRight() {
        return new JLabel("Statistics");
    }

    // todo: popup for obvious mappings
    // todo: popup for making a mapping
}
