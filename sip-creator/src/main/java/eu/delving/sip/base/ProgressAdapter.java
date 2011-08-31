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

import javax.swing.ProgressMonitor;

/**
 * Adapt a progress listener to a Swing Progress Monitor
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public abstract class ProgressAdapter implements ProgressListener {
    private long lastProgress;
    private ProgressMonitor progressMonitor;

    public ProgressAdapter(ProgressMonitor progressMonitor) {
        this.progressMonitor = progressMonitor;
    }

    @Override
    public void prepareFor(final int total) {
        Exec.swing(new Runnable() {
            @Override
            public void run() {
                progressMonitor.setProgress(0);
                progressMonitor.setMaximum(total);
            }
        });
    }

    @Override
    public boolean setProgress(final int progress) {
        if (System.currentTimeMillis() > lastProgress + PATIENCE) { // not too many events
            Exec.swing(new Runnable() {
                @Override
                public void run() {
                    progressMonitor.setProgress(progress);
                }
            });
            lastProgress = System.currentTimeMillis();
        }
        boolean cancelled = progressMonitor.isCanceled();
        if (cancelled) {
            Exec.swing(new Runnable() {
                @Override
                public void run() {
                    progressMonitor.close();
                    swingFinished(false);
                }
            });
        }
        return !cancelled;
    }

    @Override
    public void finished(final boolean success) {
        Exec.swing(new Runnable() {
            @Override
            public void run() {
                progressMonitor.close();
                swingFinished(success);
            }
        });
    }

    public abstract void swingFinished(boolean success);
}