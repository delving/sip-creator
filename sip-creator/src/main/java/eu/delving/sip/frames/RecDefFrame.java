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

import eu.delving.metadata.MappingModel;
import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.RecDefNode;
import eu.delving.metadata.RecMapping;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.HtmlPanel;
import eu.delving.sip.model.RecDefTreeNode;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;

/**
 * The structure of the input data, tree, variables and statistics.
 * From here mappings are made.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class RecDefFrame extends FrameBase {
    private JTree recDefTree;
    private HtmlPanel detailsPanel = new HtmlPanel("Details");

    public RecDefFrame(JDesktopPane desktop, SipModel sipModel, TransferHandler transferHandler) {
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
            public void nodeMappingSelected(MappingModel mappingModel) {
            }

            @Override
            public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            }

            @Override
            public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            }
        });
        recDefTree = new JTree(new DefaultTreeModel(RecDefTreeNode.create("Empty")));
        recDefTree.setCellRenderer(new RecDefTreeNode.Renderer());
        recDefTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        recDefTree.getSelectionModel().addTreeSelectionListener(new RecDefSelection());
        recDefTree.setSelectionRow(0);
        recDefTree.setDropMode(DropMode.ON);
        recDefTree.setTransferHandler(transferHandler);
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createTreePanel(), BorderLayout.CENTER);
        content.add(detailsPanel, BorderLayout.SOUTH);
    }

    @Override
    protected void refresh() {
        new TreeUpdater().run();
    }

    private JPanel createTreePanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Document Structure"));
        p.add(scroll(recDefTree), BorderLayout.CENTER);
        return p;
    }

    private class RecDefSelection implements TreeSelectionListener {

        @Override
        public void valueChanged(TreeSelectionEvent event) {
            Object last = event.getPath().getLastPathComponent();
            if (last instanceof RecDefTreeNode) {
                RecDefTreeNode node = (RecDefTreeNode) last;
                showHtmlFor(node);
                RecDefTreeNode root = (RecDefTreeNode) recDefTree.getModel().getRoot();
                root.showPath(recDefTree, node.getRecDefPath().getTagPath());
            }
        }
    }

    private class TreeUpdater implements Runnable {

        @Override
        public void run() {
            RecMapping recMapping = sipModel.getMappingModel().getRecMapping();
            if (recMapping != null) {
                recDefTree.setModel(new DefaultTreeModel(RecDefTreeNode.create(recMapping.getRecDefTree().getRoot())));
                recDefTree.setSelectionRow(0);
            }
            else {
                recDefTree.setModel(new DefaultTreeModel(RecDefTreeNode.create("No record definition")));
            }
        }
    }

    private void showHtmlFor(RecDefTreeNode treeNode) {
        RecDefNode node = treeNode.getRecDefNode();
        detailsPanel
                .setTemplate(node.isAttr() ? "recdef-attribute" : "recdef-element")
                .put("name", node.getTag())
                .put("doc", node.getDoc())
                .put("options", node.getOptions())
                .put("node", null) // todo: node.geInputNode())??
                .render();
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(270, 300);
    }
}
