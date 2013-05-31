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

import eu.delving.metadata.MappingFunction;
import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.NodeMappingChange;
import eu.delving.metadata.RecDefNode;
import eu.delving.sip.base.Swing;

import javax.swing.AbstractListModel;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A list of node mapping entries set up to change when the mapping changes, ready for placement in a JList.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class NodeMappingListModel extends AbstractListModel {
    private List<NodeMappingEntry> entries = new ArrayList<NodeMappingEntry>();
    private EntrySorting entrySorting = new EntrySorting(true);

    public JList createJList() {
        JList list = new JList(this);
        list.setCellRenderer(new NodeMappingEntry.CellRenderer());
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
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
        return entries.get(indexOf(nodeMapping));
    }

    public void clearHighlighted() {
        for (NodeMappingEntry entry : entries) entry.clearHighlighted();
    }

    public void fireContentsChanged(int index) {
        fireContentsChanged(this, index, index);
    }

    public MappingModel.ChangeListener createMappingChangeEar(final SipModel sipModel) {
        return new MappingModel.ChangeListener() {
            @Override
            public void lockChanged(MappingModel mappingModel, boolean locked) {
            }

            @Override
            public void functionChanged(MappingModel mappingModel, MappingFunction function) {
            }

            @Override
            public void nodeMappingChanged(MappingModel mappingModel, RecDefNode node, final NodeMapping nodeMapping, NodeMappingChange change) {
                sipModel.exec(new Swing() {
                    @Override
                    public void run() {
                        fireContentsChanged(indexOf(nodeMapping));
                    }
                });
            }

            @Override
            public void nodeMappingAdded(MappingModel mappingModel, RecDefNode node, final NodeMapping nodeMapping) {
                sipModel.exec(new Swing() {
                    @Override
                    public void run() {
                        entries.add(new NodeMappingEntry(NodeMappingListModel.this, nodeMapping));
                        int index = sortEntries();
                        fireIntervalAdded(this, index, index);
                    }
                });
            }

            @Override
            public void nodeMappingRemoved(MappingModel mappingModel, RecDefNode node, final NodeMapping nodeMapping) {
                sipModel.exec(new Swing() {
                    @Override
                    public void run() {
                        int index = indexOf(nodeMapping);
                        entries.remove(index);
                        fireIntervalRemoved(this, index, index);
                    }
                });
            }

            @Override
            public void populationChanged(MappingModel mappingModel, RecDefNode node) {
            }
        };
    }

    public MappingModel.SetListener createSetEar(final SipModel sipModel) {
        return new MappingModel.SetListener() {
            @Override
            public void recMappingSet(final MappingModel mappingModel) {
                sipModel.exec(new Swing() {
                    @Override
                    public void run() {
                        setList(mappingModel.hasRecMapping() ? mappingModel.getRecMapping().getNodeMappings() : null);
                    }
                });
            }
        };
    }

    public void setSorting(boolean sourceTargetSorting) {
        entrySorting = new EntrySorting(sourceTargetSorting);
        sortEntries();
        fireContentsChanged(this, 0, getSize());
    }

    public void setList(List<NodeMapping> freshList) {
        if (!entries.isEmpty()) {
            final int size = getSize();
            entries.clear();
            if (size > 0) fireIntervalRemoved(this, 0, size - 1);
        }
        if (freshList != null) {
            for (NodeMapping nodeMapping : freshList) entries.add(new NodeMappingEntry(this, nodeMapping));
            sortEntries();
        }
        final int size = getSize();
        if (size > 0) fireIntervalAdded(this, 0, size);
    }

    public int indexOf(NodeMapping nodeMapping) {
        int index = 0;
        for (NodeMappingEntry entry : entries) {
            if (entry.getNodeMapping() == nodeMapping) return index;
            index++;
        }
        throw new RuntimeException("Node mapping not found: " + nodeMapping);
    }

    private int sortEntries() {
        int inserted = -1;
        Collections.sort(entries, entrySorting);
        int index = 0;
        for (NodeMappingEntry entry : entries) {
            if (entry.getIndex() < 0) inserted = index;
            index++;
        }
        index = 0;
        for (NodeMappingEntry nodeMappingEntry : entries) nodeMappingEntry.setIndex(index++);
        return inserted;
    }

    private class EntrySorting implements Comparator<NodeMappingEntry> {
        private boolean sourceTargetOrder;

        private EntrySorting(boolean sourceTargetOrder) {
            this.sourceTargetOrder = sourceTargetOrder;
        }

        @Override
        public int compare(NodeMappingEntry entry1, NodeMappingEntry entry2) {
            NodeMapping nodeMapping1 = entry1.getNodeMapping();
            NodeMapping nodeMapping2 = entry2.getNodeMapping();
            int compare = nodeMapping1.toSortString(sourceTargetOrder)
                    .compareTo(nodeMapping2.toSortString(sourceTargetOrder));
            if (compare != 0) return compare;
            String string1 = nodeMapping1.recDefNode.getPath().toString();
            String string2 = nodeMapping2.recDefNode.getPath().toString();
            return string1.compareTo(string2);
        }
    }
}
