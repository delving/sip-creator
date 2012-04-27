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

import eu.delving.stats.ChartHelper;
import eu.delving.stats.Stats;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;

import javax.imageio.ImageWriteParam;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Make charts from Stats
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class ChartEngine {
    private ChartHelper chartHelper;
    private int width, height;

    public ChartEngine(String statsXML, String name, int width, int height) {
        Stats stats = Stats.read(new ByteArrayInputStream(statsXML.getBytes()));
        chartHelper = new ChartHelper(stats, name);
        this.width = width;
        this.height = height;
    }

    public boolean hasFrequencyChart() {
        return chartHelper.hasFrequencyChart();
    }

    public void wrieFieldFrequencyPNG(OutputStream outputStream) throws IOException {
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
        writePNG(outputStream, chartHelper.getPresentAbsentChart());
    }

    public boolean hasUniqueFieldCountChart() {
        return chartHelper.hasUniqueFieldCountChart();
    }

    public void writeUniqueFieldCountChart(OutputStream outputStream) throws IOException {
        writePNG(outputStream, chartHelper.getUniqueFieldCountChart());
    }

    private void writePNG(OutputStream outputStream, JFreeChart chart) throws IOException {
        ChartUtilities.writeChartAsPNG(outputStream, chart, width, height, true, ImageWriteParam.MODE_DEFAULT);
    }
}
