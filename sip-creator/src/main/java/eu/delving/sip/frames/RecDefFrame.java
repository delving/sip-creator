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
    private JTextArea codeArea = new JTextArea();
    private HtmlPanel detailsPanel = new HtmlPanel("Details");

    public RecDefFrame(JDesktopPane desktop, SipModel sipModel, TransferHandler transferHandler) {
        super(desktop, sipModel, "Record Definition", false);
        sipModel.getMappingModel().addListener(new MappingModel.Listener() {
            @Override
            public void recMappingSet(MappingModel mappingModel) {
                Exec.swing(new TreeUpdater());
                Exec.swing(new CodeUpdater());
            }

            @Override
            public void factChanged(MappingModel mappingModel) {
            }

            @Override
            public void recDefNodeSelected(MappingModel mappingModel) {
            }

            @Override
            public void nodeMappingSet(MappingModel mappingModel, RecDefNode node) {
                Exec.swing(new CodeUpdater());
            }
        });
        recDefTree = new JTree(new DefaultTreeModel(RecDefTreeNode.create("Empty")));
        recDefTree.setCellRenderer(new RecDefTreeNode.Renderer());
        recDefTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        recDefTree.getSelectionModel().addTreeSelectionListener(new RecDefSelection());
        recDefTree.setSelectionRow(0);
        recDefTree.setDropMode(DropMode.ON);
        recDefTree.setTransferHandler(transferHandler);
        setDefaultSize(400, 800);
    }

    @Override
    protected void buildContent(Container content) {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Document Structure", createStructurePanel());
        tabs.addTab("Builder Code", createCodePanel());
        content.add(tabs, BorderLayout.CENTER);
    }

    @Override
    protected void refresh() {
        new TreeUpdater().run();
        new CodeUpdater().run();
    }

    private JPanel createStructurePanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Document Structure"));
        p.add(createTreePanel(), BorderLayout.CENTER);
        p.add(detailsPanel, BorderLayout.SOUTH);
        return p;
    }

    private JPanel createTreePanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Document Structure"));
        p.add(scroll(recDefTree), BorderLayout.CENTER);
        return p;
    }

    private JPanel createCodePanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createTitledBorder("Builder Code"));
        p.add(scroll(codeArea), BorderLayout.CENTER);
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
    
    private class CodeUpdater implements Runnable {

        @Override
        public void run() {
            RecMapping recMapping = sipModel.getMappingModel().getRecMapping();
            if (recMapping != null) {
                String code = recMapping.toCode(null, null);
                codeArea.setText(code);
            }
            else {
                codeArea.setText("// No code");
            }
        }
    }

    private void showHtmlFor(RecDefTreeNode treeNode) {
        RecDefNode node = treeNode.getRecDefNode();
        detailsPanel
                .setTemplate(node.isAttr() ? "templates/recdef-attribute" : "templates/recdef-element")
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
