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
import eu.delving.metadata.Path;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Gather all the statistics together, identifying whether they are from imported or source.  Also convert one
 * to the other.
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
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

    public void convertToSourcePaths(Path recordRoot, Path uniqueElement) {
        if (sourceFormat) throw new IllegalStateException("Statistics already in source format");
        Iterator<FieldStatistics> walk = fieldStatisticsList.iterator();
        String underRoot = new Path(recordRoot).pop().toString();
        while (walk.hasNext()) {
            FieldStatistics stats = walk.next();
            String pathString = stats.getPath().toString();
            if (pathString.startsWith(recordRoot.toString())) {
                if (pathString.equals(uniqueElement.toString())) {
                    walk.remove();
                }
                else {
                    String fixed = Storage.RECORD_ROOT.toString() + pathString.substring(recordRoot.toString().length());
                    stats.setPath(new Path(fixed));
                }
            }
            else if (pathString.equals(underRoot)) {
                stats.setPath(new Path(Storage.ENVELOPE_TAG));
            }
            else {
                walk.remove();
            }
        }
        sourceFormat = true;
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
