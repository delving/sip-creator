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
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Build charts based on stats
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class ChartHelper {
    private static final int MAX_BAR_CHART_SIZE = 30;
    private Stats stats;
    private Path path;
    private JFreeChart fieldFrequencyChart;
    private JFreeChart wordCountChart;

    public ChartHelper(Stats stats) {
        this.stats = stats;
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

    private static JFreeChart createWordCountChart(Stats.ValueStats valueStats) {
        if (valueStats.wordCounts == null) return null;
        DefaultCategoryDataset data = new DefaultCategoryDataset();
        List<Stats.Counter> sorted = new ArrayList<Stats.Counter>(valueStats.wordCounts.counterMap.size());
        sorted.addAll(valueStats.wordCounts.counterMap.values());
        Collections.sort(sorted, new Comparator<Stats.Counter>() {
            @Override
            public int compare(Stats.Counter a, Stats.Counter b) {
                return Integer.valueOf(a.value).compareTo(Integer.valueOf(b.value));
            }
        });
        int remainder = 0;
        if (sorted.size() > MAX_BAR_CHART_SIZE) {
            for (Stats.Counter counter : sorted.subList(MAX_BAR_CHART_SIZE, sorted.size())) remainder += counter.count;
            sorted = sorted.subList(0, MAX_BAR_CHART_SIZE);
        }
        for (Stats.Counter counter : sorted) data.addValue(counter.count, "Word Count", counter.value);
        if (remainder > 0) data.addValue(remainder, "Word Count", "Remainder");
        JFreeChart chart = ChartFactory.createBarChart(
                "Word Count",
                "Number of Words",
                "Frequency",
                data,
                PlotOrientation.HORIZONTAL,
                false, true, false
        );
        CategoryPlot categoryplot = (CategoryPlot) chart.getPlot();
        categoryplot.setRangeAxisLocation(AxisLocation.BOTTOM_OR_LEFT);
        categoryplot.setRangePannable(true);
        BarRenderer barrenderer = (BarRenderer) categoryplot.getRenderer();
        barrenderer.setItemLabelAnchorOffset(9D);
        barrenderer.setBaseItemLabelsVisible(true);
        barrenderer.setBaseItemLabelGenerator(new StandardCategoryItemLabelGenerator());
//        barrenderer.setBaseToolTipGenerator(new StandardCategoryToolTipGenerator("{0}, {1}) = {2} per 100,000", new DecimalFormat("0")));
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

    private static JFreeChart createFieldFrequencyChart(Stats.RecordStats recordStats, Path path) {
        Stats.Histogram histogram = recordStats.frequencies.get(path);
        if (histogram == null) return null;
        DefaultCategoryDataset data = new DefaultCategoryDataset();
        List<Stats.Counter> sorted = new ArrayList<Stats.Counter>(histogram.counterMap.size());
        sorted.addAll(histogram.counterMap.values());
        Collections.sort(sorted, new Comparator<Stats.Counter>() {
            @Override
            public int compare(Stats.Counter a, Stats.Counter b) {
                return b.count - a.count;
            }
        });
        int remainder = 0;
        if (sorted.size() > MAX_BAR_CHART_SIZE) {
            for (Stats.Counter counter : sorted.subList(MAX_BAR_CHART_SIZE, sorted.size())) remainder += counter.count;
            sorted = sorted.subList(0, MAX_BAR_CHART_SIZE);
        }
        data.addValue(histogram.absent, "Frequency", "0");
        for (Stats.Counter counter : sorted) data.addValue(counter.count, "Frequency", counter.value);
        if (remainder > 0) data.addValue(remainder, "Frequency", "Remainder");
        JFreeChart chart = ChartFactory.createBarChart(
                "Field Frequency",
                "Cardinality",
                "Frequency",
                data,
                PlotOrientation.HORIZONTAL,
                false, true, false
        );
        CategoryPlot categoryplot = (CategoryPlot) chart.getPlot();
        categoryplot.setRangeAxisLocation(AxisLocation.BOTTOM_OR_LEFT);
        categoryplot.setRangePannable(true);
        BarRenderer barrenderer = (BarRenderer) categoryplot.getRenderer();
        barrenderer.setItemLabelAnchorOffset(9D);
        barrenderer.setBaseItemLabelsVisible(true);
        barrenderer.setBaseItemLabelGenerator(new StandardCategoryItemLabelGenerator());
//        barrenderer.setBaseToolTipGenerator(new StandardCategoryToolTipGenerator("{0}, {1}) = {2} per 100,000", new DecimalFormat("0")));
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
