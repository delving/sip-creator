package eu.delving.sip.gui;

import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JOptionPane;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;

/**
 * An extended <code>JInternalFrame</code> that provides modality in a child/parent
 * hierarchy
 *
 * @author webbyit
 */
public abstract class PopupFrame extends JInternalFrame {
    protected JDesktopPane desktopPane;
    protected JComponent parent;
    protected PopupFrame childFrame;
    protected JComponent focusOwner;

    public PopupFrame(JComponent parent, String title, boolean modal) {
        super(
                title,
                true, // resizable
                !modal, // closeable
                true, // maximizable
                false // iconifiable
        );
        setParentFrame(parent);
        setGlassPane(new ModalityInternalGlassPane(this));
        addFrameListener();
        addFrameVetoListener();
        if (modal) {
            if (parent != null && parent instanceof PopupFrame) {
                ((PopupFrame) parent).setChildFrame(PopupFrame.this);
            }
            setFocusTraversalKeysEnabled(false);
        }
    }

    private void setParentFrame(JComponent parent) {
        desktopPane = JOptionPane.getDesktopPaneForComponent(parent);
        this.parent = parent == null ? JOptionPane.getDesktopPaneForComponent(parent) : parent; // default to desktop if no parent given
    }

    public void setChildFrame(PopupFrame childFrame) {
        this.childFrame = childFrame;
    }

    public boolean hasChildFrame() {
        return (childFrame != null);
    }

    protected void closeFrame() {
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
                if (parent != null && parent instanceof PopupFrame) {
                    ((PopupFrame) parent).childClosing();
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

    @Override
    public void show() {
        if (parent != null && parent instanceof PopupFrame) {
            // Need to inform parent its about to lose its focus due
            // to child opening
            ((PopupFrame) parent).childOpening();
        }
        super.show();
    }

    /**
     * Glass pane to overlay. Listens for mouse clicks and sets selected
     * on associated modal frame. Also if modal frame has no children make
     * class pane invisible
     */
    class ModalityInternalGlassPane extends JComponent {

        private PopupFrame modalFrame;

        public ModalityInternalGlassPane(PopupFrame frame) {
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
}
