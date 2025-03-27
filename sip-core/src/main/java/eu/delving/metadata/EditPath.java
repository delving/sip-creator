/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.metadata;

/**
 * Combines a path in the RecDefTree with the edited code for that path so
 * that editing of code can be done for only one part of the tree at a time.
 *
 */
public class EditPath {
    private NodeMapping nodeMapping;
    private String editedCode;

    public EditPath(NodeMapping nodeMapping, String editedCode) {
        this.nodeMapping = nodeMapping;
        this.editedCode = editedCode;
    }

    public NodeMapping getNodeMapping() {
        return nodeMapping;
    }

    public boolean isGeneratedCode() {
        return editedCode == null;
    }

    public String getEditedCode(Path path) {
        return nodeMapping.recDefNode.getPath().equals(path) ? editedCode : null;
    }
}
