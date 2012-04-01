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
import eu.delving.metadata.Tag;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Utility;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.files.Storage;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.FilterTreeModel;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.model.SourceTreeNode;
import org.apache.log4j.lf5.viewer.categoryexplorer.TreeModelAdapter;

import javax.swing.*;
import javax.swing.event.*;
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
import java.util.SortedSet;
import java.util.TreeSet;

import static eu.delving.sip.files.DataSetState.ABSENT;

/**
 * The structure of the input data, tree, variables and statistics.
 * From here mappings are made.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class SourceFrame extends FrameBase {
    private JButton recordRootButton = new JButton("Select Record Root ");
    private JButton uniqueElementButton = new JButton("Select Unique Element");
    private JTree sourceTree;
    private boolean delimited = false;
    private JTextField filterField = new JTextField();
    private JCheckBox autoFoldBox = new JCheckBox("Auto-Fold");
    private StatisticsFrame statisticsFrame;
    private Timer timer = new Timer(300, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            Object model = sourceTree.getModel();
            if (model instanceof FilterTreeModel) {
                FilterTreeModel ftm = (FilterTreeModel) model;
                ftm.setFilter(filterField.getText().trim());
            }
        }
    });

    public SourceFrame(JDesktopPane desktop, SipModel sipModel, StatisticsFrame statisticsFrame) {
        super(Which.SOURCE, desktop, sipModel, "Source", false);
        this.statisticsFrame = statisticsFrame;
        sourceTree = new JTree(sipModel.getStatsModel().getStatsTreeModel()) {
            @Override
            public String getToolTipText(MouseEvent evt) {
                TreePath treePath = sourceTree.getPathForLocation(evt.getX(), evt.getY());
                return treePath != null ? ((SourceTreeNode) treePath.getLastPathComponent()).toHtml() : "";
            }
        };
        sourceTree.setToolTipText("?");
        sourceTree.setCellRenderer(new SourceTreeNode.Renderer());
        sourceTree.setTransferHandler(sipModel.getNodeTransferHandler());
        sourceTree.setDragEnabled(true);
        sourceTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        timer.setRepeats(false);
        wireUp();
    }

    private void reactToState(DataSetState state) {
        if (state.ordinal() < DataSetState.ANALYZED_IMPORT.ordinal()) sipModel.getStatsModel().setStatistics(null);
        boolean newDelimited = state.ordinal() >= DataSetState.ANALYZED_SOURCE.ordinal();
        if (newDelimited != delimited) {
            delimited = newDelimited;
            getContentPane().removeAll();
            buildContent(getContentPane());
            getContentPane().validate();
        }
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createFilterPanel(), BorderLayout.NORTH);
        content.add(createPanel(), BorderLayout.CENTER);
    }

    private JPanel createPanel() {
        return delimited ? createSourcePanel() : createImportedPanel();
    }

    private JPanel createFilterPanel() {
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(), BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        p.add(new JLabel("Filter:", JLabel.RIGHT), BorderLayout.WEST);
        p.add(filterField, BorderLayout.CENTER);
        p.add(autoFoldBox, BorderLayout.EAST);
        return p;
    }

    private JPanel createImportedPanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Analysis of imported data"));
        p.add(Utility.scrollVH(sourceTree), BorderLayout.CENTER);
        p.add(createSelectButtonPanel(), BorderLayout.SOUTH);
        return p;
    }

    private JPanel createSourcePanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Source data"));
        p.add(Utility.scrollVH(sourceTree), BorderLayout.CENTER);
        return p;
    }

    private JPanel createSelectButtonPanel() {
        JPanel bp = new JPanel(new GridLayout(1, 0, 5, 5));
        recordRootButton.setEnabled(false);
        bp.add(recordRootButton);
        uniqueElementButton.setEnabled(false);
        bp.add(uniqueElementButton);
        return bp;
    }

    private void onDoubleClick() {
        statisticsFrame.setPlacement(getPlacement());
        statisticsFrame.openFrame();
    }

    private void wireUp() {
        sourceTree.getModel().addTreeModelListener(new TreeModelAdapter() {
            @Override
            public void treeStructureChanged(TreeModelEvent treeModelEvent) {
                SourceTreeNode node = (SourceTreeNode) treeModelEvent.getTreePath().getLastPathComponent();
                showPath(node);
            }
        });
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                timer.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
                timer.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
                timer.restart();
            }
        });
        sipModel.getDataSetModel().addListener(new DataSetModel.Listener() {
            @Override
            public void dataSetChanged(DataSet dataSet) {
                dataSetStateChanged(dataSet, dataSet.getState());
            }

            @Override
            public void dataSetRemoved() {
                reactToState(ABSENT);
            }

            @Override
            public void dataSetStateChanged(DataSet dataSet, DataSetState dataSetState) {
                reactToState(dataSetState);
            }
        });
        sourceTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent event) {
                final SortedSet<SourceTreeNode> nodeList = new TreeSet<SourceTreeNode>();
                TreePath[] selectionPaths = sourceTree.getSelectionModel().getSelectionPaths();
                if (selectionPaths != null && delimited) {
                    for (TreePath path : selectionPaths) {
                        SourceTreeNode node = (SourceTreeNode)path.getLastPathComponent();
                        if (autoFoldBox.isSelected()) showPath(node);
                        if (node.getTag().equals(Tag.attribute(Storage.FACTS_TAG))) continue;
                        if (node.getTag().equals(Tag.attribute(Storage.ENVELOPE_TAG))) continue;
                        nodeList.add(node);
                    }
                }
                if (nodeList.size() == 1) {
                    SourceTreeNode node = nodeList.iterator().next();
                    recordRootButton.setEnabled(node.couldBeRecordRoot());
                    uniqueElementButton.setEnabled(node.couldBeUniqueElement());
                }
                else {
                    recordRootButton.setEnabled(false);
                    uniqueElementButton.setEnabled(false);
                    if (!nodeList.isEmpty()) {
                        Path parentPath = null;
                        for (SourceTreeNode node : nodeList) {
                            if (parentPath == null) {
                                parentPath = node.getPath(true).chop(1);
                            }
                            else {
                                if (!parentPath.equals(node.getPath(true).chop(1))) {
                                    sourceTree.clearSelection();
                                    return;
                                }
                            }
                        }
                    }
                }
                Exec.work(new Runnable() {
                    @Override
                    public void run() {
                        sipModel.getCreateModel().setSourceTreeNodes(nodeList);
                    }
                });
            }
        });
        sourceTree.addMouseListener(new MouseListener() {
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
        recordRootButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                TreePath path = sourceTree.getSelectionPath();
                SourceTreeNode node = (SourceTreeNode) path.getLastPathComponent();
                Path recordRoot = node.getPath(true);
                if (recordRoot != null) {
                    sipModel.getStatsModel().setRecordRoot(recordRoot);
                }
            }
        });
        uniqueElementButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                TreePath path = sourceTree.getSelectionPath();
                SourceTreeNode node = (SourceTreeNode) path.getLastPathComponent();
                sipModel.getStatsModel().setUniqueElement(node.getPath(true));
            }
        });
    }

    private void showPath(SourceTreeNode node) {
        SourceTreeNode root = (SourceTreeNode) sourceTree.getModel().getRoot();
        root.showPath(sourceTree, node.getPath(true));
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(270, 300);
    }
}
