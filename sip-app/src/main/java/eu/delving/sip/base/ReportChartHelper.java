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

package eu.delving.sip.base;

import eu.delving.sip.files.DataSet;
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

import java.awt.Color;
import java.awt.Paint;

import static org.jfree.chart.labels.ItemLabelAnchor.INSIDE12;
import static org.jfree.chart.labels.ItemLabelAnchor.OUTSIDE12;
import static org.jfree.ui.TextAnchor.CENTER_LEFT;
import static org.jfree.ui.TextAnchor.CENTER_RIGHT;

/**
 * Build charts based on stats
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class ReportChartHelper {

    public static ChartPanel createPresenceChart(DataSet dataSet, String prefix) {
        DefaultCategoryDataset data = new DefaultCategoryDataset();
        //data.addValue(counter.count, "Count", counter.value);
        data.addValue(100, "Presence", "Fake One");
        data.addValue(120, "Presence", "Fake Two");
        JFreeChart chart = ChartFactory.createBarChart(
                String.format("Field presence in %s / %s", dataSet.getSpec(), prefix),
                "Field",
                "Record count",
                data,
                PlotOrientation.VERTICAL,
                false, true, false
        );
        chart.addSubtitle(new TextTitle("Field Presence"));
        return finishBarChart(chart, new Color(218, 112, 214));
    }

    public static ChartPanel createLinkChart(DataSet dataSet, String prefix) {
        DefaultCategoryDataset data = new DefaultCategoryDataset();
        data.addValue(100, "Link", "Fake One");
        data.addValue(120, "Link", "Fake Two");
        JFreeChart chart = ChartFactory.createBarChart(
                String.format("Frequency within record in %s / %s", dataSet.getSpec(), prefix),
                "Link",
                "Frequency",
                data,
                PlotOrientation.VERTICAL,
                false, true, false
        );
        chart.addSubtitle(new TextTitle("Link Statistics"));
        return finishBarChart(chart, new Color(30, 144, 225));
    }

    private static ChartPanel finishBarChart(JFreeChart chart, Color... colors) {
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
        return wrap(chart);
    }

    private static ChartPanel wrap(JFreeChart chart) {
        return new ChartPanel(chart);
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
