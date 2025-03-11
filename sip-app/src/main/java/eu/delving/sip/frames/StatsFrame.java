/*
 * Copyright 2011, 2012 Delving BV
 *
 * Licensed under the EUPL, Version 1.0 or? as soon they
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

import eu.delving.metadata.Path;
import eu.delving.sip.base.FrameBase;
import eu.delving.sip.base.SwingHelper;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.model.DataSetModel;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.model.StatsNode;
import eu.delving.stats.ChartHelper;
import eu.delving.stats.Stats;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static eu.delving.sip.base.SwingHelper.scrollV;
import static eu.delving.sip.base.SwingHelper.scrollVH;

/**
 * Show the statistics bar charts and histograms for input, source, and output in various tabs.
 *
 *
 */

public class StatsFrame extends FrameBase {
    private StatsSet statsSet = new StatsSet("Source");
    private JPanel wordCountPanel = emptyPanel();
    private JPanel fieldFrequencyPanel = emptyPanel();
    private JPanel presencePanel = emptyPanel();
    private JPanel fieldCountPanel = emptyPanel();
    private JPanel valueHistogramPanel = emptyPanel();

    public StatsFrame(SipModel sipModel) {
        super(Which.STATISTICS, sipModel, "Stats");
        wireUp();
    }

    @Override
    protected void buildContent(Container content) {
        add(createWest(), BorderLayout.WEST);
        add(createCenter(), BorderLayout.CENTER);
    }

