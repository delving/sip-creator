/*
 * Copyright 2011, 2012 Delving BV
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
import eu.delving.metadata.Path;
import eu.delving.sip.base.SwingHelper;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import java.awt.Component;

/**
 * An entry in the NodeMappingListModel, with its associated cell renderer.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class NodeMappingEntry {
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
        listModel.fireContentsChanged(index);
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

    public static class CellRenderer extends DefaultListCellRenderer {
        private static final int MAX_LENGTH = 30;
        private boolean sourceTargetOrdering = true;

        public void setSourceTargetOrdering(boolean sourceTargetOrdering) {
            this.sourceTargetOrdering = sourceTargetOrdering;
        }

        public Component getListCellRendererComponent(JList list, Object value, int index, boolean selected, boolean cellHasFocus) {
            NodeMappingEntry entry = (NodeMappingEntry) value;
            String string = getHtml(entry.nodeMapping);
            JLabel label = (JLabel) super.getListCellRendererComponent(list, string, index, selected, cellHasFocus);
            if (entry.isHighlighted()) {
                setBackground(SwingHelper.HILIGHTED_COLOR);
            }
            else {
                setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            }
            return label;
        }

        public String getHtml(NodeMapping nodeMapping) {
            if (nodeMapping.recDefNode == null) return "No RecDefNode";
            StringBuilder html = new StringBuilder("<html><font size=+1>");
            html.append(nodeMapping.groovyCode == null ? "<p>" : "<b>");
            String input = nodeMapping.createInputString();
            String targetTail = nodeMapping.recDefNode.getPath().getTail();
            if (input.equals(targetTail)) {
                html.append(input);
            }
            else if (sourceTargetOrdering) {
                html.append(String.format("%s &rarr; %s", input, targetTail));
            }
            else {
                html.append(String.format("%s &larr; %s", targetTail, input));
            }
            html.append(nodeMapping.groovyCode == null ? "</p>" : "</b>");
            html.append("</font>");
            html.append("<hr>");
            html.append("<table border=0 cellpadding=0>");
            html.append("<tr><td width=30></td><td><i>");
            for (Path path : nodeMapping.getInputPaths()) html.append(path).append("<br>");
            html.append("&rarr; ").append(nodeMapping.outputPath);
            if (nodeMapping.groovyCode != null) html.append("</b>");
            html.append("</i></td><tr>");
            html.append("</table>");
            return html.toString();
        }

    }
}
