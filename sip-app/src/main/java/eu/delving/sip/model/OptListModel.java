/*
 * Copyright 2010 DELVING BV
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

import eu.delving.metadata.Path;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.Tag;

import javax.swing.AbstractListModel;
import javax.swing.ComboBoxModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Show all of the opts in the recdef which are not hidden
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class OptListModel extends AbstractListModel implements ComboBoxModel {
    private Object selected;
    private List<OptChoice> list = new ArrayList<OptChoice>();

    public void setList(RecDef recDef) {
        int size = getSize();
        if (size > 0) {
            list.clear();
            fireIntervalRemoved(this, 0, size);
        }
        if (recDef.opts == null) return;
        list.add(new OptChoice());
        for (RecDef.OptList optList : recDef.opts) {
            for (RecDef.Opt opt : optList.opts) {
                list.add(new OptChoice(optList.path, opt));
            }
        }
        if (!list.isEmpty()) {
            selected = list.get(0);
            fireIntervalAdded(this, 0, getSize());
        }
    }

    @Override
    public int getSize() {
        return list.size();
    }

    @Override
    public Object getElementAt(int i) {
        return list.get(i);
    }

    @Override
    public void setSelectedItem(Object object) {
        this.selected = object;
    }

    @Override
    public Object getSelectedItem() {
        return selected;
    }

    public static class OptChoice {
        private Path path;
        private Tag tail;
        private RecDef.Opt opt;

        public OptChoice() {
        }

        public OptChoice(Path path, RecDef.Opt opt) {
            path = path.copy();
            this.tail = path.pop();
            this.path = path.extend(tail.withOpt(opt.key));
            this.opt = opt;
        }

        public Path getPath() {
            return path;
        }

        public RecDef.Opt getOpt() {
            return opt;
        }

        public String toString() {
            if (opt == null) return "<choose>";
            return String.format("%s[%s]", tail, opt.content);
        }
    }
}
