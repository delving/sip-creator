/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.sip.model;

import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.Path;
import eu.delving.sip.base.SwingHelper;

import javax.swing.*;
import java.awt.*;

/**
 * An entry in the NodeMappingListModel, with its associated cell renderer.
 *
 *
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
        private static final int MAX_LENGTH = 60;
        private boolean sourceTargetOrdering = true;

        public void setSourceTargetOrdering(boolean sourceTargetOrdering) {
            this.sourceTargetOrdering = sourceTargetOrdering;
        }

        public Component getListCellRendererComponent(JList list, Object value, int index, boolean selected, boolean cellHasFocus) {
            NodeMappingEntry entry = (NodeMappingEntry) value;
            String string = getHtml(entry.nodeMapping);
            JLabel label = (JLabel) super.getListCellRendererComponent(list, string, index, selected, cellHasFocus);
            if (selected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else if (entry.getNodeMapping().inputPathMissing) {
                setBackground(Color.RED);
                setForeground(Color.BLACK);
            } else if (entry.isHighlighted()) {
                setBackground(SwingHelper.HIGHLIGHTED_COLOR);
                setForeground(Color.BLACK);
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
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
                html.append(String.format("%s &rarr; %s", stripPath(input), targetTail));
            }
            else {
                html.append(String.format("%s &larr; %s", targetTail, stripPath(input)));
            }
            html.append(nodeMapping.groovyCode == null ? "</p>" : "</b>");
            html.append("</font>");
            html.append("<table border=0 cellpadding=0>");
            html.append("<tr><td width=30></td><td><i>");
            for (Path path : nodeMapping.getInputPaths()) html.append(stripPath(path.toString())).append("<br>");
            html.append("&rarr; ").append(stripPath(nodeMapping.outputPath.toString()));
            if (nodeMapping.groovyCode != null) html.append("</b>");
            html.append("</i></td><tr>");
            html.append("</table>");
            return html.toString();
        }

        private String stripPath(String path) {
            if (path.length() > MAX_LENGTH) {
                path = path.substring(path.length() - MAX_LENGTH);
                int chop = path.indexOf('/');
                if (chop < 0) {
                    return path;
                }
                else {
                    return ".." + path.substring(chop);
                }
            }
            else {
                return path;
            }
        }

    }
}
