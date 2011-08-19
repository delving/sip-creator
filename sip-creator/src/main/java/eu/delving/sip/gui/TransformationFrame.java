/*
 * Copyright 2010 DELVING BV
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

package eu.delving.sip.gui;

import eu.europeana.sip.model.SipModel;

import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.GridLayout;

/**
 * The transformation from input record to output
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TransformationFrame extends FrameBase {

    public TransformationFrame(JDesktopPane desktop, SipModel sipModel) {
        super(desktop, sipModel, "Transformation");
        getContentPane().add(createCenter(), BorderLayout.CENTER);
        getContentPane().add(createSouth(), BorderLayout.SOUTH);
    }

    private JComponent createCenter() {
        JPanel p = new JPanel(new GridLayout(1, 0, 5, 5));
        p.add(createInput());
        p.add(createOutput());
        return new JLabel("Output");
    }

    private JComponent createInput() {
        // todo: input has search button
        return new JLabel("Input");
    }

    private JComponent createOutput() {
        return new JLabel("Output");
    }

    private JComponent createSouth() {
        // todo: south has: validate, discard-invalid checkbox, view invalid file, and UPLOAD
        return new JLabel("Buttons");
    }

    // todo: search popup
    // todo: view invalid popup
}
