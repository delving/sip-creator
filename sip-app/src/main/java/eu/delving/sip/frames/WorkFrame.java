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

package eu.delving.sip.frames;

import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.model.WorkModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

/**
 * Show the live work model
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class WorkFrame extends FrameBase {
    private static final DateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("hh:mm:ss");
    private static final Font MONOSPACED = new Font("Monospaced", Font.BOLD, 12);
    private JList list;

    public WorkFrame(JDesktopPane desktop, SipModel sipModel) {
        super(Which.WORK, desktop, sipModel, "Work");
        this.list = new JList(sipModel.getJobListModel());
        this.list.setFont(MONOSPACED);
        this.list.setCellRenderer(new JobContextCellRenderer());
        getAction().putValue(
                Action.ACCELERATOR_KEY,
                KeyStroke.getKeyStroke(KeyEvent.VK_W, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())
        );
    }

    @Override
    protected void buildContent(Container content) {
        content.add(SwingHelper.scrollV("Work happening in the background", list), BorderLayout.CENTER);
    }

    private class JobContextCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            WorkModel.JobContext jobContext = (WorkModel.JobContext) value;
            String date = TIMESTAMP_FORMAT.format(jobContext.getStart());
            String job = jobContext.getJob().toString();
            String dataSetSpec = jobContext.getDataSet();
            String show = String.format("%s: %s", job, date);
            if (dataSetSpec != null) {
                show += String.format(" (%s)", dataSetSpec);
            }
            String progress = jobContext.getProgress();
            if (progress != null) {
                show += String.format(" - %s", progress);
            }
            return super.getListCellRendererComponent(list, show, index, isSelected, cellHasFocus);
        }
    }
}
