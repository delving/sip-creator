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
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;
import java.util.prefs.Preferences;

import static javax.swing.JOptionPane.*;

/**
 * Give the user feedback in different ways, with pop-up alerts and various kinds of question dialogs.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class VisualFeedback implements Feedback {
    private Logger log = Logger.getLogger(getClass());
    private JDesktopPane desktop;
    private PasswordFetcher passwordFetcher;

    public VisualFeedback(JFrame frame, JDesktopPane desktop, Preferences preferences) {
        this.desktop = desktop;
        this.passwordFetcher = new PasswordFetcher(frame, preferences);
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
        final String exceptionMessage = exception.getMessage().trim().isEmpty() ? exception.getClass().getName() : exception.getMessage();
        if (SwingUtilities.isEventDispatchThread()) {
            inYourFace(message, exceptionMessage);
        }
        else {
            execWait(new Runnable() {
                @Override
                public void run() {
                    inYourFace(message, exceptionMessage);
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

    @Override
    public String getPassword() {
        return passwordFetcher.getPassword();
    }

    private void inYourFace(String message, String extra) {
        String sanitizedMessage = sanitizeHtml(message);
        String sanitizedExtra = sanitizeHtml(extra);
        String html = String.format("<html><b>%s</b></html>", sanitizedMessage);
        if (extra != null) html = html + String.format("<p>%s</p>", sanitizedExtra);
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
        return string.replaceAll("&", "&amp;").replaceAll("<", "&lt;");
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

    private class PasswordFetcher implements OAuthClient.PasswordRequest, ActionListener {
        private static final String PASSWORD = "Password";
        private JDialog dialog;
        private Preferences preferences;
        private JPasswordField passwordField = new JPasswordField(15);
        private JCheckBox savePassword = new JCheckBox("Save password");
        private StringBuilder password = new StringBuilder();
        private JButton ok = new JButton("Ok");

        private PasswordFetcher(JFrame frame, Preferences preferences) {
            this.dialog = new JDialog(frame, "Culture Hub", true);
            this.preferences = preferences;
            // We disable the submit button by default and if the content != empty
            ok.addActionListener(this);
            ok.setEnabled(false);
            this.dialog.getRootPane().setDefaultButton(ok);
            String savedPassword = preferences.get(PASSWORD, "");
            savePassword.setSelected(!savedPassword.isEmpty());
            passwordField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent documentEvent) {
                    ok.setEnabled(!StringUtils.isWhitespace(new String(passwordField.getPassword())));
                }

                @Override
                public void removeUpdate(DocumentEvent documentEvent) {
                    insertUpdate(documentEvent);
                }

                @Override
                public void changedUpdate(DocumentEvent documentEvent) {
                    insertUpdate(documentEvent);
                }
            });
            passwordField.setText(savedPassword);
            JLabel labelA = new JLabel("Password: ");
            labelA.setLabelFor(passwordField);
            passwordField.addActionListener(this);

            JPanel fieldPanel = new JPanel(new BorderLayout(10, 10));
            fieldPanel.add(labelA, BorderLayout.WEST);
            fieldPanel.add(passwordField, BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.add(ok);

            JPanel wrap = new JPanel(new GridLayout(0, 1, 5, 5));
            wrap.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            wrap.add(fieldPanel);
            wrap.add(savePassword);
            wrap.add(buttonPanel);

            dialog.getContentPane().add(wrap, BorderLayout.CENTER);
            dialog.pack();
        }

        @Override
        public String getPassword() {
            dialog.setLocation(
                    (Toolkit.getDefaultToolkit().getScreenSize().width - dialog.getSize().width) / 2,
                    (Toolkit.getDefaultToolkit().getScreenSize().height - dialog.getSize().height) / 2
            );
            dialog.setVisible(true);
            return password.length() == 0 ? null : password.toString();
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            password.setLength(0);
            password.append(new String(passwordField.getPassword()));
            preferences.put(PASSWORD, savePassword.isSelected() ? password.toString() : "");
            dialog.setVisible(false);
        }
    }


}
