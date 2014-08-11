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
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Gerald de Jong <gerald@delving.eu>
 */

class NarthexDataSetTableRow implements Comparable<NarthexDataSetTableRow> {
    private final SipModel sipModel;
    private final NetworkClient.Sip sip;
    private DataSet dataSet;

    NarthexDataSetTableRow(SipModel sipModel, NetworkClient.Sip sip, DataSet dataSet) {
        this.sipModel = sipModel;
        this.sip = sip;
        this.dataSet = dataSet;
    }

    public boolean isDownloadable() {
        if (dataSet == null) return true;
//        System.out.println(String.format("[%s][%s]", dataSet.getNarthexSipZipName(), sip.file));
        return !dataSet.getNarthexSipZipName().equals(sip.file);
    }

    public String getFileName() {
        return sip.file;
    }

    public String getSpec() {
        return sip.facts.spec;
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
            name = sip.facts.name;
        }
        if (name == null) name = "";
        return name;
    }

    public String getOrganization() {
        return sip.facts.orgId;
    }

    public DateTime getDateTime() {
        return new DateTime(); // todo: get it from the parse
    }

    public String getUploadedBy() {
        return "Gumby"; // todo: get it from parse
    }

    public List<SchemaVersion> getSchemaVersions() {
        if (dataSet != null) {
            return dataSet.getSchemaVersions();
        }
        else if (sip.facts.schemaVersions != null) {
            List<SchemaVersion> list = new ArrayList<SchemaVersion>();
            for (NetworkClient.SchemaVersionTag schemaVersionTag : sip.facts.schemaVersions) {
                list.add(new SchemaVersion(schemaVersionTag.prefix, schemaVersionTag.version));
            }
            return list;
        }
        else {
            return null;
        }
    }

    public String getDataSetState(SchemaVersion schemaVersion) {
        if (dataSet == null) return DataSetState.NO_DATA.toString();
        return dataSet.getState(schemaVersion.getPrefix()).toString();
    }

    @Override
    public int compareTo(NarthexDataSetTableRow row) {
        return -getDateTime().compareTo(row.getDateTime());
    }

    public String toString() {
        return String.format("Narthex(%s)", getSpec());
    }

    private boolean isBusy() {
        return sipModel.getWorkModel().isDataSetBusy(getSpec());
    }

}
