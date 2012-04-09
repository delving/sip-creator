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

package eu.delving.sip.model;

import eu.delving.metadata.NodeMapping;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.Utility;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import java.awt.Component;

import static eu.delving.sip.base.Utility.HILIGHTED_COLOR;

/**
 * An entry in the NodeMappingListModel
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */
public class NodeMappingEntry {
    private int index;
    private NodeMapping nodeMapping;
    private boolean highlighted;
    private NodeMappingListModel listModel;

    public NodeMappingEntry(NodeMappingListModel listModel, int index, NodeMapping nodeMapping) {
        this.listModel = listModel;
        this.index = index;
        this.nodeMapping = nodeMapping;
    }

    public NodeMapping getNodeMapping() {
        return nodeMapping;
    }

    public boolean isHighlighted() {
        return highlighted;
    }

    public void fireChanged() {
        Exec.swing(new Runnable() {
            @Override
            public void run() {
                listModel.fireChanged(index);
            }
        });
    }

    public String toString() {
        return nodeMapping.toString();
    }

    public void clearHighlighted() {
        if (highlighted) {
            highlighted = false;
            fireChanged();
        }
    }

    public void setHighlighted() {
        if (!highlighted) {
            highlighted = true;
            fireChanged();
        }
    }

    public static class CellRenderer extends DefaultListCellRenderer {
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean selected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, cellHasFocus);
            NodeMappingEntry entry = (NodeMappingEntry) value;
            if (entry.getNodeMapping().recDefNode.isAttr()) {
                setIcon(Utility.ATTRIBUTE_ICON);
            }
            else if (entry.getNodeMapping().recDefNode.isLeafElem()) {
                setIcon(Utility.VALUE_ELEMENT_ICON);
            }
            else {
                setIcon(Utility.COMPOSITE_ELEMENT_ICON);
            }
            if (entry.isHighlighted() && !selected) {
                setOpaque(true);
                setBackground(HILIGHTED_COLOR);
            }
            return label;
        }

    }
}
