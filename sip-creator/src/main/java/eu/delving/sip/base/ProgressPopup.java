/*
 * Copyright 2011 DELVING BV
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.sip.base;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Provide a popped-up progress bar
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class ProgressPopup implements ProgressListener {
    private BoundedRangeModel boundedRangeModel = new DefaultBoundedRangeModel();
    private JProgressBar progressBar = new JProgressBar(boundedRangeModel);
    private JLabel messageLabel = new JLabel("<html>Progress</html>");
    private String progressMessage, indeterminateMessage;
    private JDialog dialog;
    private long lastProgress;
    private volatile boolean cancel;
    private List<End> ends = new ArrayList<End>();
    private Timer showTimer = new Timer(200, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (!cancel) dialog.setVisible(true);
        }
    });

    public ProgressPopup(Component parent, String title) {
        this.showTimer.setRepeats(false);
        this.dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), title, Dialog.ModalityType.APPLICATION_MODAL);
        JButton cancelButton = new JButton("Cancel");
        JPanel bp = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bp.add(cancelButton);
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                cancel = true;
                showTimer.stop();
                dialog.setVisible(false);
            }
        });
        JPanel p = new JPanel(new GridLayout(0, 1));
        p.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        p.add(messageLabel);
        p.add(progressBar);
        p.add(bp);
        this.dialog.getContentPane().add(p);
        Dimension all = parent.getSize();
        this.dialog.setLocation(all.width / 4, 100);
        this.dialog.setSize(all.width / 2, 150);
    }

    @Override
    public void setProgressMessage(String message) {
        this.progressMessage = message;
    }

    @Override
    public void setIndeterminateMessage(String message) {
        this.indeterminateMessage = message;
    }

    @Override
    public void setProgressString(final String message) {
        Exec.swing(new Runnable() {
            @Override
            public void run() {
                if (!progressBar.isStringPainted()) progressBar.setStringPainted(true);
                progressBar.setString(message);
            }
        });
    }

    @Override
    public void prepareFor(final int total) {
        Exec.swingAny(new Runnable() {
            @Override
            public void run() {
                if (total < 0) {
                    boundedRangeModel.setMaximum(100);
                    progressBar.setIndeterminate(true);
                    messageLabel.setText(indeterminateMessage);
                }
                else {
                    boundedRangeModel.setMaximum(total);
                    progressBar.setIndeterminate(false);
                    messageLabel.setText(progressMessage);
                }
                showTimer.start();
            }
        });
    }

    @Override
    public boolean setProgress(final int progress) {
        if (System.currentTimeMillis() > lastProgress + PATIENCE) { // not too many events
            Exec.swing(new Runnable() {
                @Override
                public void run() {
                    boundedRangeModel.setValue(progress);
                }
            });
            lastProgress = System.currentTimeMillis();
        }
        return !cancel;
    }

    @Override
    public void finished(final boolean success) {
        Exec.swing(new Runnable() {
            @Override
            public void run() {
                showTimer.stop();
                if (dialog.isVisible()) {
                    dialog.setVisible(false);
                }
                for (End end : ends) end.finished(success);
            }
        });
    }

    @Override
    public void onFinished(End end) {
        this.ends.add(end);
    }
}