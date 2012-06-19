/*
 * Copyright 2011, 2012 Delving BV
 *
 * Licensed under the EUPL, Version 1.0 or? as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the
 * Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.delving.sip.menus;

import eu.delving.metadata.OptList;
import eu.delving.sip.base.Exec;
import eu.delving.sip.base.Swing;
import eu.delving.sip.model.MappingModel;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * The menu for choosing which of the hidden record definition opts to show.
 *
 * @author Gerald de Jong, Delving BV, <gerald@delving.eu>
 */

public class ShowOptionMenu extends JMenu implements MappingModel.SetListener {
    private Listener listener;

    public interface Listener {
        void optSelected(OptList.Opt opt);
    }

    public ShowOptionMenu(Listener listener) {
        super("Options");
        this.listener = listener;
    }

    @Override
    public void recMappingSet(final MappingModel mappingModel) {
        Exec.run(new Swing() {
            @Override
            public void run() {
                removeAll();
                if (mappingModel.hasRecMapping()) {
                    setEnabled(true);
                    List<OptList> optLists = mappingModel.getRecMapping().getRecDefTree().getRecDef().opts;
                    if (optLists != null) {
                        for (OptList list : optLists) {
                            if (list.dictionary != null) continue;
                            JMenu listMenu = new JMenu(list.displayName);
                            for (OptList.Opt opt : list.opts) listMenu.add(new OptAction(opt));
                            add(listMenu);
                        }
                    }
                }
                else {
                    setEnabled(false);
                }
            }
        });
    }

    private class OptAction extends AbstractAction {
        private OptList.Opt opt;

        private OptAction(OptList.Opt opt) {
            super(opt.value);
            this.opt = opt;
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            listener.optSelected(opt);
        }
    }
}
