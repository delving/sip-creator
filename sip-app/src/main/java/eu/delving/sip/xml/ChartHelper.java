/*
 * Copyright 2011 DELVING BV
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

package eu.delving.sip.xml;

import eu.delving.metadata.Path;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PiePlot3D;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.util.Rotation;

import javax.swing.*;
import java.util.*;

/**
 * Build charts based on stats
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class ChartHelper {
    private static final int MAX_PIE_CHART_SIZE = 10;
    private static final int MAX_BAR_CHART_SIZE = 30;
    private Stats stats;
    private Path path;
    private JFreeChart fieldFrequencyChart;
    private JFreeChart wordCountChart;
    private JFreeChart presentAbsentChart;

    public ChartHelper(Stats stats) {
        this.stats = stats;
        this.presentAbsentChart = createPresentAbsentChart(stats.recordStats);
    }

    public Stats.ValueStats setPath(Path path) {
        this.path = path;
        Stats.ValueStats valueStats = stats.fieldValueMap.get(path);
        this.wordCountChart = valueStats == null ? null : createWordCountChart(valueStats);
        this.fieldFrequencyChart = stats.recordStats == null ? null : createFieldFrequencyChart(stats.recordStats, path);
        return valueStats;
    }

    public boolean hasFrequencyChart() {
        return fieldFrequencyChart != null;
    }

    public JComponent getFieldFrequencyChart() {
        ChartPanel panel = new ChartPanel(fieldFrequencyChart);
        panel.setMouseWheelEnabled(true);
        return panel;
    }

    public boolean hasWordCountChart() {
        return wordCountChart != null;
    }

    public JComponent getWordCountChart() {
        ChartPanel panel = new ChartPanel(wordCountChart);
        panel.setMouseWheelEnabled(true);
        return panel;
    }

    public boolean hasPresentAbsentChart() {
        return presentAbsentChart != null;
    }

    public JComponent getPresentAbsentChart() {
        ChartPanel panel = new ChartPanel(presentAbsentChart);
        panel.setMouseWheelEnabled(true);
        return panel;
    }

    private static JFreeChart createWordCountChart(Stats.ValueStats valueStats) {
        if (valueStats.wordCounts == null) return null;
        List<Stats.Counter> sorted = new ArrayList<Stats.Counter>(valueStats.wordCounts.counterMap.size());
        sorted.addAll(valueStats.wordCounts.counterMap.values());
        Collections.sort(sorted, new Comparator<Stats.Counter>() {
            @Override
            public int compare(Stats.Counter a, Stats.Counter b) {
                return Integer.valueOf(a.value).compareTo(Integer.valueOf(b.value));
            }
        });
        if (sorted.size() < MAX_PIE_CHART_SIZE) {
            DefaultPieDataset data = new DefaultPieDataset();
            for (Stats.Counter counter : sorted) data.setValue(counter.value, counter.count);
            JFreeChart chart = ChartFactory.createPieChart3D(
                    "Field frequency within record",
                    data,
                    false, true, false
            );
            return pieChart(chart);
        }
        else {

            int remainder = 0;
            if (sorted.size() > MAX_BAR_CHART_SIZE) {
                for (Stats.Counter counter : sorted.subList(MAX_BAR_CHART_SIZE, sorted.size())) remainder += counter.count;
                sorted = sorted.subList(0, MAX_BAR_CHART_SIZE);
            }
            DefaultCategoryDataset data = new DefaultCategoryDataset();
            for (Stats.Counter counter : sorted) data.addValue(counter.count, "Count", counter.value);
            if (remainder > 0) data.addValue(remainder, "Count", "+");
            JFreeChart chart = ChartFactory.createBarChart(
                    "Word Count",
                    "Number of Words",
                    "Count",
                    data,
                    PlotOrientation.HORIZONTAL,
                    false, true, false
            );
            return horizontalBarChart(chart);
        }
    }

    private static JFreeChart createFieldFrequencyChart(Stats.RecordStats recordStats, Path path) {
        Stats.Histogram histogram = recordStats.frequencies.get(path);
        if (histogram == null) return null;
        List<Stats.Counter> sorted = new ArrayList<Stats.Counter>(histogram.counterMap.size());
        sorted.addAll(histogram.counterMap.values());
        Collections.sort(sorted, new Comparator<Stats.Counter>() {
            @Override
            public int compare(Stats.Counter a, Stats.Counter b) {
                return b.count - a.count;
            }
        });
        if (sorted.size() < MAX_PIE_CHART_SIZE) {
            DefaultPieDataset data = new DefaultPieDataset();
            for (Stats.Counter counter : sorted) data.setValue(counter.value, counter.count);
            JFreeChart chart = ChartFactory.createPieChart3D(
                    "Field frequency within record",
                    data,
                    false, true, false
            );
            return pieChart(chart);
        }
        else {
            int remainder = 0;
            if (sorted.size() > MAX_BAR_CHART_SIZE) {
                for (Stats.Counter counter : sorted.subList(MAX_BAR_CHART_SIZE, sorted.size())) remainder += counter.count;
                sorted = sorted.subList(0, MAX_BAR_CHART_SIZE);
            }
            DefaultCategoryDataset data = new DefaultCategoryDataset();
            data.addValue(histogram.absent, "Frequency", "0");
            for (Stats.Counter counter : sorted) data.addValue(counter.count, "Frequency", counter.value);
            if (remainder > 0) data.addValue(remainder, "Frequency", "+");
            JFreeChart chart = ChartFactory.createBarChart(
                    "Field frequency within record",
                    "Cardinality",
                    "Frequency",
                    data,
                    PlotOrientation.HORIZONTAL,
                    false, true, false
            );
            return horizontalBarChart(chart);
        }
    }

    private static JFreeChart pieChart(JFreeChart chart) {
        PiePlot3D pieplot3d = (PiePlot3D) chart.getPlot();
        pieplot3d.setStartAngle(290D);
        pieplot3d.setDirection(Rotation.CLOCKWISE);
        pieplot3d.setForegroundAlpha(0.5F);
        pieplot3d.setNoDataMessage("No data to display");
        return chart;
    }

    private static JFreeChart createPresentAbsentChart(Stats.RecordStats recordStats) {
        if (recordStats == null) return null;
        DefaultCategoryDataset data = new DefaultCategoryDataset();
        for (Map.Entry<Path, Stats.Histogram> histogramEntry : recordStats.frequencies.entrySet()) {
            data.addValue(histogramEntry.getValue().absent, "Absent", histogramEntry.getKey().getTail());
            data.addValue(histogramEntry.getValue().present, "Present", histogramEntry.getKey().getTail());
        }
        JFreeChart chart = ChartFactory.createBarChart(
                "Present / Absent",
                "Field",
                "Record Count",
                data,
                PlotOrientation.HORIZONTAL,
                true, true, false
        );
        return horizontalBarChart(chart);
    }

    private static JFreeChart horizontalBarChart(JFreeChart chart) {
        CategoryPlot categoryplot = (CategoryPlot) chart.getPlot();
        categoryplot.setRangeAxisLocation(AxisLocation.BOTTOM_OR_LEFT);
        categoryplot.setRangePannable(true);
        BarRenderer barrenderer = (BarRenderer) categoryplot.getRenderer();
        barrenderer.setItemLabelAnchorOffset(9D);
        barrenderer.setBaseItemLabelsVisible(true);
        barrenderer.setBaseItemLabelGenerator(new StandardCategoryItemLabelGenerator());
        barrenderer.setMaximumBarWidth(0.03);
        CategoryAxis categoryaxis = categoryplot.getDomainAxis();
        categoryaxis.setCategoryMargin(0.25D);
        categoryaxis.setUpperMargin(0.02D);
        categoryaxis.setLowerMargin(0.02D);
        NumberAxis numberaxis = (NumberAxis) categoryplot.getRangeAxis();
        numberaxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        numberaxis.setUpperMargin(0.10000000000000001D);
        ChartUtilities.applyCurrentTheme(chart);
        return chart;
    }
}
