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

import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.SipModel;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JDesktopPane;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Actions corresponding to each of the data set states
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class DataSetActions {
    private JDesktopPane parent;
    private Runnable mapMode;
    private List<Action> actions = new ArrayList<Action>();
    private final MapAction mapAction = new MapAction();

    public DataSetActions(JDesktopPane parent, SipModel sipModel, CultureHubClient cultureHubClient, Runnable mapMode) {
        this.parent = parent;
        this.mapMode = mapMode;
        sipModel.getDataSetModel().addListener(new DataSetModel.Listener() {
            @Override
            public void dataSetChanged(DataSet dataSet) {
                dataSetStateChanged(dataSet, dataSet.getState());
            }

            @Override
            public void dataSetStateChanged(DataSet dataSet, DataSetState dataSetState) {
                mapAction.setEnabled(dataSetState.ordinal() >= DataSetState.ANALYZED.ordinal());
            }
        });
        actions.add(new DownloadAction(parent, sipModel, cultureHubClient));
        actions.add(new ImportAction(parent, sipModel));
        actions.add(mapAction);
        actions.add(new ValidateAction(parent, sipModel));
        actions.add(new UploadAction(parent, sipModel, cultureHubClient));
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
            mapMode.run();
        }
    }

}
