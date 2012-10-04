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

import javax.swing.AbstractListModel;
import javax.swing.ListModel;
import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.concurrent.*;

/**
 * A model of all the work that is being done in background threads at any time.  The contents of the list
 * are shown to the outside world through a periodically updated list model of sorted entries.  Work can be
 * shown or not, depending on its type, and long term jobs also have an associated progress listener which
 * allows for cancellation as well as reporting of progress.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class WorkModel {
    private static final int REFRESH_RATE = 1000;
    private ExecutorService executor = Executors.newCachedThreadPool();
    private List<JobContext> jobContexts = new CopyOnWriteArrayList<JobContext>();
    private JobListModel jobListModel = new JobListModel();
    private Feedback feedback;

    public interface ProgressIndicator {
        void cancel();

        String getString(boolean full);
    }

    public WorkModel(Feedback feedback) {
        this.feedback = feedback;
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

    public boolean isEmpty() {
        return jobContexts.isEmpty();
    }

    public boolean isDataSetBusy(String dataSetSpec) {
        for (JobContext context : jobContexts) {
            String dataSet = context.getDataSet();
            if (dataSet == null) continue;
            if (!(context.getWork() instanceof Work.LongTermWork)) continue;
            if (dataSetSpec.equals(dataSet)) return true;
        }
        return false;
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
        private ProgressImpl progressImpl;

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
                    DataSet dataSet = ((Work.DataSetWork) getWork()).getDataSet();
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

        public ProgressIndicator getProgressIndicator() {
            return progressImpl;
        }

        public void add(Work work) {
            if (work instanceof Work.LongTermWork) {
                if (!isEmpty() && work.getClass() == getWork().getClass() && !isDone()) {
                    feedback.alert(this + " busy");
                    return;
                }
            }
            queue.add(work);
        }

        @Override
        public int compareTo(JobContext o) {
            return start.compareTo(o.start);
        }

        private void launch() {
            if (getWork() instanceof Work.LongTermWork) {
                ((Work.LongTermWork) getWork()).setProgressListener(progressImpl = new ProgressImpl());
            }
            else {
                progressImpl = null;
            }
            this.future = executor.submit(getWork());
            this.start = new Date();
        }

        private Work.Kind kind() {
            return job().getKind();
        }

        private Work.Job job() {
            return getWork().getJob();
        }

        public Work getWork() {
            return queue.peek();
        }

        private boolean isEmpty() {
            return queue.isEmpty();
        }

        @Override
        public String toString() {
            return isEmpty() ? "empty" : job().toString();
        }
    }

    private static class ProgressImpl implements ProgressListener, ProgressIndicator {
        private String progressMessage;
        private int current, maximum;
        private boolean cancelled;
        private TimeEstimator timeEstimator;

        @Override
        public void setProgressMessage(String message) {
            this.progressMessage = message;
        }

        @Override
        public void prepareFor(int maximum) {
            if (maximum <= 0) return;
            this.maximum = maximum;
            this.timeEstimator = new TimeEstimator(maximum);
        }

        @Override
        public boolean setProgress(int progress) {
            this.current = progress;
            return !cancelled;
        }

        @Override
        public String toString() {
            return "SimpleProgress";
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public String getString(boolean full) {
            if (full) {
                if (maximum == 0) {
                    return String.format("%d : %s", current, progressMessage);
                }
                else {
                    return String.format("%d/%d : %s %s", current, maximum, progressMessage, timeEstimator.getMessage(current));
                }
            }
            else {
                if (maximum == 0) {
                    return String.format("%d", current);
                }
                else {
                    return String.format("%d/%d %s", current, maximum, timeEstimator.getMessage(current));
                }
            }
        }
    }

    private static class TimeEstimator {
        public static final int ONE_SECOND = 1000;
        public static final int ONE_MINUTE = ONE_SECOND * 60;
        public static final int ONE_HOUR = ONE_MINUTE * 60;
        private int maximum;
        private long startTime;

        private TimeEstimator(int maximum) {
            this.maximum = maximum;
        }

        public String getMessage(int current) {
            long now = System.currentTimeMillis();
            double proportionComplete = (double) current / maximum;
            if (startTime == 0 || startTime == now) {
                startTime = now;
                return "time unknown";
            }
            else if (proportionComplete < 0.01) {
                return "estimating time";
            }
            else {
                long millisElapsed = now - startTime;
                double perMilli = (double) current / millisElapsed;
                long totalMills = (long) (maximum / perMilli);
                return getTimeString(totalMills - millisElapsed) + " to go";
            }
        }

        private String getTimeString(long remaining) {
            int hours = (int) (remaining / ONE_HOUR);
            long minuteMillis = remaining - ONE_HOUR * hours;
            int minutes = (int) (minuteMillis / ONE_MINUTE);
            long secondMillis = minuteMillis - minutes * ONE_MINUTE;
            int seconds = (int) (secondMillis / ONE_SECOND);
            if (hours > 0) {
                return String.format("%d hour%s %d minutes", hours, hours > 1 ? "s" : "", minutes);
            }
            else if (minutes > 0) {
                if (minutes > 5) {
                    return String.format("%d minutes", minutes);
                }
                else {
                    return String.format("%d minute%s %d seconds", minutes, minutes > 1 ? "s" : "", seconds);
                }
            }
            else if (seconds > 15) {
                return String.format("%d seconds", seconds);
            }
            else {
                return "a few seconds";
            }
        }
    }
}
