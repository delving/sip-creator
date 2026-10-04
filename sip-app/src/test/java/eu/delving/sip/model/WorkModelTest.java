/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 */

package eu.delving.sip.model;

import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.base.Work;
import eu.delving.sip.base.Work.Job;
import eu.delving.sip.base.Work.Lane;
import eu.delving.sip.files.DataSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lane assignment and per-(dataset, lane) ordering in WorkModel, using fake Work implementations
 * gated by latches so the tests never sleep for ordering.
 */
class WorkModelTest {
    private static final long WAIT_SECONDS = 5;
    private static final long MUST_NOT_HAPPEN_MILLIS = 300;

    private Feedback feedback;
    private DataSet dataSet;
    private volatile DataSet currentDataSet;
    private WorkModel workModel;

    @BeforeEach
    void setUp() {
        feedback = mock(Feedback.class);
        dataSet = mock(DataSet.class);
        when(dataSet.getSpec()).thenReturn("spec");
        workModel = new WorkModel(feedback, () -> currentDataSet);
    }

    @AfterEach
    void tearDown() {
        workModel.shutdown();
    }

    @Test
    void laneFollowsTheJob() {
        EnumSet<Job> batch = EnumSet.of(
                Job.PROCESS, Job.LOAD_REPORT, Job.CHECK_LINK, Job.LOAD_LINKS, Job.SAVE_LINKS,
                Job.GATHER_LINK_STATS, Job.GATHER_PRESENCE_STATS, Job.DELETE_CACHES
        );
        for (Job job : Job.values()) {
            Lane expected;
            if (batch.contains(job)) {
                expected = Lane.BATCH;
            }
            else if (job == Job.READ_FRAME_ARRANGEMENTS) {
                expected = Lane.NONE;
            }
            else if (job.getKind() == Work.Kind.NETWORK) {
                expected = Lane.NETWORK;
            }
            else {
                expected = Lane.EDIT;
            }
            assertEquals(expected, job.getLane(), job.name());
        }
    }

    @Test
    void twoEditJobsForTheSameDataSetRunOneAfterTheOther() throws Exception {
        Gate first = new Gate();
        Gate second = new Gate();
        workModel.exec(new EditWork(dataSet, first));
        assertTrue(first.started.await(WAIT_SECONDS, SECONDS), "first EDIT job should start");
        workModel.exec(new EditWork(dataSet, second));
        assertFalse(second.started.await(MUST_NOT_HAPPEN_MILLIS, MILLISECONDS),
                "second EDIT job must wait for the running one");
        first.release.countDown();
        assertTrue(second.started.await(WAIT_SECONDS, SECONDS), "second EDIT job should start once the first is done");
        assertTrue(first.finished.await(WAIT_SECONDS, SECONDS));
        second.release.countDown();
        assertTrue(second.finished.await(WAIT_SECONDS, SECONDS));
    }

    @Test
    void editJobDoesNotWaitForBatchJobOnTheSameDataSet() throws Exception {
        Gate batch = new Gate();
        Gate edit = new Gate();
        workModel.exec(new BatchWork(dataSet, batch));
        assertTrue(batch.started.await(WAIT_SECONDS, SECONDS), "BATCH job should start");
        workModel.exec(new EditWork(dataSet, edit));
        assertTrue(edit.started.await(WAIT_SECONDS, SECONDS), "EDIT job must not queue behind the BATCH job");
        assertEquals(1, batch.finished.getCount(), "BATCH job is still running while the EDIT job runs");
        batch.release.countDown();
        edit.release.countDown();
        assertTrue(batch.finished.await(WAIT_SECONDS, SECONDS));
        assertTrue(edit.finished.await(WAIT_SECONDS, SECONDS));
    }

