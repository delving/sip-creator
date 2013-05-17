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

package eu.delving.sip.model;

import eu.delving.schema.SchemaVersion;
import eu.delving.sip.base.HttpClientFactory;
import eu.delving.sip.base.Swing;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.LinkChecker;
import eu.delving.sip.files.ReportFile;
import eu.delving.sip.files.StorageException;
import org.apache.http.client.HttpClient;

import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A list model for showing the contents of the validation files, one for each mapping.  This class rather naively
 * just loads all of the lines of the file, which could be problematic if it is very large.  It should be made
 * more clever when time permits.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class ReportFileModel {
    private SipModel sipModel;
    private Listener listener;
    private List<ReportFile> reportFiles = new CopyOnWriteArrayList<ReportFile>();
    private HttpClient httpClient;

    public interface Listener {
        void reportsUpdated(ReportFileModel reportFileModel);
    }

    public ReportFileModel(final SipModel sipModel) {
        this.sipModel = sipModel;
        Timer timer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                for (ReportFile reportFile : reportFiles) {
                    List<ReportFile.Rec> fetch = reportFile.prepareFetch();
                    if (!fetch.isEmpty()) {
                        sipModel.exec(reportFile.fetchRecords(fetch, sipModel.getFeedback()));
                    }
                    reportFile.maintainCache();
                }
            }
        });
        timer.setRepeats(true);
        timer.start();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public List<ReportFile> getReports() {
        return reportFiles;
    }

    public void refresh() {
        if (sipModel.getDataSetModel().isEmpty()) return;
        for (ReportFile reportFile : reportFiles) reportFile.close();
        reportFiles.clear();
        DataSet dataSet = sipModel.getDataSetModel().getDataSet();
        for (SchemaVersion schemaVersion : dataSet.getSchemaVersions()) {
            try {
                ReportFile reportFile = dataSet.getReport(schemaVersion.getPrefix());
                if (reportFile == null) continue;
                reportFiles.add(reportFile);
                reportFile.setLinkChecker(new LinkChecker(getHttpClient()));
            }
            catch (StorageException e) {
                sipModel.getFeedback().alert("Cannot read report file for " + schemaVersion.getPrefix());
            }
        }
        sipModel.exec(new Swing() {
            @Override
            public void run() {
                listener.reportsUpdated(ReportFileModel.this);
            }
        });
    }

    public void shutdown() {
        for (ReportFile reportFile : reportFiles) reportFile.close();
    }

    private synchronized HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = HttpClientFactory.createLinkCheckClient();
        }
        return httpClient;
    }
}