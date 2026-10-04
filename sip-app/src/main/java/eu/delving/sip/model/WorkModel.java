/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.sip.model;

import eu.delving.sip.base.CancelException;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A model of all the work that is being done in background threads at any time.  The contents of the list
 * are shown to the outside world through a periodically updated list model of sorted entries.  Work can be
 * shown or not, depending on its type, and long term jobs also have an associated progress listener which
 * allows for cancellation as well as reporting of progress.
 *
 * Jobs are queued per (dataset, lane), see {@link Work.Lane}: jobs with the same key run one after the other
 * in one JobContext, jobs with different keys run concurrently.
 *
 */

public class WorkModel {
    private static final int REFRESH_RATE = 666;
    private ExecutorService executor = Executors.newCachedThreadPool();
    private List<JobContext> jobContexts = new CopyOnWriteArrayList<JobContext>();
    private Map<LaneKey, JobContext> laneContexts = new ConcurrentHashMap<LaneKey, JobContext>();
    private JobListModel jobListModel = new JobListModel();
    private Timer tick;
    private Feedback feedback;
    private Supplier<DataSet> currentDataSet;

    public interface ProgressIndicator {
        void cancel();

        String getProgressString();
    }

    /**
     * @param currentDataSet the dataset that is open right now, or null. SILENT jobs that do not name a dataset
     *                       (most of them do not implement DataSetWork) are queued in the EDIT lane of this
     *                       dataset, because that is the dataset whose models they touch.
     */
    public WorkModel(final Feedback feedback, Supplier<DataSet> currentDataSet) {
        this.feedback = feedback;
        this.currentDataSet = currentDataSet;
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
        tick = new Timer(REFRESH_RATE, jobListModel);
        tick.setRepeats(true);
        tick.start();
        executor.execute(new Runnable() { // the sole driver of the job contexts; the timer only snapshots them
            @Override
            public void run() {
                while (!executor.isShutdown()) {
                    try {
                        Thread.sleep(30);
                        for (JobContext context : jobContexts) {
                            if (context.doWork()) retire(context);
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
        tick.stop();
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
        LaneKey key = LaneKey.of(work, currentDataSet);
        if (key == null) {
            justDoIt(work);
            return;
        }
        JobContext running = laneContexts.get(key);
        if (running != null && running.isBusyWith(work)) {
            feedback.alert(running + " busy"); // outside the map lock: the alert blocks on a modal dialog
            return;
        }
        laneContexts.compute(key, (laneKey, context) -> { // merge-or-create is atomic per (dataset, lane)
            if (context != null && context.add(work)) return context;
            JobContext fresh = new JobContext(work, laneKey);
            jobContexts.add(fresh);
            return fresh;
        });
    }

    private void justDoIt(Work work) {
        jobContexts.add(new JobContext(work, null));
    }

    private void retire(JobContext context) {
        jobContexts.remove(context);
        if (context.key != null) laneContexts.remove(context.key, context); // no-op if exec already replaced it
    }

    public ListModel<JobContext> getListModel() {
        return jobListModel;
    }

    /**
     * What a job queues behind: the same dataset in the same lane. NETWORK jobs share one key.
     */
    private static class LaneKey {
        private static final LaneKey NETWORK = new LaneKey("", Work.Lane.NETWORK);
        private final String dataSetSpec;
        private final Work.Lane lane;

        private LaneKey(String dataSetSpec, Work.Lane lane) {
            this.dataSetSpec = dataSetSpec;
            this.lane = lane;
        }

        // DataSetWork names its dataset; the many SILENT jobs that do not are keyed on the dataset that is open
        // when they are queued, which is the one whose models they touch. No dataset means nothing to queue behind.
        private static LaneKey of(Work work, Supplier<DataSet> currentDataSet) {
            Work.Lane lane = work.getJob().getLane();
            switch (lane) {
                case NETWORK:
                    return NETWORK;
                case NONE:
                    return null;
                default:
                    DataSet dataSet = work instanceof Work.DataSetWork
                            ? ((Work.DataSetWork) work).getDataSet()
                            : currentDataSet.get();
                    return dataSet == null ? null : new LaneKey(dataSet.getSpec(), lane);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LaneKey)) return false;
            LaneKey other = (LaneKey) o;
            return dataSetSpec.equals(other.dataSetSpec) && lane == other.lane;
        }

        @Override
        public int hashCode() {
            return 31 * dataSetSpec.hashCode() + lane.hashCode();
        }
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
        public void actionPerformed(ActionEvent e) { // snapshot only; the background loop in the constructor drives the contexts
            if (!snapshot.isEmpty()) {
                int size = getSize();
                snapshot.clear();
                fireIntervalRemoved(this, 0, size - 1);
            }
            for (JobContext context : jobContexts) {
                Work work = context.getWork();
                if (work != null && work.getJob() != Work.Job.CHECK_STATE) {
                    snapshot.add(context);
                }
            }
            if (!snapshot.isEmpty()) {
                Collections.sort(snapshot);
                fireIntervalAdded(this, 0, snapshot.size() - 1);
            }
        }
    }

    public class JobContext implements Comparable<JobContext> {
        private final LaneKey key;
        private volatile Date start;
        private volatile Future<?> future;
        private Queue<Work> queue = new ConcurrentLinkedQueue<Work>();
        private volatile ProgressImpl progressImpl;

        private JobContext(Work work, LaneKey key) {
            this.key = key;
            this.queue.add(work);
            launch();
        }

        public Work getWork() {
            return queue.peek();
        }

        public synchronized boolean doWork() { // synchronized against add: a drained context must not accept work
            if (executor.isShutdown() || queue.isEmpty()) return true;
            if (!(future.isDone() || future.isCancelled())) return false;
            queue.remove();
            if (queue.isEmpty()) return true;
            launch();
            return false;
        }

        public boolean isDone() {
            return executor.isShutdown() || queue.isEmpty();
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

        public ProgressIndicator getProgressIndicator() {
            return progressImpl;
        }

        // the busy guard for LongTermWork: a second Validate while one is running for this dataset is refused
        private boolean isBusyWith(Work work) {
            if (!(work instanceof Work.LongTermWork)) return false;
            final Work existingWork = queue.peek();
            return existingWork != null && work.getClass() == existingWork.getClass() && !isDone();
        }

        private synchronized boolean add(Work work) {
            if (isDone()) return false; // drained and about to be retired: exec starts a fresh context instead
            queue.add(work);
            return true;
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
        private volatile String progressMessage;
        private volatile int current, maximum;
        private volatile boolean cancelled;
        private volatile TimeEstimator timeEstimator;

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