    @Test
    void secondProcessForBusyDataSetIsRefused() throws Exception {
        Gate first = new Gate();
        Gate second = new Gate();
        workModel.exec(new ProcessWork(dataSet, first));
        assertTrue(first.started.await(WAIT_SECONDS, SECONDS), "first PROCESS should start");
        workModel.exec(new ProcessWork(dataSet, second));
        verify(feedback).alert(contains("busy"));
        first.release.countDown();
        assertTrue(first.finished.await(WAIT_SECONDS, SECONDS));
        assertFalse(second.started.await(MUST_NOT_HAPPEN_MILLIS, MILLISECONDS), "refused PROCESS must never run");
        verify(feedback, never()).alert(contains("Exception"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void silentJobQueuesInTheEditLaneOfTheOpenDataSet() throws Exception {
        currentDataSet = dataSet;
        Gate edit = new Gate();
        Gate silent = new Gate();
        workModel.exec(new EditWork(dataSet, edit));
        assertTrue(edit.started.await(WAIT_SECONDS, SECONDS), "EDIT job should start");
        workModel.exec(new SilentWork(silent));
        assertFalse(silent.started.await(MUST_NOT_HAPPEN_MILLIS, MILLISECONDS),
                "SILENT job on the open dataset must wait for the running EDIT job");
        edit.release.countDown();
        assertTrue(silent.started.await(WAIT_SECONDS, SECONDS), "SILENT job should start once the EDIT job is done");
        silent.release.countDown();
        assertTrue(silent.finished.await(WAIT_SECONDS, SECONDS));
    }

    @Test
    void silentJobWithoutOpenDataSetRunsImmediately() throws Exception {
        currentDataSet = null;
        Gate edit = new Gate();
        Gate silent = new Gate();
        workModel.exec(new EditWork(dataSet, edit));
        assertTrue(edit.started.await(WAIT_SECONDS, SECONDS), "EDIT job should start");
        workModel.exec(new SilentWork(silent));
        assertTrue(silent.started.await(WAIT_SECONDS, SECONDS), "SILENT job without a dataset is not queued");
        edit.release.countDown();
        silent.release.countDown();
        assertTrue(edit.finished.await(WAIT_SECONDS, SECONDS));
        assertTrue(silent.finished.await(WAIT_SECONDS, SECONDS));
    }

    // === fakes

    private static class Gate {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(1);

        void pass() {
            started.countDown();
            try {
                release.await();
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            finished.countDown();
        }
    }

    private static class EditWork implements Work.DataSetWork {
        private final DataSet dataSet;
        private final Gate gate;

        EditWork(DataSet dataSet, Gate gate) {
            this.dataSet = dataSet;
            this.gate = gate;
        }

        @Override
        public void run() {
            gate.pass();
        }

        @Override
        public Job getJob() {
            return Job.SAVE_HINTS;
        }

        @Override
        public DataSet getDataSet() {
            return dataSet;
        }
    }

    private static class BatchWork implements Work.DataSetPrefixWork {
        private final DataSet dataSet;
        private final Gate gate;

        BatchWork(DataSet dataSet, Gate gate) {
            this.dataSet = dataSet;
            this.gate = gate;
        }

        @Override
        public void run() {
            gate.pass();
        }

        @Override
        public Job getJob() {
            return Job.LOAD_REPORT;
        }

        @Override
        public DataSet getDataSet() {
            return dataSet;
        }

        @Override
        public String getPrefix() {
            return "prefix";
        }
    }

    private static class ProcessWork implements Work.DataSetPrefixWork, Work.LongTermWork {
        private final DataSet dataSet;
        private final Gate gate;

        ProcessWork(DataSet dataSet, Gate gate) {
            this.dataSet = dataSet;
            this.gate = gate;
        }

        @Override
        public void run() {
            gate.pass();
        }

        @Override
        public Job getJob() {
            return Job.PROCESS;
        }

        @Override
        public DataSet getDataSet() {
            return dataSet;
        }

        @Override
        public String getPrefix() {
            return "prefix";
        }

        @Override
        public void setProgressListener(ProgressListener progressListener) {
        }
    }

    private static class SilentWork implements Work {
        private final Gate gate;

        SilentWork(Gate gate) {
            this.gate = gate;
        }

        @Override
        public void run() {
            gate.pass();
        }

        @Override
        public Job getJob() {
            return Job.SELECT_NODE_MAPPING;
        }
    }
}
