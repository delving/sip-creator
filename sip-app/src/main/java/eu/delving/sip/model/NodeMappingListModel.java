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

import eu.delving.metadata.MappingFunction;
import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.RecDefNode;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.Utility;
import org.antlr.stringtemplate.StringTemplate;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A list of node mappings
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class NodeMappingListModel extends AbstractListModel {
    private List<NodeMappingEntry> entries = new ArrayList<NodeMappingEntry>();

    public JList createJList() {
        JList list = new JList(this) {
            @Override
            public String getToolTipText(MouseEvent evt) {
                int index = locationToIndex(evt.getPoint());
                if (index < 0) return "?";
                NodeMappingEntry entry = (NodeMappingEntry) getModel().getElementAt(index);
                StringTemplate template = Utility.getTemplate("node-mapping");
                template.setAttribute("nodeMapping", entry.getNodeMapping());
                return template.toString();
            }
        };
        list.setCellRenderer(new NodeMappingEntry.CellRenderer());
        return list;
    }

    @Override
    public int getSize() {
        return entries.size();
    }

    @Override
    public Object getElementAt(int i) {
        return entries.get(i);
    }

    public NodeMappingEntry getEntry(NodeMapping nodeMapping) {
        int index = indexOf(nodeMapping);
        if (index < 0) {
            throw new RuntimeException("Node mapping not found");
        }
        return entries.get(index);
    }

    public void clearHighlighted() {
        for (NodeMappingEntry entry : entries) entry.clearHighlighted();
    }

    private int sortEntries() {
        int inserted = -1;
        Collections.sort(entries);
        int index = 0;
        for (NodeMappingEntry entry : entries) {
            if (entry.getIndex() < 0) inserted = index;
            index++;
        }
        index = 0;
        for (NodeMappingEntry nodeMappingEntry : entries) nodeMappingEntry.setIndex(index++);
        return inserted;
    }

    public MappingModel.ChangeListener createMappingChangeEar() {
        return new MappingModel.ChangeListener() {
            @Override
            public void functionChanged(MappingModel mappingModel, MappingFunction function) {
            }

            @Override
            public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
                final int index = indexOf(nodeMapping);
                if (index < 0) throw new RuntimeException("Node mapping not found: "+nodeMapping);
                Exec.swing(new Runnable() {
                    @Override
                    public void run() {
                        fireChanged(index);
                    }
                });
            }

            @Override
            public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, final NodeMapping nodeMapping) {
                Exec.swing(new Runnable() {
                    @Override
                    public void run() {
                        entries.add(new NodeMappingEntry(NodeMappingListModel.this, nodeMapping));
                        int index = sortEntries();
                        fireIntervalAdded(this, index, index);
                    }
                });
            }

            @Override
            public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, NodeMapping nodeMapping) {
                final int index = indexOf(nodeMapping);
                if (index < 0) throw new RuntimeException("Node mapping not found: "+nodeMapping);
                entries.remove(index);
                fireIntervalRemoved(this, index, index);
            }
        };
    }

    public MappingModel.SetListener createSetEar() {
        return new MappingModel.SetListener() {
            @Override
            public void recMappingSet(MappingModel mappingModel) {
                setList(mappingModel.hasRecMapping() ? mappingModel.getRecMapping().getNodeMappings() : null);
            }
        };
    }

    public void setList(List<NodeMapping> freshList) {
        if (!entries.isEmpty()) {
            final int size = getSize();
            entries.clear();
            if (size > 0) Exec.swing(new Runnable() {
                @Override
                public void run() {
                    fireIntervalRemoved(this, 0, size - 1);
                }
            });
        }
        if (freshList != null) {
            for (NodeMapping nodeMapping : freshList) entries.add(new NodeMappingEntry(this, nodeMapping));
            sortEntries();
        }
        final int size = getSize();
        if (size > 0) Exec.swing(new Runnable() {
            @Override
            public void run() {
                fireIntervalAdded(this, 0, size);
            }
        });
    }

    public int indexOf(NodeMapping nodeMapping) {
        int index = 0;
        for (NodeMappingEntry entry : entries) {
            if (entry.getNodeMapping() == nodeMapping) return index;
            index++;
        }
        return -1;
    }

    public void fireChanged(int index) {
        fireContentsChanged(this, index, index);
    }
}
