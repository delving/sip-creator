/*
 * Copyright 2010 DELVING BV
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

import eu.delving.sip.ProgressListener;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.model.SipModel;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;

/**
 * The transformation from input record to output
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class OutputFrame extends FrameBase {
    private JCheckBox saveDiscarded = new JCheckBox("Save discarded records");

    public OutputFrame(JDesktopPane desktop, SipModel sipModel) {
        super(desktop, sipModel, "Output", false);
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createOutputPanel(), BorderLayout.CENTER);
        content.add(createSouth(), BorderLayout.SOUTH);
    }

    @Override
    protected void refresh() {
    }

    private JComponent createSouth() {
        JButton upload = new JButton("Upload dataset and mapping");
        upload.setEnabled(false);
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        p.add(new JButton(new ValidateAction()));
        p.add(saveDiscarded);
        p.add(new JButton(new UploadAction()));
        return p;
    }

    // todo: view invalid records popup

    private JPanel createOutputPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Output record"));
        JTextArea area = new JTextArea(sipModel.getRecordCompileModel().getOutputDocument());
        area.setEditable(false);
        p.add(scroll(area));
        return p;
    }

    private class ValidateAction extends AbstractAction {

        private ValidateAction() {
            super("Validate all records");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            String message = String.format(
                    "<html><h3>Transforming the raw data of '%s' into '%s' format</h3>",
                    sipModel.getDataSetStore().getSpec(),
                    sipModel.getMappingModel().getRecordMapping().getPrefix()
            );
            ProgressMonitor progressMonitor = new ProgressMonitor(
                    SwingUtilities.getRoot(OutputFrame.this),
                    "<html><h2>Normalizing</h2>",
                    message,
                    0, 100
            );
            sipModel.validateFile(saveDiscarded.isSelected(), new ProgressListener.Adapter(progressMonitor) {
                @Override
                public void swingFinished(boolean success) {
                    setEnabled(true);
                }
            });
        }
    }

    private class UploadAction extends AbstractAction {

        private UploadAction() {
            super("Upload to Culture Hub");
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {
            // todo: implement somehow!
        }
    }
}
