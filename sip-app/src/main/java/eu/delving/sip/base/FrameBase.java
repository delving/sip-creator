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

package eu.delving.sip.base;

import eu.delving.sip.frames.AllFrames;
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
import java.util.ArrayList;
import java.util.List;

/**
 * The base of all windows within the SIP-Creator.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public abstract class FrameBase extends JInternalFrame {
    public static Insets INSETS = new Insets(2, /* top */ 8, /* left */ 14, /* bottom */ 10 /* right */);
    private static final int DEFAULT_MOVE_INTERVAL = 1000;
    private static final int MARGIN = 12;
    private JMenuBar originalMenuBar;
    private Which which;
    private Placement placement;
    protected FrameBase childFrame;
    protected JComponent focusOwner;
    protected SipModel sipModel;
    private boolean initialized;
    private Timer positionTimer;
    private AllFrames.XArrangement arrangement;
    private List<AdjustAction> adjustActions = new ArrayList<AdjustAction>();
    private Swing arrange;
    private String title;

    public enum Which {
        SOURCE,
        CREATE,
        TARGET,
        STATISTICS,
        INPUT,
        REC_MAPPING,
        FIELD_MAPPING,
        OUTPUT,
        MAPPING_CODE,
        WORK,
        DATA_SET,
        REPORT,
        HARVEST,
        MAPPING_HINTS,
        FUNCTIONS,
        QUESTION,
        MESSAGE,
    }

    public interface Placement {
        Point getLocation();

        Dimension getSize();
    }

    protected FrameBase(Which which, SipModel sipModel, String title) {
        super(
                title,
                true, // resizable
                true, // closeable
                true, // maximizable
                false // iconifiable
        );
        this.title = title;
        this.which = which;
        try {
            setClosed(true);
        }
        catch (PropertyVetoException e) {
            throw new RuntimeException(e);
        }
        this.sipModel = sipModel;
        for (int pos = 0; pos < 4; pos++) {
            adjustActions.add(new AdjustAction(pos, 1));
            adjustActions.add(new AdjustAction(pos, -1));
        }
        positionTimer = new Timer(DEFAULT_MOVE_INTERVAL,
                new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        ensureOnScreen();
                    }
                }
        );
        positionTimer.setRepeats(false);
        setGlassPane(new InternalGlassPane(this));
//        addFrameListener();
        addFrameVetoListener();
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

    public void setArrangementSource(AllFrames.XArrangement arrangement, Swing arrange) {
        this.arrangement = arrangement;
        this.arrange = arrange;
    }

    public void toggleEditMenu() {
        if (originalMenuBar == null && getJMenuBar() != null) originalMenuBar = getJMenuBar();
        if (getJMenuBar() == originalMenuBar) {
            JMenuBar bar = new JMenuBar();
            JMenu menu = new JMenu("Edit");
            for (AdjustAction action : adjustActions) menu.add(action);
            bar.add(menu);
            setJMenuBar(bar);
            AllFrames.XFrame frame = frame();
            if (frame != null && getJMenuBar() != null) {
                setTitle(String.format("%s ( X=%c Y=%c W=%c H=%c )", title, frame.where.charAt(0), frame.where.charAt(1), frame.where.charAt(2), frame.where.charAt(3)));
            }
            else {
                setTitle(title);
            }
        }
        else {
            setJMenuBar(originalMenuBar);
            setTitle(title);
        }
        validateTree();
    }

    public Which getWhich() {
        return which;
    }

    public void setFrameLocked(boolean locked) {
        getGlassPane().setVisible(locked);
    }

    // override this
    protected void onOpen(boolean opened) {
    }

    // override this
    public void refresh() {
    }

    public void setPlacement(Placement placement) {
        this.placement = placement;
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

    public void openFrame() {
        init();
        boolean added = addIfAbsent();
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
        JInternalFrame[] frames = sipModel.getDesktop().getAllFrames();
        for (JInternalFrame frame : frames) if (frame == this) add = false;
        if (add) sipModel.getDesktop().add(this);
        return add;
    }

    /**
     * When a frame, or more precisely it's title bar, ends up outside the visible area of the desktop,
     * the user will not be able to move the frame again. This method will keep track of the positioning
     * of the frame and will move it back to the visible area when needed.
     */
    public void ensureOnScreen() {
        Point loc = getLocation();
        Dimension desktopSize = sipModel.getDesktop().getSize();
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
            sipModel.exec(new Swing() {
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
    class InternalGlassPane extends JComponent {
        private boolean consuming;

        public InternalGlassPane(final FrameBase frame) {
            setToolTipText(
                    "<html><b>Mapping is locked<b><br>" +
                            "<p>You can unlock it from the Data Sets menu.</p>"
            );
            setFocusable(true);
            addComponentListener(new ComponentListener() {
                @Override
                public void componentResized(ComponentEvent e) {
                }

                @Override
                public void componentMoved(ComponentEvent e) {
                }

                @Override
                public void componentShown(ComponentEvent e) {
                    consuming = true;
                    requestFocus();
                }

                @Override
                public void componentHidden(ComponentEvent e) {
                    consuming = false;
                }
            });
            addKeyListener(new KeyListener() {
                @Override
                public void keyTyped(KeyEvent e) {
                    consume(e);
                }

                @Override
                public void keyPressed(KeyEvent e) {
                    consume(e);
                }

                @Override
                public void keyReleased(KeyEvent e) {
                    consume(e);
                }
            });
            addMouseListener(new MouseListener() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    consume(e);
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    consume(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    consume(e);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    consume(e);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    consume(e);
                }
            });
            addMouseMotionListener(new MouseMotionListener() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    consume(e);
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    consume(e);
                }
            });
            addMouseListener(new MouseAdapter() {

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!frame.isSelected()) {
                        try {
                            frame.setSelected(true);
                            if (!frame.hasChildFrame()) {
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

        private void consume(KeyEvent e) {
            if (consuming) e.consume();
        }

        private void consume(MouseEvent e) {
            if (consuming) e.consume();
        }


        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
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
            Dimension s = sipModel.getDesktop().getSize();
            return new Dimension(s.width - margin * 2, s.height - margin * 2);
        }
    }

    private class AdjustAction extends AbstractAction {
        private int position;
        private int direction;

        private AdjustAction(int position, int direction) {
            super("XYWH".charAt(position) + (direction > 0 ? "+" : "-"));
            this.position = position;
            this.direction = direction;
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            AllFrames.XFrame frame = frame();
            if (frame == null) return;
            StringBuilder out = new StringBuilder();
            for (int walk = 0; walk < 4; walk++) {
                char ch = frame.where.charAt(walk);
                if (position == walk) {
                    switch (ch) {
                        case '0':
                            out.append((direction < 0 && position <= 1) ? ch : (char) (ch + direction));
                            break;
                        case '1':
                            out.append((direction < 0 && position > 1) ? ch : (char) (ch + direction));
                            break;
                        case '9':
                            out.append((direction > 0) ? ch : (char) (ch + direction));
                            break;
                        default:
                            out.append((char) (ch + direction));
                    }
                }
                else {
                    out.append(frame.where.charAt(walk));
                }
            }
            frame.where = out.toString();
            arrange.run();
        }

    }

    private AllFrames.XFrame frame() {
        AllFrames.XFrame frame = null;
        for (AllFrames.XFrame maybe : arrangement.frames) {
            if (maybe.which == which) frame = maybe;
        }
        return frame;
    }

    public void exec(Swing swing) {
        Swing.Exec.later(swing);
    }

    public void exec(Work work) {
        sipModel.exec(work);
    }


}
