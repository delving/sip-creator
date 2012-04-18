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

import javax.swing.*;
import java.awt.*;

/**
 * An entry in the NodeMappingListModel
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */
public class NodeMappingEntry implements Comparable<NodeMappingEntry> {
    private int index = -1;
    private NodeMapping nodeMapping;
    private boolean highlighted;
    private NodeMappingListModel listModel;

    public NodeMappingEntry(NodeMappingListModel listModel, NodeMapping nodeMapping) {
        this.listModel = listModel;
        this.nodeMapping = nodeMapping;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
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

    public String toString() {
        return nodeMapping.toString();
    }

    @Override
    public int compareTo(NodeMappingEntry nodeMappingEntry) {
        int compare = nodeMapping.inputPath.getTail().compareTo(nodeMappingEntry.getNodeMapping().inputPath.getTail());
        if (compare != 0) return compare;
        compare = nodeMapping.inputPath.compareTo(nodeMappingEntry.getNodeMapping().inputPath);
        if (compare != 0) return compare;
        return nodeMapping.outputPath.compareTo(nodeMappingEntry.getNodeMapping().outputPath);
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
            if (entry.isHighlighted()) {
                setBackground(Utility.HILIGHTED_COLOR);
            }
            else {
                setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            }
            return label;
        }

    }
}
