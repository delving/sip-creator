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

import javax.swing.*;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;

/**
 * The base of all windows within the SIP-Creator.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public abstract class FrameBase extends JInternalFrame {
    public static Insets INSETS = new Insets(2, /* top */ 8, /* left */ 14, /* bottom */ 10 /* right */);
    private static final int DEFAULT_MOVE_INTERVAL = 1000;
    private static final int MARGIN = 12;
    private Which which;
    private Placement placement;
    protected JDesktopPane desktopPane;
    protected JComponent parent;
    protected FrameBase childFrame;
    protected JComponent focusOwner;
    protected SipModel sipModel;
    protected PopupAction action;
    private boolean initialized;
    private Timer positionTimer;

    public enum Which {
        ANALYSIS,
        CREATE,
        REC_DEF,
        STATISTICS,
        INPUT,
        REC_MAPPING,
        FIELD_MAPPING,
        OUTPUT,
        CODE,
        LOG,
        HARVEST,
    }

    public interface Placement {
        Point getLocation();

        Dimension getSize();
    }

    public FrameBase(Which which, final JComponent parent, SipModel sipModel, String title, boolean modal) {
        super(
                title,
                true, // resizable
                !modal, // closeable
                true, // maximizable
                false // iconifiable
        );
        this.which = which;
        try {
            setClosed(true);
        }
        catch (PropertyVetoException e) {
            throw new RuntimeException(e);
        }
        this.parent = parent;
        this.sipModel = sipModel;
        this.action = new PopupAction(title);
        this.desktopPane = parent instanceof FrameBase ? ((FrameBase) parent).desktopPane : JOptionPane.getDesktopPaneForComponent(parent);
        positionTimer = new Timer(DEFAULT_MOVE_INTERVAL,
                new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        ensureOnScreen();
                    }
                }
        );
        positionTimer.setRepeats(false);
        setGlassPane(new ModalityInternalGlassPane(this));
        addFrameListener();
        addFrameVetoListener();
        if (modal) {
            setFocusTraversalKeysEnabled(false);
        }
        super.addPropertyChangeListener("closed", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent event) {
                boolean opened = !((Boolean) event.getNewValue());
                onOpen(opened);
            }
        });
        addComponentListener(
                new ComponentAdapter() {
                    @Override
                    public void componentMoved(ComponentEvent componentEvent) {
                        positionTimer.restart();
                    }
                }
        );
    }

    public Which getWhich() {
        return which;
    }

    // override this
    protected void onOpen(boolean opened) {
    }

    public void setAccelerator(int number) {
        action.putValue(
                Action.ACCELERATOR_KEY,
                KeyStroke.getKeyStroke(KeyEvent.VK_0 + number, KeyEvent.CTRL_MASK)
        );
    }

    public void setPlacement(Placement placement) {
        this.placement = placement;
    }

    public Placement getPlacement() {
        return placement;
    }

    protected abstract void buildContent(Container content);

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
            if (isShowing()) {
                closeFrame();
            } else {
                openFrame();
            }
        }
    }

    public void openFrame() {
        init();
        boolean added = addIfAbsent();
        if (parent instanceof FrameBase) {
            ((FrameBase) parent).setChildFrame(FrameBase.this);
            // Need to inform parent its about to lose its focus due
            // to child opening
            ((FrameBase) parent).childOpening();
        }
        super.show();
        if (added) {
            if (placement == null) placement = new DefaultPlacement();
            setLocation(placement.getLocation());
            setSize(placement.getSize());
        }
        ensureOnScreen();
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

    private boolean addIfAbsent() {
        boolean add = true;
        JInternalFrame[] frames = desktopPane.getAllFrames();
        for (JInternalFrame frame : frames) if (frame == this) add = false;
        if (add) desktopPane.add(this);
        return add;
    }

    /**
     * When a frame, or more precisely it's title bar, ends up outside the visible area of the desktop,
     * the user will not be able to move the frame again. This method will keep track of the positioning
     * of the frame and will move it back to the visible area when needed.
     */
    public void ensureOnScreen() {
        Point loc = getLocation();
        Dimension desktopSize = desktopPane.getSize();
        if (loc.y - INSETS.top + 1 < 0) {
            loc.y = INSETS.top - 1;
            setLocation(loc);
        }
        if (loc.y + INSETS.bottom + 1 >= desktopSize.height) {
            loc.y = desktopSize.height - getHeight() + INSETS.bottom + 1;
            setLocation(loc);
        }
        if (loc.x + INSETS.right + 1 >= desktopSize.width) {
            loc.x = desktopSize.width - getWidth() + INSETS.right + 1;
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

    private class DefaultPlacement implements Placement {
        final int margin = 50;

        @Override
        public Point getLocation() {
            return new Point(margin, margin);
        }

        @Override
        public Dimension getSize() {
            Dimension s = desktopPane.getSize();
            return new Dimension(s.width - margin * 2, s.height - margin * 2);
        }
    }

}
