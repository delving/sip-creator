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

import eu.delving.metadata.*;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.model.RecDefTreeNode;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.MouseEvent;

/**
 * The structure of the input data, tree, variables and statistics.
 * From here mappings are made.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class RecDefFrame extends FrameBase {
    private JTree recDefTree;
    private CreateFrame createFrame;

    public RecDefFrame(JDesktopPane desktop, SipModel sipModel, TransferHandler transferHandler, CreateFrame createFrame) {
        super(desktop, sipModel, "Record Definition", false);
        this.createFrame = createFrame;
        sipModel.getMappingModel().addListener(new MappingModel.Listener() {
            @Override
            public void recMappingSet(MappingModel mappingModel) {
                Exec.swing(new TreeUpdater());
            }

            @Override
            public void factChanged(MappingModel mappingModel) {
            }

            @Override
            public void nodeMappingSelected(MappingModel mappingModel) {
            }

            @Override
            public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            }

            @Override
            public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            }
        });
        recDefTree = new JTree(new DefaultTreeModel(RecDefTreeNode.create("Empty"))) {
            @Override
            public String getToolTipText(MouseEvent evt) {
                TreePath treePath = recDefTree.getPathForLocation(evt.getX(), evt.getY());
                return treePath != null ? ((RecDefTreeNode) treePath.getLastPathComponent()).toHtml() : "";
            }
        };
        recDefTree.setToolTipText("?");
        recDefTree.setCellRenderer(new RecDefTreeNode.Renderer());
        recDefTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        recDefTree.getSelectionModel().addTreeSelectionListener(new RecDefSelection());
        recDefTree.setDropMode(DropMode.ON);
        recDefTree.setTransferHandler(transferHandler);
    }

    public void setPath(Path path) {
        TreePath treePath = getTreePath(path, recDefTree.getModel());
        recDefTree.setSelectionPath(treePath);
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createRecDefTreePanel());
    }

    @Override
    protected void refresh() {
        new TreeUpdater().run();
    }

    private JPanel createRecDefTreePanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.add(scroll(recDefTree), BorderLayout.CENTER);
        return p;
    }

    private TreePath getTreePath(Path path, TreeModel model) {
        return getTreePath(path, (RecDefTreeNode) model.getRoot());
    }

    private TreePath getTreePath(Path path, RecDefTreeNode node) {
        if (node.getRecDefPath().getTagPath().equals(path)) {
            return node.getRecDefPath();
        }
        for (RecDefTreeNode sub : node.getChildren()) {
            TreePath subPath = getTreePath(path, sub);
            if (subPath != null) return subPath;
        }
        return null;
    }

    private class RecDefSelection implements TreeSelectionListener {

        @Override
        public void valueChanged(TreeSelectionEvent event) {
            Object last = event.getPath().getLastPathComponent();
            if (last instanceof RecDefTreeNode) {
                RecDefTreeNode node = (RecDefTreeNode) last;
                showPath(node);
                createFrame.setRecDefNode(node);
            }
            else {
                createFrame.setRecDefNode(null);
            }
        }
    }

    private void showPath(RecDefTreeNode node) {
        RecDefTreeNode root = (RecDefTreeNode) recDefTree.getModel().getRoot();
        root.showPath(recDefTree, node.getRecDefPath().getTagPath());
    }

    private class TreeUpdater implements Runnable {

        @Override
        public void run() {
            RecMapping recMapping = sipModel.getMappingModel().getRecMapping();
            if (recMapping != null) {
                RecDefTreeNode node = RecDefTreeNode.create(recMapping.getRecDefTree().getRoot());
                recDefTree.setModel(new DefaultTreeModel(node));
                showPath(node);
            }
            else {
                recDefTree.setModel(new DefaultTreeModel(RecDefTreeNode.create("No record definition")));
            }
        }
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(270, 300);
    }
}
