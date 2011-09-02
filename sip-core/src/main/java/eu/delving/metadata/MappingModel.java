/*
 * Copyright 2010 DELVING BV
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

package eu.delving.metadata;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This class holds a record mapping model, handles loading and saving, and
 * makes it observable.
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class MappingModel {

    private RecordMapping recordMapping;
    private FieldMapping selectedFieldMapping;

    public void setRecordMapping(RecordMapping recordMapping) {
        this.recordMapping = recordMapping;
        fireMappingChanged();
    }

    public RecordMapping getRecordMapping() {
        return recordMapping;
    }

    public FieldMapping selectFieldMapping(FieldMapping fieldMapping) {
        this.selectedFieldMapping = fieldMapping;
        for (Listener listener : listeners) {
            listener.select(selectedFieldMapping);
        }
        return selectedFieldMapping;
    }

    public FieldMapping getSelectedFieldMapping() {
        return selectedFieldMapping;
    }

    public void setFact(String path, String value) {
        if (recordMapping != null) {
            boolean changed;
            if (value == null) {
                changed = recordMapping.facts.containsKey(path);
                recordMapping.facts.remove(path);
            }
            else {
                changed = recordMapping.facts.containsKey(path) && !recordMapping.facts.get(path).equals(value);
                recordMapping.facts.put(path, value);
            }
            if (changed) {
                for (Listener listener : listeners) {
                    listener.factChanged();
                }
            }
        }
    }

    public void addMapping(FieldMapping fieldMapping) {
        if (recordMapping == null) return;
        String path = fieldMapping.getDefinition().path.toString();
        recordMapping.fieldMappings.put(path, fieldMapping);
        fireMappingChanged();
    }

    public void removeMapping(FieldMapping fieldMapping) {
        if (recordMapping == null) return;
        String path = fieldMapping.getDefinition().path.toString();
        recordMapping.fieldMappings.remove(path);
        fireMappingChanged();
    }

    public void applyTemplate(RecordMapping template) {
        if (recordMapping.fieldMappings.isEmpty()) {
            recordMapping.applyTemplate(template);
            fireMappingChanged();
        }
    }

    public void notifySelectedFieldMappingChange() {
        for (Listener listener : listeners) {
            listener.fieldMappingChanged();
        }
    }

    // observable

    public interface Listener {
        void factChanged();
        void select(FieldMapping fieldMapping);
        void fieldMappingChanged();
        void recordMappingChanged(RecordMapping recordMapping);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    private List<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    private void fireMappingChanged() {
        selectedFieldMapping = null;
        for (Listener listener : listeners) {
            listener.recordMappingChanged(recordMapping);
            listener.select(null);
        }
    }

}
