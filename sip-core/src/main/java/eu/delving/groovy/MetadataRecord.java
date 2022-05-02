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
    private int recordNumber;

    public static MetadataRecord create(GroovyNode rootNode, int recordNumber) {
        return new MetadataRecord(rootNode, recordNumber);
    }

    private MetadataRecord(GroovyNode rootNode, int recordNumber) {
        this.rootNode = rootNode;
        this.recordNumber = recordNumber;
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

    private boolean checkFor(GroovyNode groovyNode, Pattern pattern) {
        if (groovyNode.text != null && pattern.matcher(groovyNode.text).find()) {
            return true;
        }
        for (GroovyNode child : groovyNode.children) {
            if (checkFor(child, pattern)) {
                return true;
            }
        }
        return false;
    }

    public String toString() {
        return String.format("MetadataRecord(%d / ?)", recordNumber);
    }

}
