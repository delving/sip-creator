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

import eu.delving.sip.frames.ProgressPopup;
import eu.delving.sip.model.Feedback;
import eu.delving.sip.model.SipModel;
import org.apache.log4j.Logger;

import javax.swing.*;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.lang.reflect.InvocationTargetException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static javax.swing.JOptionPane.*;

/**
 * Give the user feedback in different ways
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class VisualFeedback implements Feedback {
    private static final String FEEDBACK = "Feedback";
    private static final String ONE_LINE = "<html><font size=-2><i>%s</i></font>";
    private static final String TWO_LINES = "<html><font size=-2><i>%s<br>%s</i></font>";
    private static final String THREE_LINES = "<html><font size=-2><i>%s<br>%s<br>%s</i></font>";
    private static final DateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("hh:mm:ss");
    private Logger log = Logger.getLogger(getClass());
    private JToggleButton toggle = new JToggleButton(String.format(ONE_LINE, FEEDBACK));
    private LogListModel listModel = new LogListModel();
    private JList list = new JList(listModel);
    private LogFrame logFrame;
    private JDesktopPane desktop;
    private ProgressPopup progressPopup;
    private SipModel sipModel;

    public VisualFeedback(JDesktopPane desktop) {
        this.desktop = desktop;
        toggle.setHorizontalAlignment(JButton.LEFT);
    }

    public void setSipModel(SipModel sipModel) {
        this.sipModel = sipModel;
        this.logFrame = new LogFrame(desktop, sipModel);
        toggle.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent itemEvent) {
                if (itemEvent.getStateChange() == ItemEvent.SELECTED) {
                    logFrame.openAtPosition();
                }
                else {
                    logFrame.closeFrame();
                }
            }
        });
    }

    public JToggleButton getToggle() {
        return toggle;
    }

    @Override
    public void say(final String message) {
        sipModel.exec(new Swing() {
            @Override
            public void run() {
                addToList(message);
            }
        });
        log.info(message);
    }

    @Override
    public void alert(final String message) {
        if (progressPopup != null) progressPopup.finished(false);
        log.warn(message);
        execWait(new Runnable() {
            @Override
            public void run() {
                addToList(message);
                inYourFace(message, null);
            }
        });
    }

    @Override
    public void alert(final String message, final Exception exception) {
        if (progressPopup != null) progressPopup.finished(false);
        log.warn(message, exception);
        execWait(new Runnable() {
            @Override
            public void run() {
                addToList(message);
                inYourFace(message, exception.getMessage());
            }
        });
    }

    @Override
    public ProgressListener progressListener(String title) {
        if (progressPopup != null) progressPopup.finished(false); // destroy the previous one
        progressPopup = new ProgressPopup(desktop, title);
        progressPopup.onFinished(new ProgressListener.End() {
            @Override
            public void finished(ProgressListener progressListener, boolean success) {
                if (progressPopup == progressListener) progressPopup = null; // only set to null if it's the current one
            }
        });
        return progressPopup;
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
        String html = String.format("<html><h3>%s</h3>", message);
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

    private void addToList(final String message) {
        listModel.add(String.format("%s - %s", TIMESTAMP_FORMAT.format(new Date()), message));
        list.ensureIndexIsVisible(listModel.getSize() - 1);
    }

    private static String sanitizeHtml(String string) {
        if (string == null) return null;
        return string.replaceAll("<", "&lt;").replaceAll("&", "&amp;");
    }

    private class LogFrame extends FrameBase {

        public LogFrame(JDesktopPane desktop, SipModel sipModel) {
            super(Which.LOG, desktop, sipModel, "Feedback");
            addInternalFrameListener(new InternalFrameAdapter() {
                @Override
                public void internalFrameClosing(InternalFrameEvent internalFrameEvent) {
                    toggle.setSelected(false);
                }
            });
        }

        @Override
        protected void buildContent(Container content) {
            content.add(SwingHelper.scrollV(list), BorderLayout.CENTER);
        }

        public void openAtPosition() {
            int width = desktopPane.getSize().width * 2 / 3;
            setLocation(desktopPane.getSize().width - width + 8, 16);
            setSize(width, desktopPane.getSize().height);
            openFrame();
        }
    }

    private class LogListModel extends AbstractListModel {
        private static final int CHOP = 100;
        private static final int MAX = 1000;
        private List<String> lines = new ArrayList<String>();

        public void clear() {
            int size = getSize();
            lines.clear();
            fireIntervalRemoved(this, 0, size);
        }

        public void add(String line) {
            lines.add(line);
            fireIntervalAdded(this, getSize() - 1, getSize());
            switch (lines.size()) {
                case 0:
                    toggle.setText(String.format(ONE_LINE, FEEDBACK));
                    break;
                case 1:
                    toggle.setText(String.format(ONE_LINE, shortEnough(lines.get(0))));
                    break;
                case 2:
                    toggle.setText(String.format(TWO_LINES, shortEnough(lines.get(0)), shortEnough(lines.get(1))));
                    break;
                default:
                    int first = lines.size() - 3;
                    toggle.setText(String.format(THREE_LINES, shortEnough(lines.get(first)), shortEnough(lines.get(first + 1)), shortEnough(lines.get(first + 2))));
                    break;
            }
            if (lines.size() > MAX) {
                List<String> fresh = new ArrayList<String>(MAX - CHOP);
                for (int walk = CHOP; walk < lines.size(); walk++) {
                    fresh.add(lines.get(walk));
                }
                lines = fresh;
                fireIntervalRemoved(this, 0, CHOP);
            }
        }

        private String shortEnough(String s) {
            return s.length() > 80 ? s.substring(0, 77) + "..." : s;
        }

        @Override
        public int getSize() {
            return lines.size();
        }

        @Override
        public Object getElementAt(int i) {
            return lines.get(i);
        }
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
