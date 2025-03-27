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

package eu.delving.sip.panels;

import eu.delving.sip.base.Swing;
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

import static eu.delving.sip.base.KeystrokeHelper.MENU_G;
import static eu.delving.sip.files.DataSetState.SOURCED;
import static eu.delving.sip.files.DataSetState.values;

/**
 * The bottom of the main screen, below the desktop pane, intended to represent the workflow to users.
 *
 *
 */

public class StatusPanel extends JPanel {

    private static final Border DEFAULT_BORDER = BorderFactory.createTitledBorder("Actions");
    private ActionBridge actionBridge = new ActionBridge();
    private List<StateAction> actions = new ArrayList<StateAction>();
    private JButton button = new JButton();

    public StatusPanel(final SipModel sipModel) {
        super(new BorderLayout());
        sipModel.getMappingModel().addSetListener(new MappingModel.SetListener() {
            @Override
            public void recMappingSet(MappingModel mappingModel) {
                if (mappingModel.hasRecMapping()) {
                    setBorder(BorderFactory.createTitledBorder(String.format(
                            "Action - [%s -> %s]",
                            sipModel.getDataSetModel().getDataSet().getSpec(),
                            mappingModel.getRecMapping().getPrefix().toUpperCase()
                    )));
                }
                else {
                    setBorder(DEFAULT_BORDER);
                }
            }
        });
        for (DataSetState state : values()) {
            StateAction action = new StateAction(state);
            actions.add(action);
            if (state == SOURCED) sipModel.getDataSetModel().addListener(new SourcedListener(action));
        }
        button.setAction(actions.get(0));
        add(button, BorderLayout.CENTER);
        setBorder(DEFAULT_BORDER);
    }

    public void setState(DataSetState state) {
        for (StateAction action : actions) {
            boolean itIsMe = state == action.state;
            action.setEnabled(itIsMe);
            if (itIsMe) button.setAction(action);
        }
    }

    public void setReaction(DataSetState state, Swing swing) {
        for (StateAction stateAction : actions) if (stateAction.state == state) stateAction.setWork(swing);
    }

    public void setReaction(DataSetState state, Action action) {
        for (StateAction stateAction : actions) if (stateAction.state == state) stateAction.setAction(action);
    }

    public Action getButtonAction() {
        return actionBridge;
    }

    private class ActionBridge extends AbstractAction {
        private ActionBridge() {
            super("press!");
            putValue(ACCELERATOR_KEY, MENU_G);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            button.doClick();
        }
    }

    private class StateAction extends AbstractAction {
        private DataSetState state;
        private Swing swing;
        private Action action;

        private StateAction(DataSetState state) {
            super(state.toHtml());
            this.state = state;
            setEnabled(false);
        }

        public void setWork(Swing swing) {
            this.swing = swing;
        }

        public void setAction(Action action) {
            this.action = action;
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (swing != null) swing.run();
            if (action != null) action.actionPerformed(null);
        }
    }

    private class SourcedListener implements DataSetModel.SwingListener {
        private StateAction sourcedAction;

        private SourcedListener(StateAction action) {
            this.sourcedAction = action;
        }

        @Override
        public void stateChanged(DataSetModel model, DataSetState state) {
            if (state == SOURCED) sourcedAction.actionPerformed(null);
        }
    }
}
