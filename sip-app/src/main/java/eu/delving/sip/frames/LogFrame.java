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

package eu.delving.sip.frames;

import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.model.Feedback;
import eu.delving.sip.model.SipModel;
import io.sentry.Sentry;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Render the record mapping as a list of NodeMapping instances, so that an individual NodeMapping can be
 * selected for editing in the FieldMappingFrame.
 *
 *
 */

public class LogFrame extends FrameBase {
    private static final SimpleDateFormat DATE = new SimpleDateFormat("MM-dd HH:mm:ss");
    private JTextArea logArea = new JTextArea(30, 100);
    private Feedback.Log log;

    public LogFrame(final SipModel sipModel) {
        super(Which.LOG, sipModel, "Log");
        log = new Feedback.Log() {
            @Override
            public void log(String message, Throwable throwable) { // in swing thread
                logArea.append(String.format("%s %s\n", DATE.format(new Date()), message));
                if (throwable != null) {
                    StringWriter writer = new StringWriter();
                    PrintWriter out = new PrintWriter(writer);
                    throwable.printStackTrace(out);
                    logArea.append(writer.toString());

                    // If Sentry is enabled, let it capture the exception
                    Sentry.captureException(throwable);
                }
            }
        };
    }

    public Feedback.Log getLog() {
        return log;
    }

    @Override
    protected void buildContent(Container content) {
        content.add(SwingHelper.scrollVH("Log", logArea), BorderLayout.CENTER);
        content.add(new JButton(new ClearAction()), BorderLayout.SOUTH);
    }

    private class ClearAction extends AbstractAction {

        private ClearAction() {
            super("Clear");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            logArea.setText("");

        }
    }

}
