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

import eu.delving.metadata.AnalysisTree;
import eu.delving.metadata.FieldStatistics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Gather all the statistics together, identifying whether they are from imported or source.  Also convert one
 * to the other.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class Statistics implements Serializable {
    private boolean sourceFormat;
    private List<FieldStatistics> fieldStatisticsList = new ArrayList<FieldStatistics>();

    public Statistics(Collection<FieldStatistics> statsList, boolean sourceFormat) {
        this.sourceFormat = sourceFormat;
        fieldStatisticsList.addAll(statsList);
        Collections.sort(fieldStatisticsList);
        for (FieldStatistics fieldStatistics : fieldStatisticsList) {
            fieldStatistics.finish();
        }
    }

    public boolean isSourceFormat() {
        return sourceFormat;
    }

    public AnalysisTree createAnalysisTree() {
        return AnalysisTree.create(fieldStatisticsList);
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
