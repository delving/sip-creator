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

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import java.awt.Dimension;
import java.awt.event.ActionEvent;

/**
 * The base of all windows within the SIP-Creator.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public abstract class FrameBase extends JInternalFrame {
    private static final Dimension DEFAULT_SIZE = new Dimension(800, 600);
    protected SipModel sipModel;
    protected Action action;

    public FrameBase(JDesktopPane desktop, SipModel sipModel, String title) {
        super(
                title,
                true, // resizable
                true, // closable
                true, // maximizable
                false // iconifiable
        );
        this.sipModel = sipModel;
        action = new AbstractAction(title) {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                setVisible(true);
                // maybe relocate if it's off screen or something
            }
        };
        setSize(DEFAULT_SIZE);
        desktop.add(this);
    }

    public Action getAction() {
        return action;
    }
}
