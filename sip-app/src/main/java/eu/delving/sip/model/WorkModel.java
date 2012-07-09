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

import eu.delving.sip.base.Work;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * A model of all the work that is being done in background threads at any time.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class WorkModel {
    private static final int REFRESH_RATE = 1000;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private List<JobContext> jobs = new CopyOnWriteArrayList<JobContext>();
    private JobList jobList = new JobList();

    public WorkModel() {
        Timer tick = new Timer(REFRESH_RATE, jobList);
        tick.setRepeats(true);
        tick.start();
    }

    public void exec(Work work) {
        switch (work.getJob().getKind()) {
            case SILENT:
                executor.execute(work);
                break;
            default:
                executeInContext(work);
                break;
        }
    }

    private void executeInContext(Work work) {
        JobContext jobContext = new JobContext(work);
        jobContext.setFuture(executor.submit(work, jobContext));
        jobs.add(jobContext);
    }

    public ListModel getListModel() {
        return jobList;
    }

    private class JobList extends AbstractListModel implements ActionListener {
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
                fireIntervalRemoved(this, 0, size - 1);
            }
            Iterator<JobContext> jobWalk = jobs.iterator();
            while (jobWalk.hasNext()) {
                JobContext context = jobWalk.next();
                if (context.isDone()) jobWalk.remove();
                snapshot.add(context);
            }
            if (!snapshot.isEmpty()) {
                fireIntervalAdded(this, 0, snapshot.size() - 1);
            }
        }
    }

    private class JobContext {
        private Work work;
        private Date start;
        private Future<JobContext> future;

        public JobContext(Work work) {
            this.work = work;
            this.start = new Date();
        }

        public void setFuture(Future<JobContext> future) {
            this.future = future;
        }

        public Work.Job getJob() {
            return work.getJob();
        }

        public Date getStart() {
            return start;
        }

        public boolean isDone() {
            return future.isDone();
        }
    }
}
