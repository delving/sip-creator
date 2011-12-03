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

import eu.delving.metadata.FieldMapping;
import eu.delving.metadata.MappingModel;
import eu.delving.metadata.RecordMapping;
import eu.delving.sip.base.Exec;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A ListModel of FieldMapping instances
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class FieldMappingListModel extends AbstractListModel implements MappingModel.Listener {
    private List<FieldMapping> list = new ArrayList<FieldMapping>();

    @Override
    public int getSize() {
        return list.size();
    }

    @Override
    public Object getElementAt(int index) {
        // Dirty fix, added to prevent IndexOutOfBounds exceptions, take a deeper look into this.
        if (index > list.size()) {
            return list.get(0);
        }
        return list.get(index);
    }

    private void clear() {
        final int size = getSize();
        if (size > 0) {
            this.list.clear();
            Exec.swing(
                    new Runnable() {
                        @Override
                        public void run() {
                            fireIntervalRemoved(this, 0, size);
                        }
                    }
            );
        }
    }

    @Override
    public void factChanged() {
    }

    @Override
    public void select(FieldMapping fieldMapping) {
    }

    @Override
    public void fieldMappingChanged() {
    }

    @Override
    public void recordMappingChanged(RecordMapping recordMapping) {
        clear();
        if (recordMapping != null) {
            for (FieldMapping fieldMapping : recordMapping.getFieldMappings()) {
                list.add(fieldMapping);
            }
            Exec.swing(
                    new Runnable() {
                        @Override
                        public void run() {
                            fireIntervalAdded(this, 0, getSize());
                        }
                    }
            );
        }
    }

    @Override
    public void recordMappingSelected(RecordMapping recordMapping) {
        recordMappingChanged(recordMapping);
    }

    public static class CellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            FieldMapping fieldMapping = (FieldMapping) value;
            return super.getListCellRendererComponent(list, fieldMapping.getDescription(), index, isSelected, cellHasFocus);
        }
    }


}