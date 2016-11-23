/*
 * Copyright 2011, 2012 Delving BV
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

import java.util.List;
import java.util.regex.Pattern;

/**
 * The MetadataParser delivers instances of this class for each record that it
 * consumes.  The XML content is recorded in the composite tree of GroovyNode and
 * GroovyList instances, and we also hold the record number and the total number
 * of records.
 *
 *
 */

public class MetadataRecord {
    private GroovyNode rootNode;
    private int recordNumber, recordCount;

    public static MetadataRecord create(GroovyNode rootNode, int recordNumber, int recordCount) {
        return new MetadataRecord(rootNode, recordNumber, recordCount);
    }

    public static MetadataRecord poisonPill() {
        return new MetadataRecord(null, -1, -1);
    }

    private MetadataRecord(GroovyNode rootNode, int recordNumber, int recordCount) {
        this.rootNode = rootNode;
        this.recordNumber = recordNumber;
        this.recordCount = recordCount;
    }

    public boolean isPoison() {
        return rootNode == null;
    }

    public GroovyNode getRootNode() {
        return rootNode;
    }

    public String getId() {
        return rootNode.attributes().get("id");
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

    private boolean checkFor(GroovyNode groovyNode, Pattern pattern) {
        if (groovyNode.getNodeValue() instanceof List) {
            List list = (List) groovyNode.getNodeValue();
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

    public String toString() {
        return String.format("MetadataRecord(%d / %d)", recordNumber, recordCount);
    }

}
