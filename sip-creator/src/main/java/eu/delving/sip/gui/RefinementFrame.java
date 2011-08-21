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

package eu.delving.sip.gui;

import eu.europeana.sip.model.SipModel;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyVetoException;

/**
 * Refining the mapping interactively
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class RefinementFrame extends FrameBase {

    public RefinementFrame(JDesktopPane desktop, SipModel sipModel) {
        super(desktop, sipModel, "Refinement", false);
        getContentPane().add(createWest(), BorderLayout.WEST);
        getContentPane().add(createCenter(), BorderLayout.CENTER);
    }

    private JComponent createWest() {
        JButton b = new JButton("Mapping List");
        b.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                showPopupFrame();
             }
        });
        return b;
    }

    private void showPopupFrame() {
        final FrameBase pop = createPopup("Pop!");
        pop.getContentPane().setLayout(new BorderLayout());
        pop.getContentPane().add(new JLabel("Crackle to the max!"), BorderLayout.CENTER);
        JButton close = new JButton("close");
        close.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    pop.setClosed(true);
                }
                catch (PropertyVetoException e) {
                    e.printStackTrace();  // todo: something
                }
            }
        });
        pop.getContentPane().add(close, BorderLayout.EAST);
        pop.show();
    }

    private JComponent createCenter() {
        JPanel p = new JPanel(new GridLayout(0, 1, 5, 5));
        p.add(createCodePane());
        p.add(createOutputPane());
        return p;
    }

    private JComponent createCodePane() {
        // todo: code window contains dictionary mapping buttons
        return new JLabel("Code");
    }

    private JComponent createOutputPane() {
        return new JTextField();
    }

    // todo: dictionary mapping popup
}
