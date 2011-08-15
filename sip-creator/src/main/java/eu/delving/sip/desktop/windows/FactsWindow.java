/*
 * Copyright 2011 DELVING BV
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

package eu.delving.sip.desktop.windows;

import eu.europeana.sip.model.SipModel;

import javax.swing.JLabel;
import java.awt.BorderLayout;

/**
 * The facts related to a data set, available as constants for the mapping
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class FactsWindow extends DesktopWindow {

    public FactsWindow(SipModel sipModel) {
        super(sipModel);
        add(new JLabel("FACTS"), BorderLayout.CENTER);
        // todo: snatch the facts input fields from the previous version
    }
}
