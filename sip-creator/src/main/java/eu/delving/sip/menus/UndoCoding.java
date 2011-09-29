package eu.delving.sip.menus;

import javax.swing.AbstractAction;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.event.UndoableEditEvent;
import javax.swing.undo.UndoManager;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Basic undo support for JTextAreas with popup menu. The user is able to undo/redo
 * changes to the text area.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 *         todo: add copy/paste
 */
public class UndoCoding extends UndoManager {

    private JTextArea textArea;
    private JPopupMenu popupMenu;
    private UndoAction undoAction;
    private RedoAction redoAction;

    public UndoCoding(JTextArea textArea) {
        this.textArea = textArea;
        undoAction = new UndoAction(this);
        redoAction = new RedoAction(this);
        configureActions();
    }

    private void configureActions() {
        popupMenu = new JPopupMenu();
        popupMenu.add(undoAction);
        popupMenu.add(redoAction);
        textArea.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent mouseEvent) {
                        if (mouseEvent.isPopupTrigger()) {
                            popupMenu.show(textArea, mouseEvent.getX(), mouseEvent.getY());
                        }
                    }
                }
        );
    }

    @Override
    public void undoableEditHappened(UndoableEditEvent undoableEditEvent) {
        super.undoableEditHappened(undoableEditEvent);
        toggleActions();
    }

    @Override
    public void discardAllEdits() {
        super.discardAllEdits();
        toggleActions();
    }

    private void toggleActions() {
        undoAction.setEnabled(canUndo());
        redoAction.setEnabled(canRedo());
    }

    private class UndoAction extends AbstractAction {

        private UndoManager undoManager;

        private UndoAction(UndoManager undoManager) {
            super("Undo");
            this.undoManager = undoManager;
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            undoManager.undo();
            toggleActions();
        }
    }

    private class RedoAction extends AbstractAction {

        private UndoManager undoManager;

        private RedoAction(UndoManager undoManager) {
            super("Redo");
            this.undoManager = undoManager;
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.SHIFT_DOWN_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            undoManager.redo();
            toggleActions();
        }
    }
}
