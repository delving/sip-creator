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

import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecDefNode;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.model.MappingModel;
import eu.delving.sip.model.RecDefTreeNode;
import eu.delving.sip.model.SipModel;

import javax.swing.DropMode;
import javax.swing.JDesktopPane;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
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

    public RecDefFrame(JDesktopPane desktop, SipModel sipModel) {
        super(desktop, sipModel, "Record Definition", false);
        sipModel.getMappingModel().addListener(new MappingModel.Listener() {
            @Override
            public void recMappingSet(MappingModel mappingModel) {
                Exec.swing(new TreeUpdater());
            }

            @Override
            public void factChanged(MappingModel mappingModel) {
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
        recDefTree.setDragEnabled(false);
        recDefTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        recDefTree.getSelectionModel().addTreeSelectionListener(new RecDefSelection());
        recDefTree.setDropMode(DropMode.ON);
        recDefTree.setTransferHandler(sipModel.getNodeTransferHandler());
    }

    public void setPath(Path path) {
        recDefTree.setSelectionPath(sipModel.getMappingModel().getTreePath(path));
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

    private class RecDefSelection implements TreeSelectionListener {

        @Override
        public void valueChanged(TreeSelectionEvent event) {
            Object last = event.getPath().getLastPathComponent();
            if (last instanceof RecDefTreeNode) {
                RecDefTreeNode node = (RecDefTreeNode) last;
                showPath(node);
                sipModel.getCreateModel().setRecDefTreeNode(node);
            }
            else {
                sipModel.getCreateModel().setRecDefTreeNode(null);
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
            RecDefTreeNode root = sipModel.getMappingModel().getRecDefTreeRoot();
            if (root != null) {
                recDefTree.setModel(new DefaultTreeModel(root));
                showPath(root);
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
