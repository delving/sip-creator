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

import eu.delving.metadata.RecDef;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.LinkFile;
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
import java.util.Map;

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

    public static ChartPanel createPresenceChart(DataSet dataSet, String prefix, int[] presence, int totalRecords) {
        DefaultCategoryDataset data = new DefaultCategoryDataset();
        int index = 0;
        for (RecDef.Check check : RecDef.Check.values()) {
            data.addValue(presence[index], "Presence", check);
            index++;
        }
        JFreeChart chart = ChartFactory.createBarChart(
                String.format("Field presence in %s / %s", dataSet.getSpec(), prefix),
                "Field",
                String.format("Record count of %d records", totalRecords),
                data,
                PlotOrientation.VERTICAL,
                false, true, false
        );
        chart.addSubtitle(new TextTitle("Field Presence"));
        return finishBarChart(chart, new Color(218, 112, 214));
    }

    public static ChartPanel createLinkChart(DataSet dataSet, String prefix, Map<RecDef.Check, LinkFile.LinkStats> linkStatsMap) {
        DefaultCategoryDataset data = new DefaultCategoryDataset();
        for (Map.Entry<RecDef.Check, LinkFile.LinkStats> entry : linkStatsMap.entrySet()) {
            RecDef.Check check = entry.getKey();
            for (Map.Entry<String, LinkFile.Counter> statusEntry : entry.getValue().httpStatus.entrySet()) {
                data.addValue(statusEntry.getValue().count, "Status:" + statusEntry.getKey(), check);
            }
            for (Map.Entry<String, LinkFile.Counter> mimeEntry : entry.getValue().mimeTypes.entrySet()) {
                data.addValue(mimeEntry.getValue().count, "MIME:" + mimeEntry.getKey(), check);
            }
            if (entry.getKey().captureSize) {
                for (Map.Entry<LinkFile.FileSizeCategory, LinkFile.Counter> sizeEntry : entry.getValue().fileSize.entrySet()) {
                    data.addValue(sizeEntry.getValue().count, "Size:" + sizeEntry.getKey(), check);
                }
            }
        }
        JFreeChart chart = ChartFactory.createBarChart(
                String.format("Frequency in %s / %s", dataSet.getSpec(), prefix),
                "Link",
                "Frequency",
                data,
                PlotOrientation.VERTICAL,
                true, true, false
        );
        chart.addSubtitle(new TextTitle("Link Statistics"));
        return finishBarChart(
                chart,
                new Color(30, 144, 225),
                new Color(68, 206, 113),
                new Color(206, 27, 148),
                new Color(206, 188, 30),
                new Color(206, 94, 6),
                new Color(72, 206, 196),
                new Color(95, 16, 206)
        );
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
            return colors[i % colors.length];
        }

    }

}
