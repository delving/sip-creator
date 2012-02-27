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
import eu.delving.sip.base.Utility;
import eu.delving.sip.model.MappingModel;
import eu.delving.sip.model.RecDefTreeNode;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * Render the record mapping as a list of NodeMapping instances
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class RecMappingFrame extends FrameBase {
    private NodeMappingListModel listModel = new NodeMappingListModel();
    private JList nodeMappingList = new JList(listModel);
    private RemoveNodeMappingAction removeAction = new RemoveNodeMappingAction();  

    public RecMappingFrame(JDesktopPane desktop, SipModel sipModel) {
        super(desktop, sipModel, "Node Mappings", false);
        sipModel.getMappingModel().addSetListener(new MappingModel.SetListener() {
            @Override
            public void recMappingSet(MappingModel mappingModel) {
                updateList();
            }
        });
        sipModel.getMappingModel().addChangeListener(new MappingModel.ChangeListener() {
            @Override
            public void factChanged(MappingModel mappingModel, String name) {
            }

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
        nodeMappingList.setCellRenderer(new CellRenderer());
        nodeMappingList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        nodeMappingList.getSelectionModel().addListSelectionListener(new NodeMappingSelection());
    }

    private void updateList() {
        Exec.swing(new ListUpdater());
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createListPanel(), BorderLayout.CENTER);
        content.add(new JButton(removeAction), BorderLayout.SOUTH);
    }

    private JPanel createListPanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.add(Utility.scroll(nodeMappingList), BorderLayout.CENTER);
        return p;
    }

    private class NodeMappingSelection implements ListSelectionListener {

        @Override
        public void valueChanged(ListSelectionEvent event) {
            if (event.getValueIsAdjusting()) return;
            final NodeMapping selected = (NodeMapping)nodeMappingList.getSelectedValue();
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
            if (sipModel.getMappingModel().hasRecMapping()) recDefTree = sipModel.getMappingModel().getRecMapping().getRecDefTree();
        }

        @Override
        public void run() {
            listModel.setList(recDefTree);
            NodeMapping nm = sipModel.getCreateModel().getNodeMapping();
            if (nm != null) {
                int index = listModel.list.indexOf(nm);
                if (index >= 0) nodeMappingList.setSelectedIndex(index);
            }
        }
    }

    private class NodeMappingListModel extends AbstractListModel {
        private List<NodeMapping> list;
        
        public void setList(RecDefTree recDefTree) {
            if (list != null) {
                int size = getSize();
                list = null;
                if (size > 0) fireIntervalRemoved(this, 0, size - 1);
            }
            if (recDefTree != null) list = recDefTree.getNodeMappings();
            fireIntervalAdded(this, 0, getSize());
        }

        @Override
        public int getSize() {
            return list == null ? 0 : list.size();
        }

        @Override
        public Object getElementAt(int i) {
            return list.get(i);
        }
    }
    
    private class CellRenderer extends DefaultListCellRenderer {
        public Component getListCellRendererComponent(JList jList, java.lang.Object o, int i, boolean b, boolean b1) {
            // todo: icons
            return super.getListCellRendererComponent(jList, o, i, b, b1);
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
                    ((RecDefTreeNode)treePath.getLastPathComponent()).getRecDefNode().removeNodeMapping(nodeMapping.inputPath);
                    sipModel.getCreateModel().setNodeMapping(null);
                    new ListUpdater().run();
                }
            }
        }
    } 
}
