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

import eu.delving.metadata.RecDef;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.HtmlPanel;
import eu.delving.sip.model.RecDefNode;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.io.IOException;

/**
 * The structure of the input data, tree, variables and statistics.
 * From here mappings are made.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class RecDefFrame extends FrameBase {
    private JTree recDefTree;
    private HtmlPanel recDefPanel = new HtmlPanel("Details");

    public RecDefFrame(JDesktopPane desktop, SipModel sipModel) {
        super(desktop, sipModel, "Record Definition", false);
        RecDef recDef;
        try {
            recDef = RecDef.read(getClass().getResource("/lido-recdef.xml").openStream());
            recDef.resolve();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        recDefTree = new JTree(new DefaultTreeModel(RecDefNode.create(recDef)));
        recDefTree.setCellRenderer(new RecDefNode.Renderer());
        recDefTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        recDefTree.getSelectionModel().addTreeSelectionListener(new RecDefSelection());
        recDefTree.setSelectionRow(0);
        setDefaultSize(400, 800);
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createTreePanel(), BorderLayout.CENTER);
        content.add(recDefPanel, BorderLayout.SOUTH);
    }

    @Override
    protected void refresh() {
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
            RecDefNode node = (RecDefNode) event.getPath().getLastPathComponent();
            showNode(node);
            RecDefNode root = (RecDefNode) recDefTree.getModel().getRoot();
            root.showPath(recDefTree, node.getPath().getOurPath());
        }

    }

    private void showNode(RecDefNode node) {
        recDefPanel
                .setTemplate(node.isAttr() ? "templates/recdef-attribute" : "templates/recdef-element")
                .put("name", node.getTag())
                .put("doc", node.getDoc())
                .put("options", node.getOptions())
                .put("node", node.getNode())
                .render();
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(270, 300);
    }
}
