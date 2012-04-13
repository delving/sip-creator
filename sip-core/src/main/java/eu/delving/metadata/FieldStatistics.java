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

package eu.delving.metadata;

import java.io.Serializable;
import java.util.Set;

/**
 * A tree of these is built during the gathering of statistics from an XML file
 * being analyzed, one for each path encountered.  Some of the paths are to
 * elements that don't have any values (just sub-elements), and these will
 * only contain the count, and have no ValueStats available.
 * <p/>
 * Instances are serialized into a cache file which is read in when available,
 * rather than re-doing the analysis.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class FieldStatistics implements Comparable<FieldStatistics>, Serializable {
    private Path path;
    private int total;
    private ValueStats valueStats;

    public FieldStatistics(Path path) {
        this.path = path;
    }

    public void recordValue(String value) {
        if (valueStats == null) valueStats = new ValueStats();
        valueStats.recordValue(value);
    }

    public void recordOccurrence() {
        total++;
    }

    public Path getPath() {
        return path;
    }

    public int getTotal() {
        return total;
    }

    public int getApproximateSize() {
        if (valueStats == null) return 0;
        return valueStats.getApproximateSize();
    }

    public Histogram getHistogram() {
        return valueStats != null ? valueStats.histogram : null;
    }

    public RandomSample getRandomSample() {
        return valueStats != null ? valueStats.randomSample : null;
    }

    public String getSummary() {
        if (valueStats != null) return valueStats.getSummary();
        if (total == 1) return "Element appears just once.";
        return String.format("Element appears %d times.", total);
    }

    public boolean hasValues() {
        return valueStats != null;
    }

    public Set<String> getHistogramValues() {
        if (valueStats == null || valueStats.histogram == null || valueStats.histogram.isTrimmed()) return null;
        return valueStats.histogram.getValues();
    }

    public void finish() {
        if (valueStats != null) valueStats.finish();
    }

    public String toString() {
        return path + " (" + total + ")";
    }

    @Override
    public int compareTo(FieldStatistics fieldStatistics) {
        return path.compareTo(fieldStatistics.path);
    }

    private class ValueStats implements Serializable {
        RandomSample randomSample = new RandomSample();
        Histogram histogram = new Histogram();
        Uniqueness uniqueness = new Uniqueness();
        boolean uniqueValues;

        void recordValue(String value) {
            randomSample.recordValue(value);
            if (histogram != null) {
                histogram.recordValue(value);
                histogram.trimIfNecessary();
                if (histogram.isTooMuchData()) histogram = null;
            }
            if (uniqueness != null && uniqueness.isRepeated(value)) {
                uniqueness.destroy();
                uniqueness = null;
            }
        }

        public int getApproximateSize() {
            int size = randomSample.getSize();
            if (histogram != null) size += histogram.getSize();
            if (uniqueness != null) size += uniqueness.getSize();
            return size;
        }

        public void finish() {
            if (uniqueness != null) {
                Set<String> repeated = uniqueness.getRepeated();
                if (repeated.isEmpty()) uniqueValues = total > 1;
                uniqueness = null;
                if (total > 1) {
                    uniqueValues = true;
                    histogram = null;
                }
            }
            if (histogram != null) histogram.trimIfNecessary();
        }

        public String getSummary() {
            if (uniqueValues) {
                return String.format("All %d values are completely unique", total);
            }
            if (histogram == null) {
                return String.format("Histogram became too large, discarded.");
            }
            if (histogram.isTrimmed()) {
                return String.format("Histogram begame too large, so histogram is incomplete.");
            }
            if (histogram.getSize() != 1) {
                return String.format("There were %d different values, not all unique.", histogram.getSize());
            }
            Histogram.Counter counter = histogram.getCounters().iterator().next();
            return String.format("The single value '%s' appears %d times.", counter.getValue(), counter.getCount());
        }
    }
}
