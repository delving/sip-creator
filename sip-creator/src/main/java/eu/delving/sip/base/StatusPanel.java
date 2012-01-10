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

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * The bottom of the main screen, below the desktop pane
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class StatusPanel extends JPanel {

    private StateAction[] actions = new StateAction[DataSetState.values().length];

    public StatusPanel() {
        super(new GridLayout(1, 0, 5, 5));
        setBorder(BorderFactory.createTitledBorder("Actions"));
        for (int walk = 0; walk < actions.length; walk++) actions[walk] = new StateAction(DataSetState.values()[walk]);
        for (StateAction action : actions) {
            JButton button = new JButton(action);
            button.setToolTipText(action.state.toHtml());
            add(button);
        }
    }

    public void setState(DataSetState state) {
        for (StateAction action : actions) action.enableIf(state);
    }

    public void setReaction(DataSetState state, Runnable work) {
        actions[state.ordinal()].setWork(work);
    }

    public void setReaction(DataSetState state, Action action) {
        actions[state.ordinal()].setAction(action);
    }

    private class StateAction extends AbstractAction {
        private DataSetState state;
        private Runnable work;
        private Action action;

        private StateAction(DataSetState state) {
            super(state.toTitle());
            this.state = state;
        }

        public void setWork(Runnable work) {
            this.work = work;
        }

        public void setAction(Action action) {
            this.action = action;
        }

        public void enableIf(DataSetState state) {
            if (this.state == state) {
                setEnabled(true);
            }
            else if (isEnabled()) {
                setEnabled(false);
            }
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (work != null) work.run();
            if (action != null) action.actionPerformed(null);
        }
    }
}
