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

package eu.delving.groovy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Something to hold the groovy node and turn it into a string
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
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

    public List<MetadataVariable> getVariables() {
        List<MetadataVariable> variables = new ArrayList<MetadataVariable>();
        getVariables(rootNode, variables);
        return variables;
    }

    private boolean checkFor(GroovyNode groovyNode, Pattern pattern) {
        if (groovyNode.value() instanceof List) {
            List list = (List) groovyNode.value();
            for (Object member : list) {
                GroovyNode childNode = (GroovyNode) member;
                if (checkFor(childNode, pattern)) {
                    return true;
                }
            }
            return false;
        }
        else {
            return pattern.matcher(groovyNode.text()).matches();
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

    public String toHtml() {
        StringBuilder out = new StringBuilder(String.format("<html><strong>Record Number %d</strong><dl>", recordNumber));
        for (MetadataVariable variable : getVariables()) {
            out.append(String.format("<dt>%s</dt><dd><strong>%s</strong></dd>", variable.getName(), variable.getValue()));
        }
        out.append("</dl><html>");
        return out.toString();
    }

    public String toString() {
        StringBuilder out = new StringBuilder();
        out.append("Record #").append(recordNumber).append('\n');
        for (MetadataVariable variable : getVariables()) {
            out.append(variable.toString()).append('\n');
        }
        return out.toString();
    }
}
