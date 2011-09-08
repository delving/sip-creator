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

import eu.delving.sip.model.SipModel;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Actions corresponding to each of the data set states
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class DataSetActions {
    private JFrame parent;
    private List<Action> actions = new ArrayList<Action>();

    public DataSetActions(JFrame parent, SipModel sipModel, CultureHubClient cultureHubClient, List<FrameBase> frames) {
        this.parent = parent;
        actions.add(new DownloadAction(parent, sipModel, cultureHubClient));
        actions.add(new ImportAction(parent, sipModel));
        actions.add(new MapAction());
        actions.add(new ValidateAction());
        actions.add(new UploadAction());
    }

    public List<? extends Action> getActions() {
        return actions;
    }

    private class MapAction extends AbstractAction {

        private MapAction() {
            super("Map");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            JOptionPane.showMessageDialog(parent, actionEvent.getActionCommand());
        }
    }

    private class ValidateAction extends AbstractAction {

        private ValidateAction() {
            super("Validate");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            JOptionPane.showMessageDialog(parent, actionEvent.getActionCommand());
        }
    }

    private class UploadAction extends AbstractAction {

        private UploadAction() {
            super("Upload");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            JOptionPane.showMessageDialog(parent, actionEvent.getActionCommand());
        }
    }

}
