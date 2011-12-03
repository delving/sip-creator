/*
 * Copyright 2011 DELVING BV
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

import eu.delving.metadata.RecMapping;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An observable map, with knowledge of RecordMapping
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class FactModel {
    private Map<String, String> facts = new TreeMap<String, String>();

    public String get(String key) {
        return facts.get(key);
    }

    public Map<String, String> getFacts() {
        return facts;
    }

    public void set(Map<String, String> newMap) {
        facts.clear();
        if (newMap != null) facts.putAll(newMap);
        fireAllUpdated();
    }

    public void set(String name, String value) {
        facts.put(name, value);
        fireFactUpdated(name);
    }

    public boolean copyToRecordMapping(RecMapping recMapping) {
        boolean changed = false;
        for (Map.Entry<String, String> entry : facts.entrySet()) {
            if (recMapping.setFact(entry.getKey(), entry.getValue())) changed = true;
        }
        return changed;
    }

    private List<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    public void fireFactUpdated(String name) {
        String value = facts.get(name);
        for (Listener listener : listeners) {
            listener.factUpdated(name, value);
        }
    }

    public void fireAllUpdated() {
        for (Listener listener : listeners) {
            listener.allFactsUpdated(facts);
        }
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public interface Listener {
        void factUpdated(String name, String value);

        void allFactsUpdated(Map<String, String> map);
    }
}
