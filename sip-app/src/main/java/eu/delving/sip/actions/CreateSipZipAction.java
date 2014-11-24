/*
 * Copyright 2011, 2012 Delving BV
 *
 * Licensed under the EUPL, Version 1.0 or as soon they
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

import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;

/**
 * Create a sip zip
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class CreateSipZipAction extends AbstractAction {
    private SipModel sipModel;

    public CreateSipZipAction(SipModel sipModel) {
        super("Create");
        putValue(Action.SMALL_ICON, SwingHelper.ICON_UPLOAD);
        this.sipModel = sipModel;
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        setEnabled(false);
        if (sipModel.getDataSetModel().isEmpty()) return;
        try {
            File file = sipModel.getDataSetModel().createSipZip();
            sipModel.getFeedback().alert("SipZip Created: "+file.getAbsolutePath());
        }
        catch (StorageException e) {
            sipModel.getFeedback().alert("Unable to create sip zip", e);
        }
    }
}
