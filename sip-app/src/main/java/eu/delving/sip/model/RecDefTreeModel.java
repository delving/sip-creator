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

package eu.delving.sip.model;

/**
 * Special version of FilterTreeModel which knows about special rec def things.
 *
 *
 */

public class RecDefTreeModel extends FilterTreeModel {
    private boolean attributesHidden;

    public RecDefTreeModel(FilterNode root) {
        super(root);
        root.setFilterModel(this);
    }
    public boolean isAttributesHidden() {
        return attributesHidden;
    }

    public void setAttributesHidden(boolean attributesHidden) {
        if (this.attributesHidden != attributesHidden) {
            this.attributesHidden = attributesHidden;
            refreshTree();
        }
    }
}
