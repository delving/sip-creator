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

import eu.europeana.sip.util.GridBagHelper;

import javax.swing.*;
import java.awt.*;

/**
 * This window consists of two tabs, a mapping tab and a constants tab.
 * Map the source fields to the target fields.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class MappingWindow extends DesktopWindow {

    private JTabbedPane tabbedPane = new JTabbedPane();

    public MappingWindow() {
        tabbedPane.addTab("Dynamic fields", new MappingPanel());
        tabbedPane.addTab("Constant fields", new ConstantsPanel());
        tabbedPane.setPreferredSize(getPreferredSize());
        add(tabbedPane);
    }

    private class MappingPanel extends JPanel {

        private JList sourceFields;
        private JList targetFields;
        private JButton button = new JButton("Create mapping");

        {
            setLayout(new GridBagLayout());
            sourceFields = new JList();
            sourceFields.setListData(new String[]{"sample.afield", "sample.bfield"});
            targetFields = new JList();
            targetFields.setListData(new String[]{"europeana:type", "europeana:rights"});
            buildLayout();
        }

        private void buildLayout() {
            GridBagHelper g = new GridBagHelper();
            g.reset();
            add(createScrollPane("Source Fields", sourceFields), g);
            g.right();
            add(createScrollPane("Target Fields", targetFields), g);
            g.line();
            g.right();
            add(button, g);
        }

        private JScrollPane createScrollPane(String title, JList list) {
            JScrollPane sourceScrollPane = new JScrollPane(list);
            sourceScrollPane.setBorder(BorderFactory.createTitledBorder(title));
            return sourceScrollPane;
        }

    }

    private class ConstantsPanel extends JPanel {

    }
}
