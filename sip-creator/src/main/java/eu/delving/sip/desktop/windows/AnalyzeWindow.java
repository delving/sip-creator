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

import javax.swing.*;

/**
 * The analyze window will present the following data:
 *
 * <ul>
 * <li>Statistics</li>
 * <li>Document structure</li>
 * </ul>
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class AnalyzeWindow extends DesktopWindow {

    private JTabbedPane tabbedPane = new JTabbedPane();

    public AnalyzeWindow() {
        buildLayout();
    }

    private void buildLayout() {
        tabbedPane.setPreferredSize(getPreferredSize());
        tabbedPane.addTab("Document stucture", new DocumentStructurePanel());
        tabbedPane.addTab("Statistics", new StatisticsPanel());
        add(tabbedPane);
    }

    private class StatisticsPanel extends JPanel {


        private StatisticsPanel() {
            buildLayout();
        }

        private void buildLayout() {
        }
    }

    private class DocumentStructurePanel extends JPanel {

    }
}
