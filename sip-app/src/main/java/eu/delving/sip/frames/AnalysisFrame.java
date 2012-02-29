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

package eu.delving.sip.frames;

import eu.delving.metadata.Path;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.StatsTreeNode;
import eu.delving.sip.base.Utility;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;

import static eu.delving.sip.files.DataSetState.ABSENT;

/**
 * The structure of the input data, tree, variables and statistics.
 * From here mappings are made.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class AnalysisFrame extends FrameBase {
    private JButton selectRecordRootButton = new JButton("Select Record Root ");
    private JButton selectUniqueElementButton = new JButton("Select Unique Element");
    private JTree statisticsJTree;
    private DataSetState dataSetState;
    private StatisticsFrame statisticsFrame;

    public AnalysisFrame(JDesktopPane desktop, SipModel sipModel, StatisticsFrame statisticsFrame) {
        super(desktop, sipModel, "Analysis", false);
        this.statisticsFrame = statisticsFrame;
        statisticsJTree = new JTree(sipModel.getStatsModel().getStatsTreeModel()) {
            @Override
            public String getToolTipText(MouseEvent evt) {
                TreePath treePath = statisticsJTree.getPathForLocation(evt.getX(), evt.getY());
                return treePath != null ? ((StatsTreeNode) treePath.getLastPathComponent()).toHtml() : "";
            }
        };
        statisticsJTree.setToolTipText("?");
        statisticsJTree.getModel().addTreeModelListener(new Expander());
        statisticsJTree.setCellRenderer(new StatsTreeNode.Renderer());
        statisticsJTree.setTransferHandler(sipModel.getNodeTransferHandler());
        statisticsJTree.setDragEnabled(true);
        statisticsJTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        wireUp();
        sipModel.getDataSetModel().addListener(new DataSetModel.Listener() {
            @Override
            public void dataSetChanged(DataSet dataSet) {
                dataSetStateChanged(dataSet, dataSet.getState());
                dataSetState = dataSet.getState();
            }

            @Override
            public void dataSetRemoved() {
                dataSetState = ABSENT;
            }

            @Override
            public void dataSetStateChanged(DataSet dataSet, DataSetState dataSetState) {
                AnalysisFrame.this.dataSetState = dataSetState;
// todo: when the analysis was done on the source format, we don't want these enabled!
//                selectUniqueElementButton.setEnabled(??);
//                selectRecordRootButton.setEnabled(??);
            }
        });
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createPanel());
    }

    private void onDoubleClick() {
        statisticsFrame.setPlacement(getPlacement());
        statisticsFrame.openFrame();
    }

    private void wireUp() {
        statisticsJTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent event) {
                final List<StatsTreeNode> nodeList = new ArrayList<StatsTreeNode>();
                for (TreePath path : statisticsJTree.getSelectionModel().getSelectionPaths()) {
                    if (path.getPathCount() > 2) nodeList.add((StatsTreeNode) path.getLastPathComponent());
                }
                if (nodeList.size() == 1) {
                    StatsTreeNode node = nodeList.get(0);
                    selectRecordRootButton.setEnabled(node.couldBeRecordRoot() && adjustable());
                    selectUniqueElementButton.setEnabled(node.couldBeUniqueElement() && adjustable());
                }
                else {
                    selectRecordRootButton.setEnabled(false);
                    selectUniqueElementButton.setEnabled(false);
                }
                Exec.work(new Runnable() {
                    @Override
                    public void run() {
                        sipModel.getCreateModel().setStatsTreeNodes(nodeList);
                    }
                });
            }
        });
        statisticsJTree.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
            }

            @Override
            public void mousePressed(MouseEvent mouseEvent) {
                if (mouseEvent.getClickCount() == 2) onDoubleClick();
            }

            @Override
            public void mouseReleased(MouseEvent mouseEvent) {
            }

            @Override
            public void mouseEntered(MouseEvent mouseEvent) {
            }

            @Override
            public void mouseExited(MouseEvent mouseEvent) {
            }
        });
        selectRecordRootButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                TreePath path = statisticsJTree.getSelectionPath();
                StatsTreeNode node = (StatsTreeNode) path.getLastPathComponent();
                Path recordRoot = node.getPath(true);
                if (recordRoot != null) {
                    sipModel.getStatsModel().setRecordRoot(recordRoot);
                }
            }
        });
        selectUniqueElementButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                TreePath path = statisticsJTree.getSelectionPath();
                StatsTreeNode node = (StatsTreeNode) path.getLastPathComponent();
                sipModel.getStatsModel().setUniqueElement(node.getPath(true));
            }
        });
    }

    private boolean adjustable() {
        return dataSetState == null || dataSetState.ordinal() <= DataSetState.SOURCED.ordinal();
    }

    private JPanel createPanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Document Structure"));
        p.add(Utility.scroll(statisticsJTree), BorderLayout.CENTER);
        p.add(createSelectButtonPanel(), BorderLayout.SOUTH);
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
                    expandEmptyNodes((StatsTreeNode) statisticsJTree.getModel().getRoot());
                }
            });
            timer.setRepeats(false);
            timer.start();
        }

        private void expandEmptyNodes(StatsTreeNode node) {
            if (!node.isLeaf()) {
                TreePath path = node.getTreePath();
                statisticsJTree.expandPath(path);
            }
            for (StatsTreeNode childNode : node.getChildNodes()) {
                expandEmptyNodes(childNode);
            }
        }
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(270, 300);
    }
}
