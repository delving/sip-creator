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

package eu.delving.sip.model;

import eu.delving.sip.base.AnalysisTree;
import eu.delving.sip.base.SourceVariable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Given an annotation processor, provide food for the JList to show fields
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class VariableListModel extends AbstractListModel {
    private List<SourceVariable> variableList = new ArrayList<SourceVariable>();

    public void setVariableList(List<AnalysisTree.Node> variableList) {
        clear();
        for (AnalysisTree.Node node : variableList) {
            this.variableList.add(new SourceVariable(node));
        }
        Collections.sort(this.variableList);
        fireIntervalAdded(this, 0, getSize());
    }

    public void clear() {
        int size = getSize();
        if (size > 0) {
            this.variableList.clear();
            fireIntervalRemoved(this, 0, size);
        }
    }

    @Override
    public int getSize() {
        return variableList.size();
    }

    @Override
    public Object getElementAt(int index) {
        return variableList.get(index);
    }
}