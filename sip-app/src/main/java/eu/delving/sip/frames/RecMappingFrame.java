/*
 * Copyright 2011, 2012 Delving BV
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.sip.frames;

import eu.delving.metadata.MappingFunction;
import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.NodeMappingChange;
import eu.delving.metadata.RecDefNode;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.menus.RevertMappingMenu;
import eu.delving.sip.model.*;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * Render the record mapping as a list of NodeMapping instances
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class RecMappingFrame extends FrameBase {
    private RemoveNodeMappingAction removeAction = new RemoveNodeMappingAction();
    private JList nodeMappingList;
    private RevertMappingMenu revertMappingMenu;

    public RecMappingFrame(JDesktopPane desktop, final SipModel sipModel) {
        super(Which.REC_MAPPING, desktop, sipModel, "Node Mappings");
        revertMappingMenu = new RevertMappingMenu(sipModel);
        setJMenuBar(createMenuBar());
        nodeMappingList = sipModel.getMappingModel().getNodeMappingListModel().createJList();
        nodeMappingList.addListSelectionListener(new NodeMappingSelection());
        sipModel.getCreateModel().addListener(new CreateModel.Listener() {
            @Override
            public void transition(CreateModel createModel, CreateTransition transition) {
                switch (transition) {
                    case COMPLETE_TO_ARMED_SOURCE:
                    case COMPLETE_TO_ARMED_TARGET:
                        Exec.swing(new Runnable() {
                            @Override
                            public void run() {
                                nodeMappingList.clearSelection();
                            }
                        });
                        break;
                }
            }
        });
        sipModel.getMappingModel().addChangeListener(new MappingModel.ChangeListener() {
            @Override
            public void lockChanged(MappingModel mappingModel, boolean locked) {
                removeAction.setEnabled(!locked);
                revertMappingMenu.setEnabled(!locked);
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
    }

    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();
        sipModel.getMappingSaveTimer().setListReceiver(revertMappingMenu);
        bar.add(revertMappingMenu);
        return bar;
    }

    @Override
    protected void buildContent(Container content) {
        content.add(SwingHelper.scrollV("Node Mappings", nodeMappingList), BorderLayout.CENTER);
        content.add(new JButton(removeAction), BorderLayout.SOUTH);
    }

    private class NodeMappingSelection implements ListSelectionListener {

        @Override
        public void valueChanged(ListSelectionEvent event) {
            if (event.getValueIsAdjusting()) return;
            final NodeMappingEntry selected = (NodeMappingEntry) nodeMappingList.getSelectedValue();
            if (selected != null) {
                Exec.work(new Runnable() {
                    @Override
                    public void run() {
                        sipModel.getCreateModel().setNodeMapping(selected.getNodeMapping());
                    }
                });
            }
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
                }
            }
        }
    }
}
