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

package eu.delving;

import eu.delving.metadata.Path;
import eu.delving.stats.ChartHelper;
import eu.delving.stats.Stats;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;

import javax.imageio.ImageWriteParam;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Make charts in the form of images from Stats, so that these charts can also appear on the server-side.
 *
 *
 */

public class ChartEngine {
    private ChartHelper chartHelper;
    private int width, height;

    public ChartEngine(String statsXML, String name, int width, int height) {
        Stats stats = Stats.read(new ByteArrayInputStream(statsXML.getBytes()));
        chartHelper = new ChartHelper(stats, "result", name);
        this.width = width;
        this.height = height;
    }

    public void setPath(Path path) {
        chartHelper.setPath(path);
    }

    public boolean hasFrequencyChart() {
        return chartHelper.hasFrequencyChart();
    }

    public void writeFieldFrequencyPNG(OutputStream outputStream) throws IOException {
        writePNG(outputStream, chartHelper.getFieldFrequencyChart());
    }

    public boolean hasWordCountChart() {
        return chartHelper.hasWordCountChart();
    }

    public void writeWordCountChartPNG(OutputStream outputStream) throws IOException {
        writePNG(outputStream, chartHelper.getWordCountChart());
    }

    public boolean hasPresentAbsentChart() {
        return chartHelper.hasPresentAbsentChart();
    }

    public void writePresentAbsentChart(OutputStream outputStream) throws IOException {
        if (chartHelper.getPresenceCharts().length == 1) {
            writePNG(outputStream, chartHelper.getPresenceCharts()[0]);
        }
        else {
            writePNG(outputStream, chartHelper.getPresenceCharts()[0]);
            System.out.println("### multiple charts, only giving the first");
        }
    }

    public boolean hasUniqueFieldCountChart() {
        return chartHelper.hasFieldCountChart();
    }

    public void writeUniqueFieldCountChart(OutputStream outputStream) throws IOException {
        writePNG(outputStream, chartHelper.getFieldCountChart());
    }

    private void writePNG(OutputStream outputStream, JFreeChart chart) throws IOException {
        ChartUtilities.writeChartAsPNG(outputStream, chart, width, height, true, ImageWriteParam.MODE_DEFAULT);
    }
}
