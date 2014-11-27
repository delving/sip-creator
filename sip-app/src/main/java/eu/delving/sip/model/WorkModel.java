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

import eu.delving.sip.base.CancelException;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A model of all the work that is being done in background threads at any time.  The contents of the list
 * are shown to the outside world through a periodically updated list model of sorted entries.  Work can be
 * shown or not, depending on its type, and long term jobs also have an associated progress listener which
 * allows for cancellation as well as reporting of progress.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class WorkModel {
    private static final int REFRESH_RATE = 666;
    private ExecutorService executor = Executors.newCachedThreadPool();
    private List<JobContext> jobContexts = new CopyOnWriteArrayList<JobContext>();
    private JobListModel jobListModel = new JobListModel();
    private Feedback feedback;

    public interface ProgressIndicator {
        void cancel();

        String getProgressString();
    }

    public WorkModel(final Feedback feedback) {
        this.feedback = feedback;
        this.executor = new ThreadPoolExecutor(
                0, Integer.MAX_VALUE,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>()
        ) {
            @Override
            public void afterExecute(Runnable runnable, Throwable throwable) {
                super.afterExecute(runnable, throwable);
                if (throwable == null) return;
                feedback.alert("Exception: " + throwable.toString(), throwable);
            }
        };
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

    public void shutdown() {
        executor.shutdown();
    }

    public boolean isEmpty() {
        return jobContexts.isEmpty();
    }

    public boolean isDataSetBusy(String dataSetSpec) {
        for (JobContext context : jobContexts) {
            String dataSet = context.getDataSet();
            if (dataSet == null) continue;
            Work work = context.queue.peek();
            if (work == null) continue;
            if (!(work instanceof Work.LongTermWork)) continue;
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
        Work.DataSetPrefixWork w = (Work.DataSetPrefixWork) work;
        DataSet dataSet = w.getDataSet();
        String prefix = w.getPrefix();
        if (dataSet != null && prefix == null) { // todo: what's with prefix here?
            for (JobContext context : jobContexts) {
                String dataSetSpec = context.getDataSet();
                String contextPrefix = context.getPrefix();
                if (dataSetSpec != null && contextPrefix != null &&
                        dataSetSpec.equals(dataSet.getSpec()) && contextPrefix.equals(prefix)) {
                    context.add(work);
                    work = null;
                    break;
                }
            }
        }
        justDoIt(work);
    }

    private void dataSet(Work work) {
        Work.DataSetWork w = (Work.DataSetWork) work;
        DataSet dataSet = w.getDataSet();
        if (dataSet != null) {
            for (JobContext context : jobContexts) {
                String dataSetSpec = context.getDataSet();
                if (context.getPrefix() != null) continue;
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

    public ListModel<JobContext> getListModel() {
        return jobListModel;
    }

    private class JobListModel extends AbstractListModel<JobContext> implements ActionListener {
        private List<JobContext> snapshot = new ArrayList<JobContext>();

        @Override
        public int getSize() {
            return snapshot.size();
        }

        @Override
        public JobContext getElementAt(int index) {
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
                else if (context.getWork().getJob() != Work.Job.CHECK_STATE) {
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

        public Work getWork() {
            return queue.peek();
        }

        public boolean isDone() {
            if (executor.isShutdown() || queue.isEmpty()) return true;
            if (!(future.isDone() || future.isCancelled())) return false;
            queue.remove();
            if (queue.isEmpty()) return true;
            launch();
            return false;
        }

        public String getDataSet() {
            final Work work = queue.peek();
            if (work == null) return null;
            switch (work.getJob().getKind()) {
                case NETWORK_DATA_SET:
                case DATA_SET:
                case DATA_SET_PREFIX:
                    DataSet dataSet = ((Work.DataSetWork) work).getDataSet();
                    return dataSet == null ? null : dataSet.getSpec();
                default:
                    return null;
            }
        }

        public String getPrefix() {
            final Work work = queue.peek();
            if (work == null) return null;
            switch (work.getJob().getKind()) {
                case DATA_SET_PREFIX:
                    return ((Work.DataSetPrefixWork) work).getPrefix();
                default:
                    return null;
            }
        }

        public boolean isNetwork() {
            final Work work = queue.peek();
            if (work == null) return false;
            switch (work.getJob().getKind()) {
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
                final Work existingWork = queue.peek();
                if (existingWork == null) return;
                if (!queue.isEmpty() && work.getClass() == existingWork.getClass() && !isDone()) {
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
            if (executor.isShutdown()) return;
            final Work work = queue.peek();
            if (work == null) return;
            if (work instanceof Work.LongTermWork) {
                progressImpl = new ProgressImpl(feedback);
                ((Work.LongTermWork) work).setProgressListener(progressImpl);
            }
            else {
                progressImpl = null;
            }
            this.future = executor.submit(work);
            this.start = new Date();
        }

        @Override
        public String toString() {
            final Work work = queue.peek();
            if (work == null) return "empty";
            if (work instanceof Work.DataSetPrefixWork) {
                Work.DataSetPrefixWork dsp = (Work.DataSetPrefixWork) work;
                return String.format("%s [%s/%s]", work.getJob(), dsp.getDataSet(), dsp.getPrefix());
            }
            else if (work instanceof Work.DataSetWork) {
                Work.DataSetWork ds = (Work.DataSetWork) work;
                return String.format("%s [%s]", work.getJob(), ds.getDataSet());
            }
            else {
                return work.getJob().toString();
            }
        }
    }

    private static class ProgressImpl implements ProgressListener, ProgressIndicator {
        private Feedback feedback;
        private String progressMessage;
        private int current, maximum;
        private boolean cancelled;
        private TimeEstimator timeEstimator;

        private ProgressImpl(Feedback feedback) {
            this.feedback = feedback;
        }

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
        public void setProgress(int progress) throws CancelException {
            this.current = progress;
            if (cancelled) throw new CancelException();
        }

        @Override
        public Feedback getFeedback() {
            return feedback;
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
        public String getProgressString() {
            String progress = String.format("%d", current);
            if (maximum > 0) {
                progress += String.format("/%d (%s)", maximum, timeEstimator.getMessage(current));
            }
            if (progressMessage != null) {
                progress += " " + progressMessage;
            }
            return progress;
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
                return "";
            }
            else if (proportionComplete < 0.01) {
                return "estimating time";
            }
            else {
                long millisElapsed = now - startTime;
                double perMilli = (double) current / millisElapsed;
                long totalMills = (long) (maximum / perMilli);
                return getTimeString(totalMills - millisElapsed);
            }
        }

        private String getTimeString(long remaining) {
            int hours = (int) (remaining / ONE_HOUR);
            long minuteMillis = remaining - ONE_HOUR * hours;
            int minutes = (int) (minuteMillis / ONE_MINUTE);
            long secondMillis = minuteMillis - minutes * ONE_MINUTE;
            int seconds = (int) (secondMillis / ONE_SECOND);
            if (hours > 0) {
                return String.format("%d hr %d min", hours, minutes);
            }
            else if (minutes > 0) {
                if (minutes > 5) {
                    return String.format("%d min", minutes);
                }
                else {
                    return String.format("%d min %d sec", minutes, seconds);
                }
            }
            else if (seconds > 5) {
                return String.format("%d sec", seconds);
            }
            else {
                return "a few seconds";
            }
        }
    }
}
