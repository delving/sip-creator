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
import eu.delving.metadata.RecDefNode;
import eu.delving.metadata.RecDefTree;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
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
        sipModel.getMappingModel().addListener(new MappingModel.Listener() {
            @Override
            public void recMappingSet(MappingModel mappingModel) {
                Exec.swing(new ListUpdater());
            }

            @Override
            public void factChanged(MappingModel mappingModel) {
            }

            @Override
            public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
                Exec.swing(new ListUpdater());
            }

            @Override
            public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
                Exec.swing(new ListUpdater());
            }
        });
        nodeMappingList.setCellRenderer(new CellRenderer());
        nodeMappingList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        nodeMappingList.getSelectionModel().addListSelectionListener(new NodeMappingSelection());
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createListPanel(), BorderLayout.CENTER);
        content.add(new JButton(removeAction), BorderLayout.SOUTH);
    }

    private JPanel createListPanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.add(scroll(nodeMappingList), BorderLayout.CENTER);
        return p;
    }

    private class NodeMappingSelection implements ListSelectionListener {

        @Override
        public void valueChanged(ListSelectionEvent event) {
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
        private final RecDefTree recDefTree = sipModel.getMappingModel().getRecMapping().getRecDefTree();

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
                fireIntervalRemoved(this, 0, size - 1);
            }
            list = recDefTree.getNodeMappings();
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
