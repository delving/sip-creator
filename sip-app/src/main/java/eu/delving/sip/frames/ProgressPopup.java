/*
 * Copyright 2011, 2012 Delving BV
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

package eu.delving.sip.frames;

import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.ProgressListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import static eu.delving.sip.base.FrameBase.Which.PROGRESS;

/**
 * Provide a popped-up progress bar
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class ProgressPopup extends FrameBase implements ProgressListener {
    private BoundedRangeModel boundedRangeModel = new DefaultBoundedRangeModel();
    private JProgressBar progressBar = new JProgressBar(boundedRangeModel);
    private JLabel messageLabel = new JLabel("<html>Progress</html>");
    private String progressMessage, indeterminateMessage;
    private long lastProgress;
    private volatile boolean cancel;
    private List<End> ends = new ArrayList<End>();
    private Timer showTimer = new Timer(200, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            if (!cancel) openFrame();
        }
    });

    public ProgressPopup(JDesktopPane desktop, String title) {
        super(PROGRESS, desktop, null, title, true);
        this.showTimer.setRepeats(false);
        final Dimension all = desktop.getSize();
        setPlacement(new Placement() {
            @Override
            public Point getLocation() {
                return new Point(all.width/8, 200);
            }

            @Override
            public Dimension getSize() {
                return new Dimension(all.width*6/8, 250);
            }
        });
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
        Exec.swingAny(new Runnable() {
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
            Exec.swingAny(new Runnable() {
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
        Exec.swingAny(new Runnable() {
            @Override
            public void run() {
                cancel = true;
                showTimer.stop();
                closeFrame();
                for (End end : ends) end.finished(ProgressPopup.this, success);
            }
        });
    }

    @Override
    public void onFinished(End end) {
        this.ends.add(end);
    }

    @Override
    protected void buildContent(Container content) {
        JButton cancelButton = new JButton("Cancel");
        JPanel bp = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bp.add(cancelButton);
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                cancel = true;
                showTimer.stop();
                setVisible(false);
            }
        });
        JPanel p = new JPanel(new GridLayout(0, 1));
        p.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        p.add(messageLabel);
        p.add(progressBar);
        p.add(bp);
        content.add(p);
    }
}