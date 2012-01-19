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

package eu.delving.groovy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The MetadataParser delivers instances of this class for each record that it
 * consumes.  The XML content is recorded in the composite tree of GroovyNode and
 * GroovyList instances, and we also hold the record number and the total number
 * of records.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MetadataRecord {
    private GroovyNode rootNode;
    private int recordNumber, recordCount;

    MetadataRecord(GroovyNode rootNode, int recordNumber, int recordCount) {
        this.rootNode = rootNode;
        this.recordNumber = recordNumber;
        this.recordCount = recordCount;
    }

    public GroovyNode getRootNode() {
        return rootNode;
    }

    public boolean contains(Pattern pattern) {
        return checkFor(rootNode, pattern);
    }

    public int getRecordNumber() {
        return recordNumber;
    }

    public int getRecordCount() {
        return recordCount;
    }

    private List<MetadataVariable> getVariables() {
        List<MetadataVariable> variables = new ArrayList<MetadataVariable>();
        getVariables(rootNode, variables);
        return variables;
    }

    private boolean checkFor(GroovyNode groovyNode, Pattern pattern) {
        if (groovyNode.value() instanceof List) {
            List list = (List) groovyNode.value();
            for (Object member : list) {
                GroovyNode childNode = (GroovyNode) member;
                if (checkFor(childNode, pattern)) return true;
            }
            return false;
        }
        else {
            return pattern.matcher(groovyNode.text()).find();
        }
    }

    private void getVariables(GroovyNode groovyNode, List<MetadataVariable> variables) {
        if (groovyNode.value() instanceof List) {
            List list = (List) groovyNode.value();
            for (Object member : list) {
                GroovyNode childNode = (GroovyNode) member;
                getVariables(childNode, variables);
            }
        }
        else {
            List<GroovyNode> path = new ArrayList<GroovyNode>();
            GroovyNode walk = groovyNode;
            while (walk != null) {
                path.add(walk);
                walk = walk.parent();
            }
            Collections.reverse(path);
            StringBuilder out = new StringBuilder();
            Iterator<GroovyNode> nodeWalk = path.iterator();
            while (nodeWalk.hasNext()) {
                String nodeName = nodeWalk.next().name();
                out.append(nodeName);
                if (nodeWalk.hasNext()) {
                    out.append('.');
                }
            }
            String variableName = out.toString();
            variables.add(new MetadataVariable(variableName, (String) groovyNode.value()));
        }
    }

    public String toString() {
        StringBuilder out = new StringBuilder();
        out.append("Record #").append(recordNumber).append('\n');
        for (MetadataVariable variable : getVariables()) {
            out.append(variable.toString()).append('\n');
        }
        return out.toString();
    }

    private static class MetadataVariable {
        private String name;
        private String value;

        public MetadataVariable(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        public String toString() {
            return name + "= \"" + value+"\"";
        }
    }
}
