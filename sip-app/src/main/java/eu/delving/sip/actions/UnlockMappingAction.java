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

package eu.delving.sip.actions;

import eu.delving.sip.base.Work;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import java.awt.event.ActionEvent;

import static eu.delving.sip.base.KeystrokeHelper.MENU_U;
import static eu.delving.sip.base.KeystrokeHelper.configAction;
import static eu.delving.sip.base.SwingHelper.ICON_UNLOCK;

/**
 * Unlock this mapping
 *
 * @author Gerald de Jong <gerald@delving.eu>
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
        try {
            sipModel.getDataSetModel().deleteValidation();
        }
        catch (StorageException e) {
            sipModel.getFeedback().alert("Unable to delete validation", e);
        }
    }

    @Override
    public Job getJob() {
        return Job.UNLOCK_MAPPING;
    }
}
