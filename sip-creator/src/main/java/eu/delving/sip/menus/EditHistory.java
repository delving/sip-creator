package eu.delving.sip.menus;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.JTextComponent;
import javax.swing.undo.UndoManager;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Edit history support for JTextComponent. The actions are populated when a supported
 * component is selected. A JPopupMenu and a JMenu can be generated for the component
 * supporting the following actions:
 * <ul>
 * <li>cut</li>
 * <li>copy</li>
 * <li>paste</li>
 * <li>undo</li>
 * <li>redo</li>
 * </ul>
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class EditHistory extends UndoManager {

    private final static int SHORTCUT = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
    private JMenu currentMenu = new JMenu("Edit");
    private Map<JTextComponent, JMenu> menus = new HashMap<JTextComponent, JMenu>();
    private JTextComponent currentTarget;
    private Action undoAction;
    private Action redoAction;
    private ActionMap actionMap = new ActionMap();

    /**
     * Set the target JTextComponent for EditHistory. All actions will have effect on the target.
     * After the menu and actions are created for the target, they will be stored in the menus map and will
     * be used again if the same target has been selected.
     *
     * @param target The selected JTextComponent.
     */
    public void setTarget(final JTextComponent target) {
        if (null == target) {
            return;
        }
        if (menus.containsKey(target)) {
            this.currentTarget = target;
            this.currentMenu = menus.get(target);
            refreshActions();
            return;
        }
        this.currentTarget = target;
        target.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent mouseEvent) {
                        if (mouseEvent.isPopupTrigger()) {
                            refreshActions();
                            JPopupMenu popupMenu = new JPopupMenu();
                            for (Object key : actionMap.allKeys()) {
                                popupMenu.add(actionMap.get(key));
                            }
                            popupMenu.show(target, mouseEvent.getX(), mouseEvent.getY());
                        }
                    }
                }
        );
        populate();
        menus.put(target, currentMenu);
    }

    public JMenu getEditMenu() {
        return currentMenu;
    }

    private void refreshActions() {
        undoAction.setEnabled(canUndo());
        redoAction.setEnabled(canRedo());
    }

    /**
     * Invoked when the menu is requested for the first time.
     */
    private void populate() {
        undoAction = new UndoAction(this);
        redoAction = new RedoAction(this);
        Action cutAction = fetchAction(DefaultEditorKit.cutAction, "Cut", KeyStroke.getKeyStroke(KeyEvent.VK_X, SHORTCUT));
        Action copyAction = fetchAction(DefaultEditorKit.copyAction, "Copy", KeyStroke.getKeyStroke(KeyEvent.VK_C, SHORTCUT));
        Action pasteAction = fetchAction(DefaultEditorKit.pasteAction, "Paste", KeyStroke.getKeyStroke(KeyEvent.VK_V, SHORTCUT));
        actionMap.put(undoAction.getValue(Action.NAME), undoAction);
        actionMap.put(redoAction.getValue(Action.NAME), redoAction);
        actionMap.put(cutAction.getValue(Action.NAME), cutAction);
        actionMap.put(copyAction.getValue(Action.NAME), copyAction);
        actionMap.put(pasteAction.getValue(Action.NAME), pasteAction);
        for (Object key : actionMap.keys()) {
            currentMenu.add(actionMap.get(key));
        }
    }

    /**
     * Find the action by DefaultEditorKit.name and decore them for the menus.
     *
     * @param actionName    The name of the action.
     * @param preferredName Rename the action to this value.
     * @param keyStroke     Add an accelerator with this keyStroke.
     *
     * @return The action if found.
     */
    private Action fetchAction(String actionName, String preferredName, KeyStroke keyStroke) {
        for (Action action : currentTarget.getActions()) {
            if (action.getValue(Action.NAME).equals(actionName)) {
                action.putValue(Action.NAME, preferredName);
                action.putValue(Action.ACCELERATOR_KEY, keyStroke);
                return action;
            }
        }
        return null;
    }

    private class UndoAction extends AbstractAction {

        private UndoManager undoManager;

        private UndoAction(UndoManager undoManager) {
            super("Undo");
            this.undoManager = undoManager;
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_Z, SHORTCUT));
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            undoManager.undo();
            refreshActions();
        }
    }

    private class RedoAction extends AbstractAction {

        private UndoManager undoManager;

        private RedoAction(UndoManager undoManager) {
            super("Redo");
            this.undoManager = undoManager;
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_Z, SHORTCUT | KeyEvent.SHIFT_DOWN_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            undoManager.redo();
            refreshActions();
        }
    }
}
