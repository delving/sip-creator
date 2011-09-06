/*
 * Copyright 2007 EDL FOUNDATION
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.sip.model;

import eu.delving.metadata.FieldDefinition;
import eu.delving.metadata.FieldMapping;
import eu.delving.metadata.MappingModel;
import eu.delving.metadata.RecordDefinition;
import eu.delving.metadata.RecordMapping;

import javax.swing.AbstractListModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.ListModel;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * todo
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class FieldListModel extends AbstractListModel {
    private DataSetStoreModel storeModel;
    private List<FieldDefinition> fieldDefinitions;
    private Unmapped unmapped;

    public FieldListModel(DataSetStoreModel storeModel) {
        this.storeModel = storeModel;
        this.fieldDefinitions = new ArrayList<FieldDefinition>();
    }

    public ListModel getUnmapped(MappingModel mappingModel) {
        if (unmapped == null) {
            mappingModel.addListener(unmapped = new Unmapped());
        }
        return unmapped;
    }

    @Override
    public int getSize() {
        return fieldDefinitions.size();
    }

    @Override
    public Object getElementAt(int index) {
        return fieldDefinitions.get(index);
    }

    public static class CellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            FieldDefinition fieldDefinition = (FieldDefinition) value;
            String string = fieldDefinition.getFieldNameString();
            return super.getListCellRendererComponent(list, string, index, isSelected, cellHasFocus);
        }
    }

    public class Unmapped extends AbstractListModel implements MappingModel.Listener {
        private List<FieldDefinition> unmappedFields = new ArrayList<FieldDefinition>();

        @Override
        public int getSize() {
            return unmappedFields.size();
        }

        @Override
        public Object getElementAt(int index) {
            return unmappedFields.get(index);
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
            int sizeBefore = getSize();
            unmappedFields.clear();
            fieldDefinitions.clear();
            fireIntervalRemoved(this, 0, sizeBefore);
            if (recordMapping != null) {
                RecordDefinition recordDefinition = storeModel.getRecordDefinition(recordMapping.getPrefix());
                fieldDefinitions.addAll(recordDefinition.getMappableFields());
                Collections.sort(fieldDefinitions, new Comparator<FieldDefinition>() {
                    @Override
                    public int compare(FieldDefinition field0, FieldDefinition field1) {
                        return field0.getFieldNameString().compareTo(field1.getFieldNameString());
                    }
                });
                nextVariable:
                for (FieldDefinition fieldDefinition : fieldDefinitions) {
                    for (FieldMapping fieldMapping : recordMapping.getFieldMappings()) {
                        if (fieldMapping.fieldDefinition.getTag().equals(fieldDefinition.getTag())) {
                            continue nextVariable;
                        }
                    }
                    unmappedFields.add(fieldDefinition);
                }
                fireIntervalAdded(this, 0, getSize());
            }
        }
    }
}
