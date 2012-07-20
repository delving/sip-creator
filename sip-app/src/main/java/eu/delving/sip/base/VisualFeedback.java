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

import eu.delving.sip.model.Feedback;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;

import static javax.swing.JOptionPane.*;

/**
 * Give the user feedback in different ways, with pop-up alerts and various kinds of question dialogs.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class VisualFeedback implements Feedback {
    private Logger log = Logger.getLogger(getClass());
    private JDesktopPane desktop;

    public VisualFeedback(JDesktopPane desktop) {
        this.desktop = desktop;
    }

    @Override
    public void alert(final String message) {
        log.warn(message);
        if (SwingUtilities.isEventDispatchThread()) {
            inYourFace(message, null);
        }
        else {
            execWait(new Runnable() {
                @Override
                public void run() {
                    inYourFace(message, null);
                }
            });
        }
    }

    @Override
    public void alert(final String message, final Exception exception) {
        log.warn(message, exception);
        if (SwingUtilities.isEventDispatchThread()) {
            inYourFace(message, exception.getMessage());
        }
        else {
            execWait(new Runnable() {
                @Override
                public void run() {
                    inYourFace(message, exception.getMessage());
                }
            });
        }
    }

    @Override
    public String ask(String question) {
        return askQuestion(desktop, question, null);
    }

    @Override
    public String ask(String question, String defaultValue) {
        return askQuestion(desktop, question, defaultValue);
    }

    @Override
    public boolean confirm(String title, String message) {
        return askOption(desktop, message, title, YES_NO_OPTION, QUESTION_MESSAGE);
    }

    @Override
    public boolean form(String title, Object... components) {
        return askOption(desktop, components, title, OK_CANCEL_OPTION, QUESTION_MESSAGE);
    }

    private void inYourFace(String message, String extra) {
        message = sanitizeHtml(message);
        extra = sanitizeHtml(extra);
        String html = String.format("<html><b>%s</b></html>", message);
        if (extra != null) html = html + String.format("<p>%s</p>", extra);
        askOption(desktop, html, "Message", DEFAULT_OPTION, INFORMATION_MESSAGE);
    }

    public static String askQuestion(JDesktopPane desktop, String question, Object initialSelectionValue) {
        final JOptionPane pane = new JOptionPane(question, QUESTION_MESSAGE, OK_CANCEL_OPTION, null, null, null);
        pane.putClientProperty(new Object(), Boolean.TRUE);
        pane.setWantsInput(true);
        pane.setInitialSelectionValue(initialSelectionValue);
        JDialog frame = pane.createDialog(desktop, "Question");
        pane.selectInitialValue();
        frame.setVisible(true);
        acquireFocus();
        Object value = pane.getInputValue();
        return value == UNINITIALIZED_VALUE ? null : (String) value;
    }

    public static boolean askOption(
            JDesktopPane desktop,
            Object message, String title,
            int optionType, int messageType
    ) {
        JOptionPane pane = new JOptionPane(message, messageType, optionType, null, null, null);
        pane.putClientProperty(new Object(), Boolean.TRUE);
        JDialog frame = pane.createDialog(desktop, title);
        pane.selectInitialValue();
        frame.setVisible(true);
        acquireFocus();
        Object selectedValue = pane.getValue();
        return selectedValue != null && selectedValue instanceof Integer && (Integer) selectedValue == YES_OPTION;
    }

    private static void acquireFocus() {
        Component fo = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (fo != null && fo.isShowing()) fo.requestFocus();
    }

//    private static void startLWModal(JInternalFrame frame) {
//        try {
//            Object obj = AccessController.doPrivileged(new ModalPrivilegedAction(Container.class, "startLWModal"));
//            if (obj != null) ((Method) obj).invoke(frame, (Object[]) null);
//        }
//        catch (Exception ex) {
//            // ignore
//        }
//    }
//
//    private static class ModalPrivilegedAction implements PrivilegedAction {
//        private Class clazz;
//        private String methodName;
//
//        public ModalPrivilegedAction(Class clazz, String methodName) {
//            this.clazz = clazz;
//            this.methodName = methodName;
//        }
//
//        public Object run() {
//            Method method = null;
//            try {
//                method = clazz.getDeclaredMethod(methodName, (Class[]) null);
//            }
//            catch (NoSuchMethodException ex) {
//                // ignore
//            }
//            if (method != null) method.setAccessible(true);
//            return method;
//        }
//    }

    private static String sanitizeHtml(String string) {
        if (string == null) return null;
        return string.replaceAll("<", "&lt;").replaceAll("&", "&amp;");
    }

    private void execWait(Runnable runnable) {
        try {
            SwingUtilities.invokeAndWait(runnable);
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
