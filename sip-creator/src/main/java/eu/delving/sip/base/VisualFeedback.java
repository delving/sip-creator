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

import eu.delving.sip.ProgressListener;
import eu.delving.sip.model.Feedback;
import eu.delving.sip.model.SipModel;
import org.apache.log4j.Logger;

import javax.swing.AbstractListModel;
import javax.swing.JDesktopPane;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JToggleButton;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

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
    private Logger log = Logger.getLogger(getClass());
    private JToggleButton toggle = new JToggleButton(String.format(ONE_LINE, FEEDBACK));
    private LogListModel listModel = new LogListModel();
    private JList list = new JList(listModel);
    private LogFrame logFrame;
    private JDesktopPane desktop;

    public VisualFeedback(JDesktopPane desktop) {
        this.desktop = desktop;
    }

    public void setSipModel(SipModel sipModel) {
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
        addToList(message);
        log.info(message);
    }

    @Override
    public void alert(String message) {
        addToList(message);
        log.warn(message);
        inYourFace(message);
    }

    @Override
    public void alert(String message, Exception exception) {
        addToList(message);
        log.warn(message, exception);
        inYourFace(message);
    }

    @Override
    public ProgressListener progressListener(Component parent, String title, String message) {
        ProgressMonitor progressMonitor = new ProgressMonitor(SwingUtilities.getRoot(parent), title, message, 0, 100);
        return new ProgressAdapter(progressMonitor);
    }

    private void addToList(final String message) {
        Exec.swing(new Runnable() {
            @Override
            public void run() {
                listModel.add(message);
                list.ensureIndexIsVisible(listModel.getSize() - 1);
            }
        });
    }

    private void inYourFace(final String message) {
        Exec.swing(new Runnable() {
            @Override
            public void run() {
                JOptionPane.showMessageDialog(null, String.format("<html><h3>%s</h3>", message));
            }
        });
    }

    private class LogFrame extends FrameBase {
        private final Dimension SIZE = new Dimension(600, 400);

        public LogFrame(JDesktopPane desktop, SipModel sipModel) {
            super(desktop, sipModel, "Feedback", false);
        }
        @Override
        protected void buildContent(Container content) {
            content.add(scroll(list), BorderLayout.CENTER);
        }

        @Override
        protected void refresh() {
        }

        public void openAtPosition() {
            setLocation(desktopPane.getSize().width - SIZE.width + 8, desktopPane.getSize().height - SIZE.height + 16);
            setSize(SIZE);
            openFrame(false);
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
                    toggle.setText(String.format(ONE_LINE, lines.get(0)));
                    break;
                case 2:
                    toggle.setText(String.format(TWO_LINES, lines.get(0), lines.get(1)));
                    break;
                default:
                    int first = lines.size() - 3;
                    toggle.setText(String.format(THREE_LINES, lines.get(first), lines.get(first + 1), lines.get(first + 2)));
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

        @Override
        public int getSize() {
            return lines.size();
        }

        @Override
        public Object getElementAt(int i) {
            return lines.get(i);
        }
    }
}
