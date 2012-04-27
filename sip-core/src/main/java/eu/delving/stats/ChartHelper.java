/*
 * Copyright 2011, 2012 Delving BV
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

package eu.delving.stats;

import eu.delving.metadata.Path;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.ItemLabelPosition;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.category.DefaultCategoryDataset;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

import static org.jfree.chart.labels.ItemLabelAnchor.INSIDE12;
import static org.jfree.chart.labels.ItemLabelAnchor.OUTSIDE12;
import static org.jfree.ui.TextAnchor.CENTER_LEFT;
import static org.jfree.ui.TextAnchor.CENTER_RIGHT;

/**
 * Build charts based on stats
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class ChartHelper {
    private static final int MAX_BAR_CHART_SIZE = 40;
    private Stats stats;
    private JFreeChart fieldFrequencyChart;
    private JFreeChart wordCountChart;
    private JFreeChart[] presenceCharts;
    private JFreeChart fieldCountChart;
    private String name;

    public ChartHelper(Stats stats, String which, String name) {
        this.stats = stats;
        this.presenceCharts = createPresenceCharts(stats.recordStats, which, name);
        this.fieldCountChart = createFieldCountChart(stats.recordStats, which, name);
        this.name = name;
    }

    public Stats.ValueStats setPath(Path path) {
        Stats.ValueStats valueStats = stats.fieldValueMap.get(path);
        this.wordCountChart = valueStats == null ? null : createWordCountChart(valueStats, path, name);
        this.fieldFrequencyChart = stats.recordStats == null ? null : createFieldFrequencyChart(stats.recordStats, path, name);
        return valueStats;
    }

    public boolean hasFrequencyChart() {
        return fieldFrequencyChart != null;
    }

    public JFreeChart getFieldFrequencyChart() {
        return fieldFrequencyChart;
    }

    public JComponent getFieldFrequencyPanel() {
        return wrap(fieldFrequencyChart);
    }

    public boolean hasWordCountChart() {
        return wordCountChart != null;
    }

    public JFreeChart getWordCountChart() {
        return wordCountChart;
    }

    public JComponent getWordCountPanel() {
        return wrap(wordCountChart);
    }

    public boolean hasPresentAbsentChart() {
        return presenceCharts != null;
    }

    public JFreeChart[] getPresenceCharts() {
        return presenceCharts;
    }

    public JComponent getPresencePanel() {
        ChartPanel[] panels = new ChartPanel[presenceCharts.length];
        int index = 0;
        for (JFreeChart chart : presenceCharts) {
            panels[index] = wrap(chart);
            index++;
        }
        if (panels.length > 1) {
            JTabbedPane tabs = new JTabbedPane();
            index = 0;
            for (ChartPanel panel : panels) tabs.addTab(String.format("Chart %d", index++), panel);
            return tabs;
        }
        else {
            return panels[0];
        }
    }

    public boolean hasFieldCountChart() {
        return fieldCountChart != null;
    }

    public JFreeChart getFieldCountChart() {
        return fieldCountChart;
    }

    public JComponent getFieldCountPanel() {
        return wrap(fieldCountChart);
    }

    private static ChartPanel wrap(JFreeChart chart) {
        return new ChartPanel(chart);
    }

    private static JFreeChart createWordCountChart(Stats.ValueStats valueStats, Path path, String name) {
        if (valueStats.wordCounts == null) return null;
        List<Stats.Counter> sorted = sort(valueStats.wordCounts.counterMap.values(), MAX_BAR_CHART_SIZE, valueStats.total);
        DefaultCategoryDataset data = new DefaultCategoryDataset();
        for (Stats.Counter counter : sorted) data.addValue(counter.count, "Count", counter.value);
        JFreeChart chart = ChartFactory.createBarChart(
                String.format("Word count per occurrence in %s", name),
                "Number of Words",
                "Record count",
                data,
                PlotOrientation.VERTICAL,
                false, true, false
        );
        chart.addSubtitle(new TextTitle(path.toString()));
        return finishBarChart(chart, new Color(218, 112, 214));
    }

    private static JFreeChart createFieldFrequencyChart(Stats.RecordStats recordStats, Path path, String name) {
        Stats.Histogram histogram = recordStats.frequencies.get(path);
        if (histogram == null) return null;
        List<Stats.Counter> sorted = sort(histogram.counterMap.values(), MAX_BAR_CHART_SIZE, recordStats.recordCount);
        DefaultCategoryDataset data = new DefaultCategoryDataset();
        data.addValue(histogram.absent, "Frequency", "0");
        for (Stats.Counter counter : sorted) data.addValue(counter.count, "Frequency", counter.value);
        JFreeChart chart = ChartFactory.createBarChart(
                String.format("Frequency within record in %s", name),
                "Occurrences",
                "Frequency",
                data,
                PlotOrientation.VERTICAL,
                false, true, false
        );
        chart.addSubtitle(new TextTitle(path.toString()));
        return finishBarChart(chart, new Color(30, 144, 225));
    }

    private static JFreeChart[] createPresenceCharts(Stats.RecordStats recordStats, String which, String name) {
        if (recordStats == null) return null;
        int bars = recordStats.frequencies.size();
        JFreeChart[] charts;
        if (bars > MAX_BAR_CHART_SIZE) {
            charts = new JFreeChart[bars / MAX_BAR_CHART_SIZE + 1];
        }
        else {
            charts = new JFreeChart[1];
        }
        Iterator<Map.Entry<Path, Stats.Histogram>> entryWalk = recordStats.frequencies.entrySet().iterator();
        int chartSize = bars / charts.length;
        for (int walk = 0; walk < charts.length; walk++) {
            DefaultCategoryDataset data = new DefaultCategoryDataset();
            if (walk == charts.length - 1) chartSize = MAX_BAR_CHART_SIZE;
            for (int count = 0; count < chartSize; count++) {
                if (!entryWalk.hasNext()) continue;
                Map.Entry<Path, Stats.Histogram> histogramEntry = entryWalk.next();
                data.addValue(histogramEntry.getValue().absent, "Absent", histogramEntry.getKey().getTail());
                data.addValue(histogramEntry.getValue().present, "Present", histogramEntry.getKey().getTail());
            }
            charts[walk] = finishBarChart(ChartFactory.createBarChart(
                    String.format("Fields presence in %s of %s", which, name),
                    "Field",
                    "Record count",
                    data,
                    PlotOrientation.VERTICAL,
                    true, true, false
            ));
        }
        return charts;
    }

    private static JFreeChart createFieldCountChart(Stats.RecordStats recordStats, String which, String name) {
        if (recordStats == null) return null;
        List<Stats.Counter> sorted = sort(recordStats.fieldCount.counterMap.values());
        DefaultCategoryDataset data = new DefaultCategoryDataset();
        for (Stats.Counter counter : sorted) data.addValue(counter.count, "Count", counter.value);
        JFreeChart chart = ChartFactory.createBarChart(
                String.format("Unique fields per record in %s of %s", which, name),
                "Unique field count",
                "Record count",
                data,
                PlotOrientation.VERTICAL,
                false, true, false
        );
        return finishBarChart(chart);//, new Color(220, 20, 60), new Color(50, 205, 50));
    }

    private static JFreeChart finishBarChart(JFreeChart chart, Color... colors) {
        CategoryPlot categoryplot = (CategoryPlot) chart.getPlot();
        categoryplot.setRangeAxisLocation(AxisLocation.BOTTOM_OR_LEFT);
        categoryplot.setRangePannable(true);
        if (colors.length > 0) categoryplot.setRenderer(new CustomRenderer(colors));
        BarRenderer barrenderer = (BarRenderer) categoryplot.getRenderer();
        barrenderer.setItemLabelAnchorOffset(9D);
        barrenderer.setBaseItemLabelsVisible(true);
        barrenderer.setBaseItemLabelGenerator(new StandardCategoryItemLabelGenerator());
        barrenderer.setMaximumBarWidth(0.05);
        barrenderer.setItemMargin(0.03D);
        ItemLabelPosition itemlabelposition = new ItemLabelPosition(INSIDE12, CENTER_RIGHT, CENTER_RIGHT, -1.5707963267948966D);
        barrenderer.setBasePositiveItemLabelPosition(itemlabelposition);
        ItemLabelPosition itemlabelposition1 = new ItemLabelPosition(OUTSIDE12, CENTER_LEFT, CENTER_LEFT, -1.5707963267948966D);
        barrenderer.setPositiveItemLabelPositionFallback(itemlabelposition1);
        CategoryAxis categoryaxis = categoryplot.getDomainAxis();
        categoryaxis.setCategoryLabelPositions(CategoryLabelPositions.DOWN_45);
        categoryaxis.setCategoryMargin(0.25D);
        categoryaxis.setUpperMargin(0.02D);
        categoryaxis.setLowerMargin(0.02D);
        NumberAxis numberaxis = (NumberAxis) categoryplot.getRangeAxis();
        numberaxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        numberaxis.setUpperMargin(0.10000000000000001D);
        ChartUtilities.applyCurrentTheme(chart);
        return chart;
    }

    private static List<Stats.Counter> sort(Collection<Stats.Counter> original, int maxSize, int total) {
        List<Stats.Counter> sorted = sort(original);
        if (sorted.size() > maxSize) {
            int remainder = 0;
            for (Stats.Counter counter : sorted.subList(MAX_BAR_CHART_SIZE, sorted.size())) remainder += counter.count;
            sorted = sorted.subList(0, MAX_BAR_CHART_SIZE);
            if (remainder > 0) {
                Stats.Counter counter = new Stats.Counter();
                counter.count = remainder;
                counter.value = "+";
                counter.setTotal(total);
                sorted.add(counter);
            }
        }
        return sorted;
    }

    private static List<Stats.Counter> sort(Collection<Stats.Counter> original) {
        List<Stats.Counter> sorted = new ArrayList<Stats.Counter>(original.size());
        sorted.addAll(original);
        Collections.sort(sorted, new Comparator<Stats.Counter>() {
            @Override
            public int compare(Stats.Counter a, Stats.Counter b) {
                return Integer.valueOf(a.value).compareTo(Integer.valueOf(b.value));
            }
        });
        return sorted;
    }

    static class CustomRenderer extends BarRenderer {
        private Paint colors[];

        public CustomRenderer(Paint colors[]) {
            this.colors = colors;
        }

        public Paint getItemPaint(int i, int j) {
            return colors[j % colors.length];
        }

    }

}
