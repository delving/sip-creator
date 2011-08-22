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
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JInternalFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyVetoException;

/**
 * The base of all windows within the SIP-Creator.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public abstract class FrameBase extends PopupFrame {
    private static final Dimension DEFAULT_SIZE = new Dimension(800, 600);
    private static final int MARGIN = 12;
    private Dimension defaultSize = DEFAULT_SIZE;
    protected SipModel sipModel;
    protected Action action;
    private boolean initialized;

    public FrameBase(JComponent parent, SipModel sipModel, String title, boolean modal) {
        super(parent, title, modal);
        this.sipModel = sipModel;
        this.action = new PopupAction(title, !modal);
    }

    public void setDefaultSize(int width, int height) {
        defaultSize = new Dimension(width, height);
    }

    protected abstract void initContent(Container content);

    @Override
    public void show() {
        if (!initialized) {
            JPanel content = (JPanel) getContentPane();
            content.setBorder(BorderFactory.createEmptyBorder(MARGIN, MARGIN, MARGIN, MARGIN));
            initContent(content);
            initialized = true;
        }
        Point added = addIfAbsent();
        super.show();
        if (added != null) {
            setLocation(added);
            setSize(defaultSize); // after show
        }
        if (hasChildFrame()) {
            childFrame.moveToFront();
            try {
                childFrame.setSelected(true);
            }
            catch (PropertyVetoException e) {
                e.printStackTrace();  // child should not veto
            }
        }
        else {
            moveToFront();
            ensureOnScreen();
            try {
                setSelected(true);
            }
            catch (PropertyVetoException e) {
                e.printStackTrace();  // we should not veto
            }
        }
    }

    public Action getAction() {
        return action;
    }

    private class PopupAction extends AbstractAction {

        public PopupAction(String title, boolean withAccelerator) {
            super(title);
            if (withAccelerator) {
                this.putValue(
                        Action.ACCELERATOR_KEY,
                        KeyStroke.getKeyStroke(
                                KeyEvent.VK_A + (title.charAt(0) - 'A'),
                                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()
                        )
                );
            }
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            show();
        }

    }

    private Point addIfAbsent() {
        boolean add = true;
        JInternalFrame[] frames = desktopPane.getAllFrames();
        Point max = new Point();
        for (JInternalFrame frame : frames) {
            if (frame == this) {
                add = false;
            }
            Point loc = frame.getLocation();
            if (max.x < loc.x) {
                max.x = loc.x;
            }
            if (max.y < loc.y) {
                max.y = loc.y;
            }
        }
        if (add) {
            desktopPane.add(this);
            max.x += 25;
            max.y += 25;
            return max;
        }
        else {
            return null;
        }
    }

    private void ensureOnScreen() {
        boolean move = false;
        Point loc = getLocation();
        Dimension desk = desktopPane.getSize();
        Dimension size = getSize();
        Point far = new Point(loc.x + size.width, loc.y + size.height);
        if (loc.x < 0) {
            loc.x = 0;
            move = true;
        }
        else if (far.x > desk.width) {
            loc.x -= far.x - desk.width;
            move = true;
        }
        if (loc.y < 0) {
            loc.y = 0;
            move = true;
        }
        else if (far.y > desk.height) {
            loc.y -= far.y - desk.height;
            move = true;
        }
        if (move) {
            setLocation(loc);
        }
    }

    protected static JScrollPane scroll(JComponent content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
//        scroll.setPreferredSize(new Dimension(300, 800));
        return scroll;
    }

}
