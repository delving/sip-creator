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

package eu.delving.sip.actions;

import eu.delving.sip.base.Work;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import java.awt.event.ActionEvent;

import static eu.delving.sip.base.KeystrokeHelper.MENU_U;
import static eu.delving.sip.base.KeystrokeHelper.configAction;
import static eu.delving.sip.base.SwingHelper.ICON_UNLOCK;

/**
 * Unlock this mapping
 *
 *
 */

public class UnlockMappingAction extends AbstractAction implements Work {

    private SipModel sipModel;

    public UnlockMappingAction(SipModel sipModel) {
        configAction(this, "Unlock mapping for editing", ICON_UNLOCK, MENU_U);
        this.sipModel = sipModel;
        setEnabled(false);
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        sipModel.exec(this);
    }

    @Override
    public void run() {
        sipModel.getMappingModel().setLocked(false);
        sipModel.getDataSetModel().deleteResults();
        sipModel.getReportFileModel().refresh();
    }

    @Override
    public Job getJob() {
        return Job.UNLOCK_MAPPING;
    }
}
