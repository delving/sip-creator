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

import eu.delving.groovy.GroovyVariable;
import eu.delving.metadata.FieldStatistics;

import java.util.Set;
import java.util.TreeSet;

/**
 * Hold a variable for later use
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class SourceVariable implements Comparable<SourceVariable> {
    private StatsTreeNode node;
    private String variableName;
    private Set<String> attributeNames = new TreeSet<String>();

    public SourceVariable(StatsTreeNode node) {
        this.node = node;
        this.variableName = GroovyVariable.name(node.getPath()); // todo: will need to use ancestors too
        for (StatsTreeNode child : node.getChildNodes()) {
            if (child.getTag().isAttribute()) {
                attributeNames.add(child.getTag().toString());
            }
        }
    }

    public Set<String> getAttributeNames() {
        return attributeNames;
    }

    public StatsTreeNode getNode() {
        return node;
    }

    public String getVariableName() {
        return variableName;
    }

    public String toString() {
        StringBuilder out = new StringBuilder(variableName);
        if (!attributeNames.isEmpty()) out.append(attributeNames);
        return out.toString();
    }

    @Override
    public int compareTo(SourceVariable o) {
        return node.compareTo(o.node);
    }

    public boolean hasStatistics() {
        return node != null && node.getStatistics() != null;
    }

    public FieldStatistics getStatistics() {
        return node.getStatistics();
    }
}
