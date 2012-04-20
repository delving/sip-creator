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

package eu.delving.sip.base;

import eu.delving.metadata.NodeMapping;
import eu.delving.metadata.OptList;
import eu.delving.sip.model.SourceTreeNode;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

/**
 * Help with handling the dictionary contained in a NodeMapping
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class DictionaryHelper {

    public static boolean isDictionaryPossible(NodeMapping nodeMapping) {
        if (nodeMapping == null || nodeMapping.recDefNode == null || !nodeMapping.hasOneStatsTreeNode()) return false;
        SourceTreeNode sourceTreeNode = (SourceTreeNode) nodeMapping.getSingleStatsTreeNode();
        if (sourceTreeNode.getStatistics() == null) return false;
        Set<String> values = sourceTreeNode.getStatistics().getHistogramValues();
        OptList optList = nodeMapping.recDefNode.getOptList();
        return values != null && optList != null && nodeMapping.dictionary == null;
    }

    public static boolean refreshDictionary(NodeMapping nodeMapping) {
        if (!isDictionaryPossible(nodeMapping)) throw new RuntimeException("Should have checked");
        SourceTreeNode sourceTreeNode = (SourceTreeNode) nodeMapping.getSingleStatsTreeNode();
        if (sourceTreeNode.getStatistics() == null) return false;
        return setDictionaryDomain(nodeMapping, sourceTreeNode.getStatistics().getHistogramValues());
    }

    public static int countNonemptyDictionaryEntries(NodeMapping nodeMapping) {
        int nonemptyEntries = 0;
        if (nodeMapping != null && nodeMapping.dictionary != null) {
            for (String value : nodeMapping.dictionary.values()) {
                if (!value.trim().isEmpty()) nonemptyEntries++;
            }
        }
        return nonemptyEntries;
    }

    public static boolean setDictionaryDomain(NodeMapping nodeMapping, Collection<String> domainValues) {
        if (nodeMapping == null) return false;
        boolean changed = false;
        if (nodeMapping.dictionary == null) {
            nodeMapping.dictionary = new TreeMap<String, String>();
            changed = true;
        }
        for (String key : domainValues)
            if (!nodeMapping.dictionary.containsKey(key)) {
                nodeMapping.dictionary.put(key, "");
                changed = true;
            }
        Set<String> unused = new HashSet<String>(nodeMapping.dictionary.keySet());
        unused.removeAll(domainValues);
        for (String unusedKey : unused) {
            nodeMapping.dictionary.remove(unusedKey);
            changed = true;
        }
        nodeMapping.groovyCode = null;
        if (changed) nodeMapping.notifyChanged();
        return changed;
    }

    public static void removeDictionary(NodeMapping nodeMapping) {
        if (nodeMapping == null) return;
        nodeMapping.dictionary = null;
        nodeMapping.groovyCode = null;
        nodeMapping.notifyChanged();
    }
}
