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

import eu.delving.schema.SchemaVersion;
import eu.delving.sip.base.NetworkClient;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.model.SipModel;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Gerald de Jong <gerald@delving.eu>
 */

class HubDataSetTableRow implements Comparable<HubDataSetTableRow> {
    private final SipModel sipModel;
    private NetworkClient.DataSetEntry dataSetEntry;
    private DataSet dataSet;
    private HubDataSetState previousHubDataSetState;

    HubDataSetTableRow(SipModel sipModel, NetworkClient.DataSetEntry dataSetEntry, DataSet dataSet) {
        this.sipModel = sipModel;
        this.dataSetEntry = dataSetEntry;
        this.dataSet = dataSet;
        this.previousHubDataSetState = getState();
    }

    public String getOrganization() {
        return dataSetEntry != null ? dataSetEntry.orgId : dataSet.getOrganization();
    }

    public String getSpec() {
        return dataSetEntry != null ? dataSetEntry.spec : dataSet.getSpec();
    }

    public NetworkClient.DataSetEntry getDataSetEntry() {
        return dataSetEntry;
    }

    public DataSet getDataSet() {
        return dataSet;
    }

    public String getDataSetName() {
        String name;
        if (dataSet != null) {
            name = dataSet.getDataSetFacts().get("name");
        }
        else {
            name = dataSetEntry.name;
        }
        if (name == null) name = "";
        return name;
    }

    public List<SchemaVersion> getSchemaVersions() {
        if (dataSet != null) {
            return dataSet.getSchemaVersions();
        }
        else if (dataSetEntry.schemaVersions != null) {
            List<SchemaVersion> list = new ArrayList<SchemaVersion>();
            for (NetworkClient.SchemaVersionTag schemaVersionTag : dataSetEntry.schemaVersions) {
                list.add(new SchemaVersion(schemaVersionTag.prefix, schemaVersionTag.version));
            }
            return list;
        }
        else {
            return null;
        }
    }

    public HubDataSetState getState() {
        if (isBusy()) {
            return HubDataSetState.BUSY;
        }
        else if (dataSetEntry != null && dataSet != null) {
            if (dataSetEntry.lockedBy == null) {
                return HubDataSetState.ORPHAN_ARCHIVE;
            }
            else if (isLockedByUser()) {
                return HubDataSetState.OWNED_BY_YOU;
            }
            else { // locked by somebody else
                return HubDataSetState.ORPHAN_TAKEN;
            }
        }
        else if (dataSetEntry != null) { // dataSet is null
            if (dataSetEntry.lockedBy == null) {
                return HubDataSetState.AVAILABLE;
            }
            else if (isLockedByUser()) {
                return HubDataSetState.ORPHAN_UPDATE;
            }
            else { // locked by somebody else
                return HubDataSetState.UNAVAILABLE;
            }
        }
        else { // dataSetEntry is null
            return HubDataSetState.ORPHAN_LONELY;
        }
    }

    public String getDataSetState(SchemaVersion schemaVersion) {
        if (dataSet == null) return DataSetState.NO_DATA.toString();
        return dataSet.getState(schemaVersion.getPrefix()).toString();
    }

    @Override
    public int compareTo(HubDataSetTableRow hubRow) {
        return getSpec().compareTo(hubRow.getSpec());
    }

    public boolean hasStateChanged() {
        HubDataSetState newHubDataSetState = getState();
        if (newHubDataSetState == previousHubDataSetState) return false;
        previousHubDataSetState = newHubDataSetState;
        return true;
    }

    public String toString() {
        return String.format("DataSet(%s)", getSpec());
    }

    private boolean isBusy() {
        return sipModel.getWorkModel().isDataSetBusy(getSpec());
    }

    private boolean isLockedByUser() {
        boolean absent = dataSetEntry == null || dataSetEntry.lockedBy == null;
        return !absent && sipModel.getStorage().getUsername().equals(dataSetEntry.lockedBy.username);
    }
}
