/*
 * Copyright 2012 Delving BV
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
import eu.delving.sip.base.NetworkClient;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.model.SipModel;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static eu.delving.sip.base.KeystrokeHelper.SPACE;
import static eu.delving.sip.base.KeystrokeHelper.addKeyboardAction;

/**
 * Show the datasets both local and on the server, so all info about their status is unambiguous.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class RemoteDataSetFrame extends FrameBase {
    private final NarthexDataSetTableModel narthexTableModel;
    private final JTable narthexTable;

    public RemoteDataSetFrame(final SipModel sipModel, NetworkClient networkClient) {
        super(Which.DATA_SET, sipModel, "Data Sets");
        this.narthexTableModel = new NarthexDataSetTableModel(sipModel, networkClient);
        this.narthexTable = createNarthexTable();
    }

    @Override
    protected void onOpen(boolean opened) {
        if (opened) Swing.Exec.later(new Swing() {
            @Override
            public void run() {
                narthexTable.requestFocus();
            }
        });
    }

    @Override
    protected void buildContent(Container content) {
        content.add(SwingHelper.scrollV("Narthex Data Sets", narthexTable), BorderLayout.CENTER);
        content.add(createHubTableSouth(narthexTableModel.getPatternField()), BorderLayout.SOUTH);
    }

    private JTable createNarthexTable() {
        JTable table = new JTable(narthexTableModel, narthexTableModel.getColumnModel());
        table.setRowHeight(45);
        table.setIntercellSpacing(new Dimension(12, 4));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(narthexTableModel.getSelectionListener());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() > 1 && narthexTableModel.EDIT_ACTION.isEnabled()) {
                    if (narthexTable.getSelectedRow() != narthexTable.rowAtPoint(e.getPoint())) return;
                    narthexTableModel.EDIT_ACTION.actionPerformed(null);
                }
            }
        });
        return table;
    }

    private JPanel createHubTableSouth(JTextField patternField) {
        JPanel p = new JPanel(new GridLayout(1, 0, 10, 10));
        p.setBorder(BorderFactory.createTitledBorder("Actions"));
        p.add(button(narthexTableModel.REFRESH_ACTION));
        p.add(createFilter(patternField));
        p.add(button(narthexTableModel.EDIT_ACTION));
        addKeyboardAction(narthexTableModel.EDIT_ACTION, SPACE, (JComponent) getContentPane());
//        addKeyboardAction(new UpDownAction(false), UP, (JComponent) getContentPane());
//        addKeyboardAction(new UpDownAction(true), DOWN, (JComponent) getContentPane());
        p.add(button(narthexTableModel.DOWNLOAD_ACTION));
        return p;
    }

    private JButton button(Action action) {
        JButton button = new JButton(action);
        button.setHorizontalAlignment(JButton.LEFT);
        return button;
    }

    private JPanel createFilter(JTextField patternField) {
        JLabel label = new JLabel("Filter:", JLabel.RIGHT);
        label.setLabelFor(patternField);
        JPanel p = new JPanel(new BorderLayout());
        p.add(label, BorderLayout.WEST);
//        attachAccelerator(new UpDownAction(true), patternField);
//        attachAccelerator(new UpDownAction(false), patternField);
        p.add(patternField, BorderLayout.CENTER);
        return p;
    }

    @Override
    public void refresh() {
        Swing.Exec.later(new Swing() {
            @Override
            public void run() {
                narthexTableModel.REFRESH_ACTION.actionPerformed(null);
            }
        });
    }
}
