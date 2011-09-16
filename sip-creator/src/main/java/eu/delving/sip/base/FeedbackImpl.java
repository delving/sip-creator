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
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Give the user feedback in different ways
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class FeedbackImpl implements Feedback {
    private Logger log = Logger.getLogger(getClass());
    private LogListModel listModel = new LogListModel();
    private JList list = new JList(listModel);

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
        private Timer timer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                closeFrame();
            }
        });

        public LogFrame(JDesktopPane desktop, SipModel sipModel, String title) {
            super(desktop, sipModel, title, false);
        }

        public void log(final String line) {
        }

        @Override
        protected void buildContent(Container content) {
            content.add(scroll(list), BorderLayout.CENTER);
            pack();
        }

        @Override
        protected void refresh() {
        }

    }

    private class LogListModel extends AbstractListModel {
        private List<String> lines = new ArrayList<String>();

        public void clear() {
            int size = getSize();
            lines.clear();
            fireIntervalRemoved(this, 0, size);
        }

        public void add(String line) {
            lines.add(line);
            fireIntervalAdded(this, getSize() - 1, getSize());
            // todo: start reducing if it gets big
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
