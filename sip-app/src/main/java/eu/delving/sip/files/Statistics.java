/*
 * Copyright 2011 DELVING BV
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

package eu.delving.sip.files;

import eu.delving.metadata.FieldStatistics;
import eu.delving.sip.base.StatsTree;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Gather all the statistics together, identifying whether they are from imported or source.  Also convert one
 * to the other.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class Statistics implements Serializable {
    private boolean sourceFormat;
    private Map<String, String> namespaces;
    private List<FieldStatistics> fieldStatisticsList;

    public Statistics(Map<String,String> namespaces, List<FieldStatistics> fieldStatisticsList, boolean sourceFormat) {
        this.sourceFormat = sourceFormat;
        this.namespaces = namespaces;
        this.fieldStatisticsList = fieldStatisticsList;
        Collections.sort(this.fieldStatisticsList);
        for (FieldStatistics fieldStatistics : this.fieldStatisticsList) {
            fieldStatistics.finish();
        }
    }

    public boolean isSourceFormat() {
        return sourceFormat;
    }

    public Map<String, String> getNamespaces() {
        return namespaces;
    }

    public StatsTree createAnalysisTree() {
        return StatsTree.create(fieldStatisticsList);
    }

    public int size() {
        return fieldStatisticsList.size();
    }

    public long getElementCount() {
        long total = 0L;
        for (FieldStatistics stats : fieldStatisticsList) {
            total += stats.getTotal();
        }
        return total;
    }
}
