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
import org.jfree.chart.plot.RingPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.PieDataset;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.Paint;
import java.util.Locale;
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

    public static JPanel createPresenceChart(DataSet dataSet, String prefix, int[] presence, int totalRecords) {
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

    private static JPanel finishBarChart(JFreeChart chart, Color... colors) {
        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        plot.setRangeAxisLocation(AxisLocation.BOTTOM_OR_LEFT);
        plot.setRangePannable(true);
        if (colors.length > 0) plot.setRenderer(new CustomRenderer(colors));
        BarRenderer bar = (BarRenderer) plot.getRenderer();
        bar.setItemLabelAnchorOffset(9D);
        bar.setBaseItemLabelsVisible(true);
        bar.setBaseItemLabelGenerator(new StandardCategoryItemLabelGenerator());
        bar.setMaximumBarWidth(0.05);
        bar.setItemMargin(0.03D);
        bar.setBasePositiveItemLabelPosition(new ItemLabelPosition(
                INSIDE12,
                CENTER_RIGHT,
                CENTER_RIGHT,
                -1.5707963267948966D
        ));
        bar.setPositiveItemLabelPositionFallback(new ItemLabelPosition(
                OUTSIDE12,
                CENTER_LEFT,
                CENTER_LEFT,
                -1.5707963267948966D
        ));
        CategoryAxis x = plot.getDomainAxis();
        x.setCategoryLabelPositions(
                CategoryLabelPositions.DOWN_90
        );
        x.setCategoryMargin(0.25D);
        x.setUpperMargin(0.02D);
        x.setLowerMargin(0.02D);
        NumberAxis y = (NumberAxis) plot.getRangeAxis();
        y.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        y.setUpperMargin(0.10000000000000001D);
        ChartUtilities.applyCurrentTheme(chart);
        return new ChartPanel(chart);
    }

    static class CustomRenderer extends BarRenderer {
        private Paint colors[];

        public CustomRenderer(Paint colors[]) {
            this.colors = colors;
        }

        @Override
        public Paint getItemPaint(int i, int j) {
            return colors[i % colors.length];
        }

    }

    public static JComponent createLinkChart(DataSet dataSet, String prefix, Map<RecDef.Check, LinkFile.LinkStats> linkStatsMap) {
        JPanel p = new JPanel(new GridLayout(0, 1));
        for (Map.Entry<RecDef.Check, LinkFile.LinkStats> entry : linkStatsMap.entrySet()) {
            JPanel pp = new JPanel(new GridLayout(1, 0));
            pp.setBorder(BorderFactory.createTitledBorder(entry.getKey().toString()));
            for (Map.Entry<String, PieDataset> datasetEntry : entry.getValue().createPies().entrySet()) {
                JFreeChart chart = ChartFactory.createRingChart(
                        datasetEntry.getKey(),
                        datasetEntry.getValue(),
                        true,
                        false,
                        Locale.getDefault()
                );
                RingPlot plot = (RingPlot)chart.getPlot();
                plot.setLabelGenerator(null);
                plot.setNoDataMessage("No data available");
                plot.setSectionDepth(0.34999999999999998D);
                plot.setCircular(true);
                plot.setLabelGap(0.02D);
                pp.add(new ChartPanel(chart));
            }
            p.add(pp);
        }
        return p;
    }


}
