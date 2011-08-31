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

package eu.delving.sip.frames;

import eu.delving.metadata.AnalysisTree;
import eu.delving.metadata.AnalysisTreeNode;
import eu.delving.metadata.Path;
import eu.delving.sip.ProgressListener;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.model.SipModel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * The structure of the input data, tree, variables and statistics.
 * From here mappings are made.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class AnalysisFrame extends FrameBase {
    private static final String RUN_ANALYSIS = "Run the Analysis";
    private static final String ELEMENTS_PROCESSED = "%d Elements Processed";
    private JButton selectRecordRootButton = new JButton("Select Record Root ");
    private JButton selectUniqueElementButton = new JButton("Select Unique Element");
    private JButton analyzeButton = new JButton(RUN_ANALYSIS);
    private JButton convertButton = new JButton("Convert!");
    private JTree statisticsJTree;

    public AnalysisFrame(JDesktopPane desktop, SipModel sipModel) {
        super(desktop, sipModel, "Analysis", false);
        statisticsJTree = new JTree(sipModel.getAnalysisModel().getAnalysisTreeModel());
        statisticsJTree.getModel().addTreeModelListener(new Expander());
        statisticsJTree.setCellRenderer(new AnalysisTreeCellRenderer());
        statisticsJTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        wireUp();
        setDefaultSize(400, 800);
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createPanel());
    }

    @Override
    protected void refresh() {
    }

    private void wireUp() {
        statisticsJTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent event) {
                TreePath path = event.getPath();
                if (statisticsJTree.getSelectionModel().isPathSelected(path)) {
                    final AnalysisTree.Node node = (AnalysisTree.Node) path.getLastPathComponent();
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            selectRecordRootButton.setEnabled(node.couldBeRecordRoot());
                            selectUniqueElementButton.setEnabled(!node.couldBeRecordRoot());
                            sipModel.getAnalysisModel().selectStatistics(node.getStatistics());
                        }
                    });
                }
                else {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            selectRecordRootButton.setEnabled(false);
                            selectUniqueElementButton.setEnabled(false);
                            sipModel.getAnalysisModel().selectStatistics(null);
                        }
                    });
                }
            }
        });
        selectRecordRootButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                TreePath path = statisticsJTree.getSelectionPath();
                AnalysisTreeNode node = (AnalysisTreeNode) path.getLastPathComponent();
                Path recordRoot = node.getPath();
                if (recordRoot != null) {
                    sipModel.getAnalysisModel().setRecordRoot(recordRoot);
                }
            }
        });
        selectUniqueElementButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                TreePath path = statisticsJTree.getSelectionPath();
                AnalysisTreeNode node = (AnalysisTreeNode) path.getLastPathComponent();
                sipModel.getAnalysisModel().setUniqueElement(node.getPath());
            }
        });
        analyzeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                analyzeButton.setEnabled(false);
                performAnalysis();
            }
        });
        // todo: convert buttons should only appear when DataSetStore says it makes sense
        convertButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                String message = String.format(
                        "<html><h3>Converting source data of '%s' to standard form</h3>",
                        sipModel.getStoreModel().getStore().getSpec()
                );
                ProgressMonitor progressMonitor = new ProgressMonitor(
                        SwingUtilities.getRoot(parent),
                        "<html><h2>converting</h2>",
                        message,
                        0, 100
                );
                sipModel.convertSource(new ProgressListener.Adapter(progressMonitor) {
                    @Override
                    public void swingFinished(boolean success) {
                        // todo: implement
                    }
                });
            }
        });
    }

    private JPanel createPanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Document Structure"));
        p.add(scroll(statisticsJTree), BorderLayout.CENTER);
        JPanel south = new JPanel(new GridLayout(0, 1));
        south.add(analyzeButton);
        south.add(createSelectButtonPanel());
        south.add(convertButton);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    private JPanel createSelectButtonPanel() {
        JPanel bp = new JPanel(new GridLayout(1, 0, 5, 5));
        selectRecordRootButton.setEnabled(false);
        bp.add(selectRecordRootButton);
        selectUniqueElementButton.setEnabled(false);
        bp.add(selectUniqueElementButton);
        return bp;
    }

    private void performAnalysis() {
        sipModel.analyzeFields(new SipModel.AnalysisListener() {

            @Override
            public void finished(boolean success) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        setElementsProcessed(sipModel.getAnalysisModel().getElementCount());
                        analyzeButton.setEnabled(true);
                    }
                });
            }

            @Override
            public void analysisProgress(final long elementCount) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        setElementsProcessed(elementCount);
                    }
                });
            }
        });
    }

    private void setElementsProcessed(long count) {
        analyzeButton.setText(String.format(ELEMENTS_PROCESSED, count));
    }

    private class AnalysisTreeCellRenderer extends DefaultTreeCellRenderer {
        private Font normalFont, thickFont;

        @Override
        public Component getTreeCellRendererComponent(JTree jTree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            JLabel label = (JLabel) super.getTreeCellRendererComponent(jTree, value, selected, expanded, leaf, row, hasFocus);
            AnalysisTree.Node node = (AnalysisTree.Node) value;
            label.setFont(getNormalFont());
            if (node.isRecordRoot()) {
                label.setFont(getThickFont());
                label.setText(String.format("%s : Record Root", node));
            }
            if (node.isUniqueElement()) {
                label.setFont(getThickFont());
                label.setText(String.format("%s : Unique Element", node));
            }
            return label;
        }

        private Font getNormalFont() {
            if (normalFont == null) {
                normalFont = super.getFont();
            }
            return normalFont;
        }

        private Font getThickFont() {
            if (thickFont == null) {
                thickFont = new Font(getNormalFont().getFontName(), Font.BOLD, getNormalFont().getSize());
            }
            return thickFont;
        }
    }

    private class Expander implements TreeModelListener {

        @Override
        public void treeNodesChanged(TreeModelEvent e) {
        }

        @Override
        public void treeNodesInserted(TreeModelEvent e) {
        }

        @Override
        public void treeNodesRemoved(TreeModelEvent e) {
        }

        @Override
        public void treeStructureChanged(TreeModelEvent e) {
            Timer timer = new Timer(500, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    expandEmptyNodes((AnalysisTree.Node) statisticsJTree.getModel().getRoot());
                }
            });
            timer.setRepeats(false);
            timer.start();
        }

        private void expandEmptyNodes(AnalysisTree.Node node) {
            if (node.couldBeRecordRoot()) {
                TreePath path = node.getTreePath();
                statisticsJTree.expandPath(path);
            }
            for (AnalysisTree.Node childNode : node.getChildNodes()) {
                expandEmptyNodes(childNode);
            }
        }
    }
}
