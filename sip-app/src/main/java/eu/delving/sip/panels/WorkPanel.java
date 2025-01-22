/*
 * Copyright 2011, 2012 Delving BV
 *
 * Licensed under the EUPL, Version 1.0 or as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the
 * Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.delving.sip.panels;

import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.base.Work;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.model.WorkModel;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

import static eu.delving.sip.base.SwingHelper.LONG_TERM_JOB_COLOR;
import static eu.delving.sip.base.SwingHelper.NORMAL_JOB_COLOR;

/**
 * Show the live work model , and pop up a cancellation dialog when one is selected.
 *
 *
 */

public class WorkPanel extends JPanel {
    private static final Font MONOSPACED = new Font("Monospaced", Font.BOLD, 12);
    private JList<WorkModel.JobContext> list;

    public WorkPanel(final SipModel sipModel) {
        super(new BorderLayout());
        this.list = new JList<WorkModel.JobContext>(sipModel.getWorkModel().getListModel());
        // this.list.setFont(MONOSPACED);
        this.list.setCellRenderer(new WorkCellRenderer());
        this.list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.list.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting() || list.isSelectionEmpty()) return;
                WorkModel.JobContext context = list.getSelectedValue();
                if (context != null && context.getProgressIndicator() != null) {
                    String message = String.format(
                            "<html>Do you want to cancel this job?<br>%s",
                            toFullString(context)
                    );
                    if (sipModel.getFeedback().confirm("Cancel Job", message)) {
                        context.getProgressIndicator().cancel();
                    }
                }
            }
        });
        this.list.setVisibleRowCount(4);
        setBorder(BorderFactory.createTitledBorder("Background Jobs"));
        this.add(SwingHelper.scrollVH(list), BorderLayout.CENTER);
    }

    private String toFullString(WorkModel.JobContext jobContext) {
        Work work = jobContext.getWork();
        if (work == null) return "";
        String dataSetSpec = jobContext.getDataSet();
        String show = work.getJob().toString();
        if (dataSetSpec != null) {
            String prefix = jobContext.getPrefix();
            if (prefix != null) {
                show += String.format("(%s/%s)", dataSetSpec, prefix);
            }
            else {
                show += String.format("(%s)", dataSetSpec);
            }
        }
        WorkModel.ProgressIndicator progress = jobContext.getProgressIndicator();
        if (progress != null) {
            show += String.format(" %s", progress.getProgressString());
        }
        return show;
    }

    private class WorkCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            WorkModel.JobContext jobContext = (WorkModel.JobContext) value;
            String show = toFullString(jobContext);
            Component component = super.getListCellRendererComponent(list, show, index, isSelected, cellHasFocus);
            Work work = jobContext.getWork();
            component.setForeground(Color.BLACK);
            if (work == null) {
                component.setBackground(Color.WHITE);
            }
            else if (work instanceof Work.LongTermWork) {
                component.setBackground(LONG_TERM_JOB_COLOR);
            }
            else {
                component.setBackground(NORMAL_JOB_COLOR);
            }
            return component;
        }
    }
}
