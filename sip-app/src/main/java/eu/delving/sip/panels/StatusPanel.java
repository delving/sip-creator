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

package eu.delving.sip.panels;

import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.MappingModel;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * The bottom of the main screen, below the desktop pane
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class StatusPanel extends JPanel {

    private static final Border DEFAULT_BORDER = BorderFactory.createTitledBorder("Actions");
    private List<StateAction> actions = new ArrayList<StateAction>();

    public StatusPanel(final SipModel sipModel) {
        super(new GridLayout(1, 0, 5, 5));
        sipModel.getMappingModel().addSetListener(new MappingModel.SetListener() {
            @Override
            public void recMappingSet(MappingModel mappingModel) {
                if (sipModel.getDataSetModel().hasDataSet() && mappingModel.hasRecMapping()) {
                    setBorder(BorderFactory.createTitledBorder(String.format(
                            "Actions - [%s -> %s]",
                            sipModel.getDataSetModel().getDataSet().getSpec(),
                            mappingModel.getRecMapping().getPrefix().toUpperCase()
                    )));
                }
                else {
                    setBorder(DEFAULT_BORDER);
                    for (StateAction action : actions) if (action.isEnabled()) action.setEnabled(false);
                }
            }
        });
        for (DataSetState state : DataSetState.values()) {
            StateAction action = new StateAction(state);
            actions.add(action);
            if (state == DataSetState.SOURCED) sipModel.getDataSetModel().addListener(new SourcedListener(action));
        }
        for (StateAction action : actions) {
            JButton button = new JButton(action);
            button.setToolTipText(action.state.toHtml());
            add(button);
        }
        setBorder(DEFAULT_BORDER);
    }

    public void setState(DataSetState state) {
        for (StateAction action : actions) action.enableIf(state);
    }

    public void setReaction(DataSetState state, Runnable work) {
        for (StateAction stateAction : actions) if (stateAction.state == state) stateAction.setWork(work);
    }

    public void setReaction(DataSetState state, Action action) {
        for (StateAction stateAction : actions) if (stateAction.state == state) stateAction.setAction(action);
    }

    private class StateAction extends AbstractAction {
        private DataSetState state;
        private Runnable work;
        private Action action;

        private StateAction(DataSetState state) {
            super(state.toTitle());
            this.state = state;
            setEnabled(false);
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
    
    private class SourcedListener implements DataSetModel.Listener {
        private StateAction sourcedAction;

        private SourcedListener(StateAction action) {
            this.sourcedAction = action;
        }

        @Override
        public void dataSetChanged(DataSet dataSet) {
            dataSetStateChanged(dataSet, dataSet.getState());
        }

        @Override
        public void dataSetRemoved() {
        }

        @Override
        public void dataSetStateChanged(DataSet dataSet, DataSetState dataSetState) {
            if (dataSetState == DataSetState.SOURCED) sourcedAction.actionPerformed(null);
        }
    }
}
