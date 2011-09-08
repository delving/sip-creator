/*
 * Copyright 2011 DELVING BV
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

package eu.delving.sip.base;

import eu.delving.metadata.ValidationException;
import eu.delving.sip.model.SipModel;

import javax.swing.AbstractAction;
import javax.swing.JCheckBox;
import javax.swing.JDesktopPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Runs the validation for a file.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class ValidateAction extends AbstractAction {

    private SipModel sipModel;
    private JDesktopPane parent;

    public ValidateAction(JDesktopPane parent, SipModel sipModel) {
        super("Validate all records");
        this.sipModel = sipModel;
        this.parent = parent;
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        String message = String.format(
                "<html><h3>Transforming the raw data of '%s' into '%s' format and validating</h3>",
                sipModel.getDataSetModel().getDataSet().getSpec(),
                sipModel.getMappingModel().getRecordMapping().getPrefix()
        );
        ProgressMonitor progressMonitor = new ProgressMonitor(
                SwingUtilities.getRoot(parent),
                "<html><h2>Validating</h2>",
                message,
                0, 100
        );
        sipModel.validateFile(
                new ProgressAdapter(progressMonitor) {
                    @Override
                    public void swingFinished(boolean success) {
                        setEnabled(true);
                    }
                },
                new SipModel.ValidationListener() {

                    @Override
                    public void failed(ValidationException validationException) {
                        final JCheckBox allowInvalidCheckBox = new JCheckBox("Allow invalid records");
                        JPanel pane = new JPanel(new GridLayout(2, 0));
                        pane.add(new JLabel(String.format("Error in record #%d, do you want to fix this problem?",
                                validationException.getRecordNumber())
                        ));
                        pane.add(allowInvalidCheckBox);
                        allowInvalidCheckBox.setSelected(sipModel.isAllowInvalidRecords());
                        allowInvalidCheckBox.addActionListener(
                                new ActionListener() {
                                    @Override
                                    public void actionPerformed(ActionEvent actionEvent) {
                                        sipModel.setAllowInvalidRecords(allowInvalidCheckBox.isSelected());
                                    }
                                }
                        );
                        JOptionPane.showMessageDialog(parent, pane, "title", JOptionPane.YES_NO_OPTION);
                    }
                }
        );
    }
}
