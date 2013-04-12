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

package eu.delving.stats;

import org.mapdb.DBMaker;

import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class Uniqueness {
    private static final int HOLD_THRESHOLD = 5000;
    private Set<String> all = new HashSet<String>(HOLD_THRESHOLD * 3 / 2);
    private boolean exceedsThreshold;
    private int maxValueSize;

    public Uniqueness(int maxValueSize) {
        this.maxValueSize = maxValueSize;
    }

    public boolean isStillUnique(String value) {
        if (value.length() > maxValueSize) value = value.substring(0, maxValueSize);
        if (all.contains(value)) return false;
        all.add(value);
        if (!exceedsThreshold && all.size() > HOLD_THRESHOLD) {
            Set<String> dbSet = DBMaker.newTempHashSet();
            dbSet.addAll(all);
            all = dbSet;
            exceedsThreshold = true;
        }
        return true;
    }

    public int getSize() {
        if (all == null) return 0;
        return all.size();
    }
}