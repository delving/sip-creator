/*
 * Copyright 2011 DELVING BV
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

package eu.delving.sip.base;

import eu.delving.sip.files.DataSetState;
import eu.delving.sip.model.SipModel;

import javax.swing.AbstractAction;
import javax.swing.Action;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Actions corresponding to each of the data set states
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class DataSetStateActions {
    private SipModel sipModel;
    private List<StateAction> actions = new ArrayList<StateAction>();

    public DataSetStateActions(SipModel sipModel) {
        this.sipModel = sipModel;
        for (DataSetState state : DataSetState.values()) {
            actions.add(new StateAction(state));
        }
    }

    public List<? extends Action> getActions() {
        return actions;
    }

    private class StateAction extends AbstractAction {
        private DataSetState state;

        private StateAction(DataSetState state) {
            super(state.toString());
            this.state = state;
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            System.out.println("STATE ACTION: "+state);
        }
    }
}
