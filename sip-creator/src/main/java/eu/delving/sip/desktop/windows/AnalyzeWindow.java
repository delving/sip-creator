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

import eu.delving.metadata.FieldStatistics;
import eu.delving.metadata.Path;
import eu.delving.sip.FileStore;
import eu.europeana.sip.gui.AnalysisFactsPanel;
import eu.europeana.sip.model.SipModel;
import org.apache.log4j.Logger;

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

    private static final Logger LOG = Logger.getRootLogger();
    private DocumentStructurePanel documentStructurePanel;

    public AnalyzeWindow(SipModel sipModel) {
        super(sipModel);
        buildLayout();
        SipModel.UpdateListener updateListener = new SipModel.UpdateListener() {

            @Override
            public void updatedDataSetStore(FileStore.DataSetStore dataSetStore) {
                LOG.info("Updated data set store " + dataSetStore);
                documentStructurePanel.setTitle(dataSetStore.getSpec());
            }

            @Override
            public void updatedStatistics(FieldStatistics fieldStatistics) {
                LOG.info("Updated field statistics " + fieldStatistics);
            }

            @Override
            public void updatedRecordRoot(Path recordRoot, int recordCount) {
                LOG.info("Updated record root " + recordRoot + " " + recordCount);
            }

            @Override
            public void normalizationMessage(boolean complete, String message) {
                LOG.info("Normalization : " + complete + " " + message);
            }
        };
        sipModel.addUpdateListener(updateListener);
    }

    private void buildLayout() {
        documentStructurePanel = new DocumentStructurePanel("-");
        StatisticsPanel statisticsPanel = new StatisticsPanel();
//        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, documentStructurePanel, statisticsPanel);
//        splitPane.setPreferredSize(getPreferredSize());
//        splitPane.setPreferredSize(new Dimension(1000, 500));
        add(statisticsPanel);
        setSize(new Dimension(1200, 700));
    }

    private class StatisticsPanel extends JPanel {

        private AnalysisFactsPanel panel = new AnalysisFactsPanel(sipModel);

        private StatisticsPanel() {
            panel.setSize(new Dimension(700, 400));
            add(panel);
        }
    }

    /**
     * todo: document structure will be presented as a TreeTable.
     */
    private class DocumentStructurePanel extends JPanel {

        private JLabel title = new JLabel();
        private JTree tree;

        public DocumentStructurePanel(String spec) {
            buildLayout();
            title.setText(spec);
        }


        private void buildLayout() {
            setLayout(new BorderLayout());
            add(new JLabel("Document structure"), BorderLayout.NORTH);
            tree = new JTree(createTreeModel());
            JScrollPane scrollPane = new JScrollPane(tree);
            scrollPane.setPreferredSize(new Dimension(300, 300));
            add(title, BorderLayout.NORTH);
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

        public void setTitle(String spec) {
            title.setText(spec);
        }
    }
}
