/*
 * Copyright 2011 DELVING BV
 *
 * Licensed under the EUPL, Version 1.0 or? as soon they
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

package eu.delving.sip.panels;

import eu.delving.metadata.NodeMapping;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.Utility;
import org.antlr.stringtemplate.StringTemplate;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Shows a list of node mappings
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class NodeMappingPanel extends JPanel {
    private NodeMappingListModel listModel = new NodeMappingListModel();
    private JList nodeMappingList;

    public NodeMappingPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Node Mappings"));
        nodeMappingList = new JList(listModel) {
            @Override
            public String getToolTipText(MouseEvent evt) {
                int index = nodeMappingList.locationToIndex(evt.getPoint());
                NodeMapping nodeMapping = (NodeMapping) nodeMappingList.getModel().getElementAt(index);
                StringTemplate template = Utility.getTemplate("node-mapping");
                template.setAttribute("nodeMapping", nodeMapping);
                return template.toString();
            }
        };
        nodeMappingList.setCellRenderer(new CellRenderer());
        nodeMappingList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        nodeMappingList.setToolTipText("?");
        add(Utility.scrollV(nodeMappingList));
    }

    public JList getNodeMappingList() {
        return nodeMappingList;
    }

    public void setList(List<NodeMapping> list) {
        Exec.checkSwing();
        listModel.setList(list);
    }

    public void select(NodeMapping nodeMapping) {
        Exec.checkSwing();
        if (nodeMapping != null) {
            int index = listModel.list.indexOf(nodeMapping);
            if (index >= 0) nodeMappingList.setSelectedIndex(index);
        }
    }

    private class NodeMappingListModel extends AbstractListModel {
        private List<NodeMapping> list;

        public void setList(List<NodeMapping> freshList) {
            if (list != null) {
                int size = getSize();
                list = null;
                if (size > 0) fireIntervalRemoved(this, 0, size - 1);
            }
            if (freshList != null) list = freshList;
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
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            NodeMapping nodeMapping = (NodeMapping) value;
            if (nodeMapping.recDefNode.isAttr()) {
                setIcon(Utility.ATTRIBUTE_ICON);
            }
            else if (nodeMapping.recDefNode.getChildren().isEmpty()) {
                setIcon(Utility.VALUE_ELEMENT_ICON);
            }
            else {
                setIcon(Utility.COMPOSITE_ELEMENT_ICON);
            }
            return label;
        }
    }




}
