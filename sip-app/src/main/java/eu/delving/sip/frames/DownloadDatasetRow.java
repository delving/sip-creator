/*
 * Copyright 2012 Delving BV
 *
 * Licensed under the EUPL, Version 1.0 or as soon they
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

import eu.delving.sip.base.NetworkClient;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.model.SipModel;

/**
 * @author Gerald de Jong <gerald@delving.eu>
 */

class DownloadDatasetRow {
    private final SipModel sipModel;
    private final NetworkClient.SipEntry sipEntry;
    private DataSet dataSet;

    DownloadDatasetRow(SipModel sipModel, NetworkClient.SipEntry sipEntry, DataSet dataSet) {
        this.sipModel = sipModel;
        this.sipEntry = sipEntry;
        this.dataSet = dataSet;
    }

    public boolean isDownloadable() {
        return sipEntry != null;
    }

    public String getFileName() {
        return sipEntry.file;
    }

    public String getSpec() {
        if (sipEntry == null) return dataSet.getSpec();
        return sipEntry.dataset;
    }

    public String getGeneratedTime() {
        if (sipEntry == null) return "-";
        return sipEntry.date;
    }

    public DataSet getDataSet() {
        return dataSet;
    }

    public String getDataSetState() {
        if (dataSet == null) return DataSetState.ABSENT.toString();
        return dataSet.getState().toString();
    }

    public String getDownloadTime() {
        if (dataSet == null) return "-";
        return dataSet.getTime();
    }

    public String toString() {
        return getSpec();
    }

    private boolean isBusy() {
        return sipModel.getWorkModel().isDataSetBusy(getSpec());
    }

}
