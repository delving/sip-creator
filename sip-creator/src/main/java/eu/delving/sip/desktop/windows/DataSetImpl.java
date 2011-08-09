/*
 * Copyright 2010 DELVING BV
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

package eu.delving.sip.desktop.windows;

import eu.delving.sip.DataSetInfo;

import java.io.Serializable;

/**
 * todo: add description
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class DataSetImpl implements DataSet, Serializable {

    private String name;
    private String spec;
    private String state;
    private int recordCount;


    public DataSetImpl(DataSetInfo dataSetInfo) {
        name = dataSetInfo.name;
        spec = dataSetInfo.spec;
        state = dataSetInfo.state;
        recordCount = dataSetInfo.recordCount;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getSpec() {
        return spec;
    }

    @Override
    public String getState() {
        return state;
    }

    @Override
    public int getRecordCount() {
        return recordCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataSetImpl dataSet = (DataSetImpl) o;
        return spec.equals(dataSet.spec);
    }

    @Override
    public int hashCode() {
        return spec.hashCode();
    }
}
