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

import eu.europeana.sip.model.SipModel;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import java.awt.*;

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

    public AnalyzeWindow(SipModel sipModel) {
        super(sipModel);
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

    /**
     * todo: document structure will be presented as a TreeTable.
     */
    private class DocumentStructurePanel extends JPanel {

        private JTree tree;

        {
            buildLayout();
        }

        private void buildLayout() {
            setLayout(new BorderLayout());
            add(new JLabel("Document structure"), BorderLayout.NORTH);
            tree = new JTree(createTreeModel());
            JScrollPane scrollPane = new JScrollPane(tree);
            scrollPane.setPreferredSize(new Dimension(300, 300));
            add(scrollPane, BorderLayout.CENTER);
        }

        // todo: this is mock data, replace with actual data from SIPModel
        private TreeModel createTreeModel() {
            DefaultMutableTreeNode root = new DefaultMutableTreeNode("adlibXML");
            DefaultMutableTreeNode diagnostic = new DefaultMutableTreeNode("diagnostic");
            diagnostic.add(new DefaultMutableTreeNode("abc"));
            diagnostic.add(new DefaultMutableTreeNode("def"));
            diagnostic.add(new DefaultMutableTreeNode("ghi"));
            root.add(diagnostic);
            DefaultMutableTreeNode unknown = new DefaultMutableTreeNode("unknown");
            unknown.add(new DefaultMutableTreeNode("123"));
            unknown.add(new DefaultMutableTreeNode("456"));
            unknown.add(new DefaultMutableTreeNode("789"));
            root.add(unknown);
            return new DefaultTreeModel(root);
        }
    }
}
