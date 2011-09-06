/*
 * Copyright 2011 Delving BV
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

package eu.delving.sip.frames;

import eu.delving.groovy.MetadataRecord;
import eu.delving.sip.ProgressListener;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.ProgressAdapter;
import eu.delving.sip.base.Utility;
import eu.delving.sip.files.FileStore;
import eu.delving.sip.model.SipModel;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ProgressMonitor;
import javax.swing.SpringLayout;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class RecordScanPopup extends FrameBase {
    private List<JTextField> fields = new ArrayList<JTextField>();
    private Listener listener;
    private FieldScanPredicate currentPredicate = DEFAULT_PREDICATE;

    public interface Listener {
        void searchStarted(String description);
    }

    public RecordScanPopup(JComponent parent, SipModel sipModel, Listener listener) {
        super(parent, sipModel, "Scan Criteria", true);
        this.listener = listener;
        setDefaultSize(500, 240);
    }

    @Override
    protected void buildContent(Container content) {
        JPanel p = new JPanel(new SpringLayout());
        fields.add(createField(p, "Record Number (modulo):", new FieldScanPredicate() {

            private int modulo;

            @Override
            public void setFieldValue(String value) {
                try {
                    modulo = Integer.parseInt(value);
                }
                catch (NumberFormatException e) {
                    modulo = 1;
                }
                if (modulo <= 0) {
                    modulo = 1;
                }
            }

            @Override
            public String render() {
                return String.format("Modulo %d", modulo);
            }

            @Override
            public boolean accept(MetadataRecord record) {
                return modulo == 1 || record.getRecordNumber() % modulo == 0;
            }
        }));
        fields.add(createField(p, "Field Contains (Substring)", new FieldScanPredicate() {
            private String substring;

            @Override
            public void setFieldValue(String value) {
                this.substring = value;
            }

            @Override
            public String render() {
                return String.format("Contains '%s'", substring);
            }

            @Override
            public boolean accept(MetadataRecord record) {
                return record.contains(Pattern.compile(String.format(".*%s.*", substring)));
            }
        }));
        fields.add(createField(p, "Field Equals (RegEx)", new FieldScanPredicate() {
            private String regex;

            @Override
            public void setFieldValue(String value) {
                this.regex = value;
            }

            @Override
            public String render() {
                return String.format("Equals '%s'", regex);
            }

            @Override
            public boolean accept(MetadataRecord record) {
                return record.contains(Pattern.compile(regex));
            }
        }));
        Utility.makeCompactGrid(p, p.getComponentCount() / 3, 3, 5, 5, 5, 5);
        content.add(p, BorderLayout.CENTER);
        content.add(createCancel(), BorderLayout.SOUTH);
    }

    @Override
    protected void refresh() {
        for (JTextField field : fields) {
            field.setText(null);
        }
    }

    @Override
    protected FileStore.StoreState getMinimumStoreState() {
        return FileStore.StoreState.EMPTY;
    }

    private JPanel createCancel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                currentPredicate = DEFAULT_PREDICATE;
                listener.searchStarted(currentPredicate.render());
                closeFrame();
            }
        });
        p.add(cancel);
        return p;
    }

    public String getPredicateDescription() {
        return currentPredicate.render();
    }

    public void scan(boolean next) {
        if (!next) sipModel.seekFresh();
        ProgressListener progressListener = null;
        if (currentPredicate != null) {
            for (JTextField field : fields) field.setEnabled(false);
            final ProgressMonitor progressMonitor = new ProgressMonitor(
                    SwingUtilities.getRoot(RecordScanPopup.this),
                    "<html><h2>Scanning</h2>",
                    currentPredicate.render(),
                    0, 100
            );
            progressListener = new ProgressAdapter(progressMonitor) {
                @Override
                public void swingFinished(boolean success) {
                    for (JTextField field : fields) field.setEnabled(true);
                }
            };
        }
        sipModel.seekRecord(currentPredicate, progressListener);
    }

    private JTextField createField(Container container, String prompt, final FieldScanPredicate fieldScanPredicate) {
        JLabel label = new JLabel(prompt, JLabel.RIGHT);
        final JTextField field = new JTextField();
        label.setLabelFor(field);
        JButton setButton = new JButton("Set");
        setButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                fieldScanPredicate.setFieldValue(field.getText().trim());
                currentPredicate = fieldScanPredicate;
                listener.searchStarted(fieldScanPredicate.render());
                closeFrame();
                scan(true);
            }
        });
        container.add(label);
        container.add(field);
        container.add(setButton);
        return field;
    }

    private interface FieldScanPredicate extends SipModel.ScanPredicate {

        void setFieldValue(String value);

        String render();
    }

    private static final FieldScanPredicate DEFAULT_PREDICATE = new FieldScanPredicate() {
        @Override
        public void setFieldValue(String value) {
        }

        @Override
        public String render() {
            return "All records";
        }

        @Override
        public boolean accept(MetadataRecord record) {
            return true;
        }
    };
}
