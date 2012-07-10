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

package eu.delving.sip.model;

import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.concurrent.*;

/**
 * A model of all the work that is being done in background threads at any time.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class WorkModel {
    private static final int REFRESH_RATE = 1000;
    private ExecutorService executor = Executors.newCachedThreadPool();
    private List<JobContext> jobContexts = new CopyOnWriteArrayList<JobContext>();
    private JobListModel jobListModel = new JobListModel();

    public WorkModel() {
        Timer tick = new Timer(REFRESH_RATE, jobListModel);
        tick.setRepeats(true);
        tick.start();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(30);
                        for (JobContext context : jobContexts) {
                            if (context.isDone()) jobContexts.remove(context);
                        }
                    }
                    catch (InterruptedException e) {
                        break;
                    }
                }
            }
        });
    }

    public void exec(Work work) {
        switch (work.getJob().getKind()) {
            case SILENT:
                executor.execute(work);
                break;
            case NETWORK:
                network(work);
                break;
            case NETWORK_DATA_SET:
                dataSet(work);
                break;
            case DATA_SET:
                dataSet(work);
                break;
            case DATA_SET_PREFIX:
                dataSetPrefix(work);
                break;
            default:
                throw new RuntimeException();
        }
    }

    private void dataSetPrefix(Work work) {
        dataSet(work); // for now
    }

    private void dataSet(Work work) {
        Work.DataSetWork w = (Work.DataSetWork) work;
        DataSet dataSet = w.getDataSet();
        if (dataSet != null) {
            for (JobContext context : jobContexts) {
                String dataSetSpec = context.getDataSet();
                if (dataSetSpec != null && dataSetSpec.equals(dataSet.getSpec())) {
                    context.add(work);
                    work = null;
                    break;
                }
            }
        }
        justDoIt(work);
    }

    private void network(Work work) {
        for (JobContext context : jobContexts) {
            if (context.isNetwork()) {
                context.add(work);
                work = null;
                break;
            }
        }
        justDoIt(work);
    }

    private void justDoIt(Work work) {
        if (work == null) return;
        jobContexts.add(new JobContext(work));
    }

    public ListModel getListModel() {
        return jobListModel;
    }

    private class JobListModel extends AbstractListModel implements ActionListener {
        private List<JobContext> snapshot = new ArrayList<JobContext>();

        @Override
        public int getSize() {
            return snapshot.size();
        }

        @Override
        public Object getElementAt(int index) {
            return snapshot.get(index);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (!snapshot.isEmpty()) {
                int size = getSize();
                snapshot.clear();
                fireIntervalRemoved(this, 0, size);
            }
            for (JobContext context : jobContexts) {
                if (context.isDone()) {
                    jobContexts.remove(context);
                }
                else {
                    snapshot.add(context);
                }
            }
            if (!snapshot.isEmpty()) {
                Collections.sort(snapshot);
                fireIntervalAdded(this, 0, snapshot.size());
            }
        }
    }

    public class JobContext implements Comparable<JobContext> {
        private Date start;
        private Future<?> future;
        private Queue<Work> queue = new ConcurrentLinkedQueue<Work>();
        private SimpleProgress progress;

        public JobContext(Work work) {
            this.queue.add(work);
            launch();
        }

        public Work.Job getJob() {
            return job();
        }

        public Date getStart() {
            return start;
        }

        public boolean isDone() {
            if (isEmpty()) return true;
            if (!future.isDone()) return false;
//          todo:  future.isCancelled()
            if (progress != null) progress.finished(true);
            queue.remove();
            if (isEmpty()) return true;
            launch();
            return false;
        }

        public String getDataSet() {
            if (isEmpty()) return null;
            switch (kind()) {
                case NETWORK_DATA_SET:
                case DATA_SET:
                case DATA_SET_PREFIX:
                    DataSet dataSet = ((Work.DataSetWork) work()).getDataSet();
                    return dataSet == null ? null : dataSet.getSpec();
                default:
                    return null;
            }
        }

        public boolean isNetwork() {
            if (isEmpty()) return false;
            switch (kind()) {
                case NETWORK:
                    return true;
                default:
                    return false;
            }
        }

        public String getFullProgress() {
            return progress == null ? null : progress.toFullString();
        }

        public String getMiniProgress() {
            return progress == null ? " - " : progress.toString();
        }

        public void add(Work work) {
            queue.add(work);
        }

        @Override
        public int compareTo(JobContext o) {
            return start.compareTo(o.start);
        }

        private void launch() {
            this.future = executor.submit(work());
            this.start = new Date();
            if (work() instanceof Work.LongTermWork) {
                ((Work.LongTermWork) work()).setProgressListener(progress = new SimpleProgress());
            }
            else {
                progress = null;
            }
        }

        private Work.Kind kind() {
            return job().getKind();
        }

        private Work.Job job() {
            return work().getJob();
        }

        private Work work() {
            return queue.peek();
        }

        private boolean isEmpty() {
            return queue.isEmpty();
        }

        @Override
        public String toString() {
            return (isEmpty() ? "empty" : job().toString()) + start.toString();
        }
    }

    private static class SimpleProgress implements ProgressListener {
        private String progressMessage, indeterminateMessage;
        private int current, maximum;
        private boolean finished, success;

        @Override
        public void setProgressMessage(String message) {
            this.progressMessage = message;
        }

        @Override
        public void setIndeterminateMessage(String message) {
            this.indeterminateMessage = message;
        }

        @Override
        public void prepareFor(int total) {
            this.maximum = total;
        }

        @Override
        public boolean setProgress(int progress) {
            this.current = progress;
            return true; // todo: false if you want to cancel
        }

        @Override
        public void finished(boolean success) {
            this.finished = true;
            this.success = success;
        }

        public String toFullString() {
            String message = progressMessage;
            if (indeterminateMessage != null && current == 0) message = indeterminateMessage;
            if (maximum == 0) {
                return String.format("%d : %s", current, message);
            }
            else {
                return String.format("%d/%d : %s", current, maximum, message);
            }
        }

        @Override
        public String toString() {
            if (maximum == 0) {
                return String.format("%d", current);
            }
            else {
                return String.format("%d/%d", current, maximum);
            }
        }

    }
}
