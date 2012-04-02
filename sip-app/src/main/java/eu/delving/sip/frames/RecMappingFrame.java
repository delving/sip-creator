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

import eu.delving.metadata.MappingFunction;
import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.RecDefNode;
import eu.delving.metadata.RecDefTree;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.model.MappingModel;
import eu.delving.sip.model.RecDefTreeNode;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.model.SourceTreeNode;
import eu.delving.sip.panels.NodeMappingPanel;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.event.ActionEvent;

/**
 * Render the record mapping as a list of NodeMapping instances
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class RecMappingFrame extends FrameBase {
    private RemoveNodeMappingAction removeAction = new RemoveNodeMappingAction();
    private NodeMappingPanel nodeMappingPanel = new NodeMappingPanel();

    public RecMappingFrame(JDesktopPane desktop, SipModel sipModel) {
        super(Which.REC_MAPPING, desktop, sipModel, "Node Mappings", false);
        sipModel.getMappingModel().addSetListener(new MappingModel.SetListener() {
            @Override
            public void recMappingSet(MappingModel mappingModel) {
                updateList();
            }
        });
        sipModel.getMappingModel().addChangeListener(new MappingModel.ChangeListener() {
            @Override
            public void functionChanged(MappingModel mappingModel, MappingFunction function) {
            }

            @Override
            public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
            }

            @Override
            public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
                updateList();
            }

            @Override
            public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
                updateList();
            }
        });
        nodeMappingPanel.getNodeMappingList().addListSelectionListener(new NodeMappingSelection());
    }

    private void updateList() {
        Exec.swingAny(new ListUpdater());
    }

    @Override
    protected void buildContent(Container content) {
        content.add(nodeMappingPanel, BorderLayout.CENTER);
        content.add(new JButton(removeAction), BorderLayout.SOUTH);
    }

    private class NodeMappingSelection implements ListSelectionListener {

        @Override
        public void valueChanged(ListSelectionEvent event) {
            if (event.getValueIsAdjusting()) return;
            final NodeMapping selected = (NodeMapping) nodeMappingPanel.getNodeMappingList().getSelectedValue();
            if (selected != null) {
                Exec.work(new Runnable() {
                    @Override
                    public void run() {
                        sipModel.getCreateModel().setNodeMapping(selected);
                    }
                });
            }
        }
    }

    private class ListUpdater implements Runnable {
        private RecDefTree recDefTree;

        private ListUpdater() {
            if (sipModel.getMappingModel().hasRecMapping())
                recDefTree = sipModel.getMappingModel().getRecMapping().getRecDefTree();
        }

        @Override
        public void run() {
            nodeMappingPanel.setList(recDefTree.getNodeMappings());
            nodeMappingPanel.select(sipModel.getCreateModel().getNodeMapping());
        }
    }

    private class RemoveNodeMappingAction extends AbstractAction implements Runnable {

        private RemoveNodeMappingAction() {
            super("Remove selected mapping");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            Exec.work(this);
        }

        @Override
        public void run() {
            NodeMapping nodeMapping = sipModel.getCreateModel().getNodeMapping();
            if (nodeMapping != null) {
                TreePath treePath = sipModel.getMappingModel().getTreePath(nodeMapping.outputPath);
                if (treePath != null) {
                    RecDefTreeNode node = (RecDefTreeNode) treePath.getLastPathComponent();
                    nodeMapping = node.getRecDefNode().removeNodeMapping(nodeMapping.inputPath);
                    SourceTreeNode.removeStatsTreeNodes(nodeMapping);
                    sipModel.getCreateModel().setNodeMapping(null);
                    updateList();
                }
            }
        }
    }
}
