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

import eu.delving.sip.ProgressListener;

import javax.swing.BorderFactory;
import javax.swing.BoundedRangeModel;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Provide a popped-up progress bar
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class ProgressPopup implements ProgressListener {
    private BoundedRangeModel boundedRangeModel = new DefaultBoundedRangeModel();
    private JDialog dialog;
    private long lastProgress;
    private volatile boolean cancel;
    private End end;
    private Timer showTimer = new Timer(500, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (!cancel) dialog.setVisible(true);
        }
    });

    public ProgressPopup(Component parent, String title, String message) {
        JProgressBar bar = new JProgressBar(boundedRangeModel);
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
        p.add(new JLabel(message));
        p.add(bar);
        p.add(bp);
        this.dialog.getContentPane().add(p);
        Dimension all = parent.getSize();
        this.dialog.setLocation(all.width / 4, 100);
        this.dialog.setSize(all.width / 2, 150);
    }

    @Override
    public void prepareFor(final int total) {
        Exec.swing(new Runnable() {
            @Override
            public void run() {
                boundedRangeModel.setMaximum(total);
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
        if (cancel) {
            Exec.swing(new Runnable() {
                @Override
                public void run() {
                    dialog.setVisible(false);
                    if (end != null) end.finished(false);
                }
            });
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
                if (end != null) end.finished(success);
            }
        });
    }

    @Override
    public void onFinished(End end) {
        this.end = end;
    }
}