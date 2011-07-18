package eu.delving.sip;

import java.util.ArrayList;
import java.util.List;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;

/**
 * An enumeration of the responses that can be returned from the DataSetController
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

@XStreamAlias("data-set")
public class DataSetResponse {

    @XStreamAsAttribute()
    private String responseCode;

    @XStreamAlias("data-set-list")
    private List<DataSetInfo> dataSetList = new ArrayList<DataSetInfo>();

    @XStreamAlias("changed-records-keys")
    private String changedRecords = new String();

    @XStreamAlias("errorMessage")
    private String errorMessage;

    public DataSetResponse(DataSetResponseCode responseCode) {
        this.responseCode = responseCode.toString();
    }

    public DataSetResponseCode getResponseCode() {
        try {
            return DataSetResponseCode.valueOf(responseCode);
        }
        catch (Exception e) {
            return DataSetResponseCode.UNKNOWN_RESPONSE;
        }
    }

    public boolean isEverythingOk() {
        return DataSetResponseCode.THANK_YOU == getResponseCode();
    }

    public void addDataSetInfo(DataSetInfo dataSetInfo) {
        dataSetList.add(dataSetInfo);
    }

    public List<DataSetInfo> getDataSetList() {
        return dataSetList;
    }

    public String getChangedRecords() {
        return changedRecords;
    }

    public String toString() {
        return responseCode;
    }
}