    private JComponent createWest() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(statsSet.statsSetName));
        p.add(statsSet.treePanel);
        statsSet.select();
        return p;
    }

    private JComponent createCenter() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Presence in Record", presencePanel);
        tabs.addTab("Field Count", fieldCountPanel);
        tabs.addTab("Word Count", wordCountPanel);
        tabs.addTab("Field Frequency", fieldFrequencyPanel);
        tabs.addTab("Values Histogram", valueHistogramPanel);
        return tabs;
    }

    private void clearPanel(JPanel panel) {
        panel.removeAll();
        panel.add(emptyLabel());
        panel.validate();
    }

    private void setPanelContent(JPanel panel, JComponent content) {
        if (content == null) {
            clearPanel(panel);
        }
        else {
            panel.removeAll();
            panel.add(content);
            panel.validate();
        }
    }

    private JPanel emptyPanel() {
        JPanel p = new JPanel(new BorderLayout());
        clearPanel(p);
        return p;
    }

    private JComponent emptyLabel() {
        return new JLabel("Not Available", JLabel.CENTER);
    }

    private void wireUp() {
        sipModel.getDataSetModel().addListener(new DataSetModel.SwingListener() {
            @Override
            public void stateChanged(DataSetModel model, DataSetState state) {
                DataSet dataSet = model.getDataSet();
                switch (state) {
                    case ABSENT:
                    case SOURCED:
                        statsSet.setStats(null);
                        break;
                    case MAPPING:
                    case ANALYZED_SOURCE:
                        statsSet.setStats(dataSet.getStats());
                        break;
                    case PROCESSED:
                        statsSet.setStats(dataSet.getStats());
                        break;
                }
            }
        });
    }

    private class StatsSet {
        private String statsSetName;
        private DefaultTreeModel treeModel = new DefaultTreeModel(StatsNode.create("Empty"));
        private JTree tree = new JTree(treeModel);
        private JPanel treePanel = emptyPanel();
        private ChartHelper chartHelper;
        private boolean expand = false;

        private StatsSet(String statsSetName) {
            this.statsSetName = statsSetName;
            tree.setCellRenderer(new StatsNode.Renderer());
            tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
            tree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
                @Override
                public void valueChanged(TreeSelectionEvent treeSelectionEvent) {
                    TreePath treePath = tree.getSelectionPath();
                    if (treePath == null) {
                        setPath(null);
                    }
                    else {
                        setPath(((StatsNode) treePath.getLastPathComponent()).getPath(true));
                    }
                }
            });
        }

        private void setStats(Stats stats) {
            if (stats == null) {
                chartHelper = null;
                treeModel.setRoot(StatsNode.create("Empty"));
                clearPanel(treePanel);
            }
            else {
                String name = (stats.name == null) ? sipModel.getDataSetModel().getDataSet().getSpec() : stats.name;
                chartHelper = new ChartHelper(stats, statsSetName, name);
                final StatsNode root = StatsNode.create(stats.fieldValueMap.keySet());
                setPanelContent(treePanel, scrollVH(tree));
                JPanel enforceMinimumWidth = new JPanel();
                enforceMinimumWidth.setPreferredSize(new Dimension(500, 0));
                treePanel.add(enforceMinimumWidth, BorderLayout.SOUTH);
                treeModel.setRoot(root);
                expand = true;
                expandIfNecessary();
                setRecordStatPanels();
            }
        }

        private void select() {
            setRecordStatPanels();
            TreePath treePath = tree.getSelectionModel().getSelectionPath();
            setPath(treePath == null ? null : ((StatsNode) treePath.getLastPathComponent()).getPath(true));
            expandIfNecessary();
        }

        private void setRecordStatPanels() {
            if (chartHelper != null) {
                setPanelContent(fieldCountPanel, chartHelper.hasFieldCountChart() ? chartHelper.getFieldCountPanel() : null);
                setPanelContent(presencePanel, chartHelper.hasPresentAbsentChart() ? chartHelper.getPresencePanel() : null);
            }
            else {
                clearPanel(fieldCountPanel);
                clearPanel(presencePanel);
            }
        }

        private void expandIfNecessary() {
            if (!expand) return;
            StatsNode root = (StatsNode) tree.getModel().getRoot();
            root.expand(tree);
            expand = false;
        }

        private void setPath(Path path) {
            if (path != null) {
                if (chartHelper == null) return;
                Stats.ValueStats valueStats = chartHelper.setPath(path);
                setPanelContent(fieldFrequencyPanel, chartHelper.hasFrequencyChart() ? chartHelper.getFieldFrequencyPanel() : null);
                setPanelContent(wordCountPanel, chartHelper.hasWordCountChart() ? chartHelper.getWordCountPanel() : null);
                Stats.Histogram values = valueStats == null ? null : valueStats.values;
                setPanelContent(valueHistogramPanel, values != null ? createHistogramList(values) : null);
            }
            else {
                clearPanel(fieldFrequencyPanel);
                clearPanel(wordCountPanel);
                clearPanel(valueHistogramPanel);
            }
        }
    }

    private JComponent createHistogramList(Stats.Histogram values) {
        HistogramModel histogramModel = new HistogramModel();
        histogramModel.setHistogram(values);
        JList<Stats.Counter> list = new JList<Stats.Counter>(histogramModel);
        list.setCellRenderer(new HistogramCellRenderer());
        list.setTransferHandler(new ListTransferHandler());
        JLabel trimLabel = new JLabel("All values present", JLabel.CENTER);
        if (values.isTrimmed()) {
            trimLabel.setText("Histogram is incomplete");
            trimLabel.setForeground(Color.RED);
        }
        JPanel p = new JPanel(new BorderLayout());
        p.add(scrollV(list), BorderLayout.CENTER);
        p.add(trimLabel, BorderLayout.SOUTH);
        return p;
    }

    private static class HistogramModel extends AbstractListModel<Stats.Counter> {
        private List<Stats.Counter> list = new ArrayList<Stats.Counter>();

        public void setHistogram(Stats.Histogram histogram) {
            int size = getSize();
            list.clear();
            fireIntervalRemoved(this, 0, size);
            if (histogram != null) {
                list.addAll(histogram.counterMap.values());
                Collections.sort(list);
                fireIntervalAdded(this, 0, getSize());
            }
        }

        @Override
        public int getSize() {
            return list.size();
        }

        @Override
        public Stats.Counter getElementAt(int i) {
            return list.get(i);
        }
    }

    private class HistogramCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Stats.Counter counter = (Stats.Counter) value;
            String show = String.format("   %d (%s) : '%s'", counter.count, counter.percentage, counter.value);
            return super.getListCellRendererComponent(list, show, index, isSelected, cellHasFocus);
        }
    }

    private class ListTransferHandler extends TransferHandler {
        protected Transferable createTransferable(JComponent c) {
            JList list = (JList) c;
            StringBuilder out = new StringBuilder();
            boolean onlyValues = sipModel.getFeedback().confirm("Copy", "Copy only values?");
            for (int walk = 0; walk < list.getModel().getSize(); walk++) {
                Stats.Counter counter = (Stats.Counter) list.getModel().getElementAt(walk);
                if (onlyValues) {
                    out.append(counter.value).append('\n');
                }
                else {
                    out.append(counter.toString()).append('\n');
                }
            }
            return new SwingHelper.StringTransferable(out.toString());
        }

        public int getSourceActions(JComponent c) {
            return COPY;
        }
    }
}
