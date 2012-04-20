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
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.files.Storage;
import eu.delving.sip.model.*;
import org.apache.log4j.lf5.viewer.categoryexplorer.TreeModelAdapter;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.SortedSet;
import java.util.TreeSet;

import static eu.delving.sip.base.SwingHelper.scrollVH;
import static eu.delving.sip.files.DataSetState.ABSENT;
import static eu.delving.sip.files.DataSetState.ANALYZED_SOURCE;

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
    private JPanel treePanel = new JPanel(new BorderLayout());
    private JPanel selectButtonPanel = new JPanel(new GridLayout(1, 0, 5, 5));
    private JPanel filterPanel = new JPanel(new BorderLayout(10, 10));
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
        sourceTree = new JTree(sipModel.getStatsModel().getSourceTreeModel()) {
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
        treePanel.add(scrollVH(sourceTree), BorderLayout.CENTER);
        recordRootButton.setEnabled(false);
        selectButtonPanel.add(recordRootButton);
        uniqueElementButton.setEnabled(false);
        selectButtonPanel.add(uniqueElementButton);
        filterPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(), BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        filterPanel.add(new JLabel("Filter:", JLabel.RIGHT), BorderLayout.WEST);
        filterPanel.add(filterField, BorderLayout.CENTER);
        filterPanel.add(autoFoldBox, BorderLayout.EAST);
        wireUp();
    }

    private void reactToState(DataSetState state) {
        if (!state.atLeast(DataSetState.ANALYZED_IMPORT)) sipModel.getStatsModel().setStatistics(null);
        boolean newDelimited = state.atLeast(ANALYZED_SOURCE);
        if (newDelimited != delimited) {
            delimited = newDelimited;
            getContentPane().removeAll();
            buildContent(getContentPane());
            getContentPane().validate();
        }
    }

    @Override
    protected void buildContent(Container content) {
        content.add(filterPanel, BorderLayout.NORTH);
        content.add(treePanel, BorderLayout.CENTER);
        if (!delimited) content.add(selectButtonPanel, BorderLayout.SOUTH);
    }

    private void onDoubleClick() {
        statisticsFrame.setPlacement(getPlacement());
        statisticsFrame.openFrame();
    }

    private void wireUp() {
        sipModel.getCreateModel().addListener(new CreateModel.Listener() {
            @Override
            public void transition(CreateModel createModel, CreateTransition transition) {
                switch (transition) {
                    case COMPLETE_TO_COMPLETE:
                    case NOTHING_TO_COMPLETE:
                        Exec.swing(new Runnable() {
                            @Override
                            public void run() {
                                sourceTree.clearSelection();
                            }
                        });
                        break;
                }
            }
        });
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
            public void dataSetChanged(final DataSet dataSet) {
                if (dataSet != null) {
                    reactToState(dataSet.getState());
                    String kind = delimited ? "Source" : "Imported";
                    treePanel.setBorder(BorderFactory.createTitledBorder(
                            String.format("%s Data for \"%s\"", kind, dataSet.getSpec()
                            )));
                    dataSetStateChanged(dataSet, dataSet.getState());
                }
                else {
                    treePanel.setBorder(BorderFactory.createEtchedBorder());
                }
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
                if (selectionPaths != null) {
                    for (TreePath path : selectionPaths) {
                        SourceTreeNode node = (SourceTreeNode) path.getLastPathComponent();
                        if (autoFoldBox.isSelected()) showPath(node);
                        if (node.getTag().equals(Tag.attribute(Storage.FACTS_TAG))) continue;
                        if (node.getTag().equals(Tag.attribute(Storage.ENVELOPE_TAG))) continue;
                        nodeList.add(node);
                    }
                }
                if (nodeList.size() == 1 && !delimited) {
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
                                parentPath = node.getPath(true).takeFirst();
                            }
                            else {
                                if (!parentPath.equals(node.getPath(true).takeFirst())) {
                                    sourceTree.clearSelection();
                                    return;
                                }
                            }
                        }
                    }
                }
                if (!nodeList.isEmpty()) Exec.work(new Runnable() {
                    @Override
                    public void run() {
                        sipModel.getCreateModel().setSource(nodeList);
                    }
                });
            }
        });
        sourceTree.addMouseListener(new MouseInputAdapter() {
            @Override
            public void mousePressed(MouseEvent mouseEvent) {
                if (mouseEvent.getClickCount() == 2) onDoubleClick();
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
