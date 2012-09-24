/*
 * Copyright 2011, 2012 Delving BV
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

import eu.delving.metadata.*;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.files.Storage;
import eu.delving.sip.model.*;
import org.apache.log4j.lf5.viewer.categoryexplorer.TreeModelAdapter;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.SortedSet;
import java.util.TreeSet;

import static eu.delving.sip.base.SwingHelper.scrollVH;

/**
 * This frame shows a tree of nodes which is gathered during the analysis process on the input data, and each
 * node contains its statistics.  A filter mechanism allows for selection in a large tree.  This frame also
 * allows for the initial choices of record root and unique element so that conversion to standard form
 * can be done.
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

    public SourceFrame(SipModel sipModel) {
        super(Which.SOURCE, sipModel, "Source");
        sourceTree = new JTree(sipModel.getStatsModel().getSourceTreeModel()) {
            @Override
            public String getToolTipText(MouseEvent evt) {
                TreePath treePath = sourceTree.getPathForLocation(evt.getX(), evt.getY());
                return treePath != null ? ((SourceTreeNode) treePath.getLastPathComponent()).getHtmlToolTip() : "";
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
        boolean newDelimited = state.atLeast(DataSetState.SOURCED);
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

    private void wireUp() {
        sipModel.getCreateModel().addListener(new CreateModel.Listener() {
            @Override
            public void transition(CreateModel createModel, CreateTransition transition) {
                switch (transition) {
                    case COMPLETE_TO_COMPLETE:
                    case NOTHING_TO_COMPLETE:
                        exec(new Swing() {
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
        sipModel.getMappingModel().addChangeListener(new MappingModel.ChangeListener() {
            @Override
            public void lockChanged(MappingModel mappingModel, boolean locked) {
                setFrameLocked(locked);
            }

            @Override
            public void functionChanged(MappingModel mappingModel, MappingFunction function) {
            }

            @Override
            public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping, NodeMappingChange change) {
            }

            @Override
            public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            }

            @Override
            public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            }
        });
        sipModel.getDataSetModel().addListener(new DataSetModel.SwingListener() {
            @Override
            public void stateChanged(DataSetModel model, DataSetState state) {
                switch (state) {
                    case ABSENT:
                    case NO_DATA:
                        treePanel.setBorder(BorderFactory.createEtchedBorder());
                        break;
                    default:
                        String kind = delimited ? "Source" : "Imported";
                        treePanel.setBorder(BorderFactory.createTitledBorder(String.format(
                                "%s Data for \"%s\"",
                                kind, model.getDataSet().getSpec()
                        )));
                        break;
                }
                reactToState(state);
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
                if (!nodeList.isEmpty()) exec(new Work() {
                    @Override
                    public void run() {
                        sipModel.getCreateModel().setSource(nodeList);
                    }

                    @Override
                    public Job getJob() {
                        return Job.SELECT_SOURCE_SET_SOURCE;
                    }
                });
            }
        });
//        sourceTree.addMouseListener(new MouseInputAdapter() {
//            @Override
//            public void mousePressed(MouseEvent mouseEvent) {
//                if (mouseEvent.getClickCount() == 2) onDoubleClick();
//            }
//        });
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
                if (!node.getStats().isUnique()) {
                    if (!sipModel.getFeedback().confirm(
                            "Unique Element",
                            "This element is not verified to be unique. Multiples will be discarded"
                    )) return;
                }
                sipModel.getStatsModel().setUniqueElement(node.getPath(true));
            }
        });
    }

    private void showPath(SourceTreeNode node) {
        SourceTreeNode root = (SourceTreeNode) sourceTree.getModel().getRoot();
        root.showPath(sourceTree, node.getPath(true));
    }
}
