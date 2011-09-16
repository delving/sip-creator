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

package eu.delving.sip.frames;

import eu.delving.sip.base.Exec;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.FactModel;
import eu.delving.sip.model.SipModel;

import javax.swing.BorderFactory;
import javax.swing.JDesktopPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Show the facts and the status of the current dataset/mapping
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class StatusFrame extends FrameBase {
    private FactsTableModel factsTableModel = new FactsTableModel();
    private JLabel statusLabel = new JLabel();
    private JTable factsTable = new JTable(factsTableModel);

    public StatusFrame(JDesktopPane desktop, SipModel sipModel) {
        super(desktop, sipModel, "Status", false);
        sipModel.getDataSetModel().addListener(new DataSetModel.Listener() {
            @Override
            public void dataSetChanged(DataSet dataSet) {
                showStatus(dataSet.getState());
            }

            @Override
            public void dataSetRemoved() {
                statusLabel.setText("<html><b>No dataset</b>");
            }

            @Override
            public void dataSetStateChanged(DataSet dataSet, DataSetState dataSetState) {
                showStatus(dataSetState);
            }
        });
        statusLabel.setFont(new Font("Sans", Font.BOLD, 24));
        sipModel.getDataSetFacts().addListener(factsTableModel);
    }

    @Override
    protected void buildContent(Container content) {
        content.add(createStatusPanel(), BorderLayout.NORTH);
        content.add(createFactsPanel(), BorderLayout.CENTER);
    }

    @Override
    protected void refresh() {
    }

    private void showStatus(final DataSetState dataSetState) {
        statusLabel.setText(String.format("<html><b>Status:</b><i>%s</i>", saveStateDescription(dataSetState)));
    }

    private String saveStateDescription(DataSetState dataSetState) {
        switch (dataSetState) {
            case EMPTY:
                return "has no source yet";
            case IMPORTED:
                performAnalysis();
                return "imported - analyze";
            case IMPORTED_ANALYZED:
                return "imported - choose record root and unique element";
            case IMPORTED_HINTS_SET:
                return "imported - ready for conversion to source";
            case SOURCED:
                return "sourced";
            case ANALYZED:
                return "analyzed";
            case MAPPED:
                return "mapping exists, not validated";
            case VALIDATED:
                return "validated, ready for upload!";
            default:
                throw new IllegalArgumentException("Unknown data set state: " + dataSetState);
        }
    }

    private JPanel createStatusPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Status"));
        p.add(statusLabel);
        return p;
    }

    private JPanel createFactsPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Data Set Facts"));
        p.add(factsTable.getTableHeader(), BorderLayout.NORTH);
        p.add(factsTable, BorderLayout.CENTER);
        return p;
    }

    private class FactsTableModel extends AbstractTableModel implements FactModel.Listener {

        private List<Map.Entry<String, String>> entryList = new ArrayList<Map.Entry<String, String>>();

        @Override
        public String getColumnName(int col) {
            return col == 0 ? "Fact Name" : "Value";
        }

        @Override
        public int getRowCount() {
            return entryList.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public Object getValueAt(int row, int col) {
            return col == 0 ? entryList.get(row).getKey() : entryList.get(row).getValue();
        }

        @Override
        public void factUpdated(String name, String value) {
            // doesn't happen
        }

        @Override
        public void allFactsUpdated(Map<String, String> map) {
            if (!entryList.isEmpty()) {
                int oldRows = getRowCount();
                entryList.clear();
                fireTableRowsDeleted(0, oldRows);
            }
            entryList.addAll(map.entrySet());
            if (!entryList.isEmpty()) {
                fireTableRowsInserted(0, getRowCount());
            }
        }
    }

    private void performAnalysis() {
        sipModel.analyzeFields(new SipModel.AnalysisListener() {

            @Override
            public void finished(boolean success) {
                Exec.swing(new Runnable() {
                    @Override
                    public void run() {
                        setElementsProcessed(sipModel.getAnalysisModel().getElementCount());
                    }
                });
            }

            @Override
            public void analysisProgress(final long elementCount) {
                Exec.swing(new Runnable() {
                    @Override
                    public void run() {
                        setElementsProcessed(elementCount);
                    }
                });
            }
        });
    }

    private void setElementsProcessed(long count) {
        statusLabel.setText(String.format("%d elements processed", count));
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(400, 250);
    }
}
