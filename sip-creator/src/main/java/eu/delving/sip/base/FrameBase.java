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

package eu.delving.sip.base;

import eu.delving.sip.model.SipModel;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;

/**
 * The base of all windows within the SIP-Creator.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public abstract class FrameBase extends JInternalFrame {
    private static final Dimension DEFAULT_SIZE = new Dimension(800, 600);
    private static final int MARGIN = 12;
    private Dimension defaultSize = DEFAULT_SIZE;
    protected JDesktopPane desktopPane;
    protected JComponent parent;
    protected FrameBase childFrame;
    protected JComponent focusOwner;
    protected SipModel sipModel;
    protected Action action;
    private boolean modal;
    private boolean initialized;

    public FrameBase(JComponent parent, SipModel sipModel, String title, boolean modal) {
        super(
                title,
                true, // resizable
                !modal, // closeable
                true, // maximizable
                false // iconifiable
        );
        this.parent = parent;
        this.sipModel = sipModel;
        this.action = new PopupAction(title);
        this.modal = modal;
        this.desktopPane = parent instanceof FrameBase ? ((FrameBase) parent).desktopPane : JOptionPane.getDesktopPaneForComponent(parent);
        setGlassPane(new ModalityInternalGlassPane(this));
        addFrameListener();
        addFrameVetoListener();
        if (modal) {
            setFocusTraversalKeysEnabled(false);
        }
        if (getStoredSize() != null) {
            Exec.swingLater(new Runnable() {
                @Override
                public void run() {
                    show();
                }
            });
        }
    }

    public void setAccelerator(int number) {
        action.putValue(
                Action.ACCELERATOR_KEY,
                KeyStroke.getKeyStroke(KeyEvent.VK_0 + number, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())
        );
    }

    public void setDefaultSize(int width, int height) {
        defaultSize = new Dimension(width, height);
    }

    public SipModel getSipModel() {
        return sipModel;
    }

    protected abstract void buildContent(Container content);

    protected abstract void refresh();

    @Override
    public void show() {
        init();
        refresh();
        Point added = addIfAbsent();
        if (parent instanceof FrameBase) {
            ((FrameBase) parent).setChildFrame(FrameBase.this);
            // Need to inform parent its about to lose its focus due
            // to child opening
            ((FrameBase) parent).childOpening();
        }
        super.show();
        if (added != null) {
            setLocation(added);
            Dimension storedSize = getStoredSize();
            if (storedSize != null) {
                setSize(storedSize);
            }
            else {
                setSize(defaultSize); // after show
            }
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
        try {
            setClosed(false);
        }
        catch (PropertyVetoException e) {
            e.printStackTrace();  // nobody should be vetoing this
        }
    }

    public void init() {
        if (!initialized) {
            JPanel content = (JPanel) getContentPane();
            content.setBorder(BorderFactory.createEmptyBorder(MARGIN, MARGIN, MARGIN, MARGIN));
            buildContent(content);
            initialized = true;
        }
    }

    public Action getAction() {
        return action;
    }

    private class PopupAction extends AbstractAction {

        public PopupAction(String title) {
            super(title);
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (!sipModel.hasDataSetStore() || sipModel.getMappingModel().getRecordMapping() == null) {
                JOptionPane.showInternalMessageDialog(desktopPane, "A Dataset and Mapping must be selected from the menus", FrameBase.this.getTitle(), JOptionPane.PLAIN_MESSAGE);
            }
            else {
                show();
            }
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
            if (modal) {
                parent.getLocation(max);
                max.x += 25;
                max.y += 25;
                return max;
            }
            else {
                Point storedLocation = getStoredLocation();
                if (storedLocation != null) {
                    return storedLocation;
                }
                else {
                    max.x += 25;
                    max.y += 25;
                    return max;
                }
            }
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

    public void setChildFrame(FrameBase childFrame) {
        this.childFrame = childFrame;
    }

    public boolean hasChildFrame() {
        return childFrame != null;
    }

    public void closeFrame() {
        try {
            setClosed(true);
        }
        catch (PropertyVetoException e) {
            e.printStackTrace();  // nobody should be vetoing this
        }
    }

    protected void addFrameVetoListener() {
        addVetoableChangeListener(new VetoableChangeListener() {

            public void vetoableChange(PropertyChangeEvent evt) throws PropertyVetoException {
                if (evt.getPropertyName().equals(JInternalFrame.IS_SELECTED_PROPERTY)
                        && evt.getNewValue().equals(Boolean.TRUE)) {
                    if (hasChildFrame()) {
                        childFrame.setSelected(true);
                        if (childFrame.isIcon()) {
                            childFrame.setIcon(false);
                        }
                        throw new PropertyVetoException("no!", evt);
                    }
                }
            }
        });
    }

    /**
     * Method to control the display of the glasspane, dependant
     * on the frame being active or not
     */
    protected void addFrameListener() {
        addInternalFrameListener(new InternalFrameAdapter() {

            @Override
            public void internalFrameActivated(InternalFrameEvent e) {
                if (hasChildFrame()) {
                    getGlassPane().setVisible(true);
                    grabFocus();
                }
                else {
                    getGlassPane().setVisible(false);
                }
            }

            @Override
            public void internalFrameDeactivated(InternalFrameEvent e) {
                if (hasChildFrame()) {
                    getGlassPane().setVisible(true);
                    grabFocus();
                }
                else {
                    getGlassPane().setVisible(false);
                }
            }

            @Override
            public void internalFrameOpened(InternalFrameEvent e) {
                getGlassPane().setVisible(false);
            }

            @Override
            public void internalFrameClosing(InternalFrameEvent e) {
                if (parent != null && parent instanceof FrameBase) {
                    ((FrameBase) parent).childClosing();
                }
            }
        });
    }

    /**
     * Method to handle child frame closing and make this frame
     * available for user input again with no glasspane visible
     */
    protected void childClosing() {
        getGlassPane().setVisible(false);
        if (focusOwner != null) {
            java.awt.EventQueue.invokeLater(new Runnable() {

                public void run() {
                    try {
                        moveToFront();
                        setSelected(true);
                        focusOwner.grabFocus();
                    }
                    catch (PropertyVetoException ex) {
                        ex.printStackTrace();
                    }
                }
            });
            focusOwner.grabFocus();
        }
        getGlassPane().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        setChildFrame(null);
        setClosable(true);
    }

    /*
     * Method to handle child opening and becoming visible.
     */
    protected void childOpening() {
        // record the present focused component
        setClosable(false);
        focusOwner = (JComponent) getFocusOwner();
        grabFocus();
        getGlassPane().setVisible(true);
        getGlassPane().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    }

    /**
     * Glass pane to overlay. Listens for mouse clicks and sets selected
     * on associated modal frame. Also if modal frame has no children make
     * class pane invisible
     */
    class ModalityInternalGlassPane extends JComponent {

        private FrameBase modalFrame;

        public ModalityInternalGlassPane(FrameBase frame) {
            modalFrame = frame;
            addMouseListener(new MouseAdapter() {

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!modalFrame.isSelected()) {
                        try {
                            modalFrame.setSelected(true);
                            if (!modalFrame.hasChildFrame()) {
                                setVisible(false);
                            }
                        }
                        catch (PropertyVetoException e1) {
                            //e1.printStackTrace();
                        }
                    }
                }
            });
        }

        @Override
        public void paint(Graphics g) {
            super.paint(g);
            g.setColor(new Color(255, 255, 255, 100));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    private Dimension getStoredSize() {
        Dimension size = new Dimension(getStoredInt("width"), getStoredInt("height"));
        return size.width > 100 && size.height > 100 ? size : null;
    }

    private Point getStoredLocation() {
        Point location = new Point(getStoredInt("x"), getStoredInt("y"));
        return location.x >= 0 && location.y >= 0 ? location : null;
    }

    private int getStoredInt(String name) {
        return sipModel.getPreferences().getInt(String.format("%s:%s", title, name), -1);
    }

    public void putState() {
        if (isVisible()) {
            putStoredInt("x", getLocation().x);
            putStoredInt("y", getLocation().y);
            putStoredInt("width", getSize().width);
            putStoredInt("height", getSize().height);
        }
        else {
            putStoredInt("x", -1);
            putStoredInt("y", -1);
            putStoredInt("width", -1);
            putStoredInt("height", -1);
        }
    }

    private void putStoredInt(String name, int value) {
        sipModel.getPreferences().putInt(String.format("%s:%s", title, name), value);
    }

    protected static JScrollPane scroll(JComponent content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        return scroll;
    }

}
