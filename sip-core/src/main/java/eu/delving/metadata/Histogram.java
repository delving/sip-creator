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
import java.text.DecimalFormat;
import java.util.*;

/**
 * This class maintains a map of counters in such a way that it grows to certain
 * size and then trims itself.  This is all about keeping memory usage in check
 * since there can be very many of these around at one time.
 * <p/>
 * There are two things that can cause a histogram to give up and effectively
 * delete itself:  the number of values becomes outrageous or the amount of
 * storage becomes outrageous.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class Histogram implements Serializable {
    private static final int MAX_STORAGE_SIZE = 1024 * 512;
    private static final int MAX_SIZE = 5000;
    private static final int SAMPLE_SIZE = 15;
    private static final int MAX_VALUE_LENGTH = 60;
    private static final DecimalFormat PERCENT = new DecimalFormat("#0.00%");
    private static final double OVERSAMPLING = 1.3;
    private int total;
    private int storageSize;
    private Map<String, Counter> counterMap;
    private boolean trimmed;

    public Histogram() {
        this.counterMap = new HashMap<String, Counter>((int)(MAX_SIZE * OVERSAMPLING));
    }

    public void recordValue(String value) {
        Counter counter = counterMap.get(value);
        if (counter == null) {
            counterMap.put(value, counter = new Counter(value));
            storageSize += value.length();
        }
        counter.count++;
        total++;
    }

    public int getTotal() {
        return total;
    }

    public int getSize() {
        return counterMap.size();
    }

    public List<Counter> getFirstValues() {
        List<Counter> values = getCounters();
        return values.subList(0, Math.min(SAMPLE_SIZE, values.size()));
    }

    public Set<String> getValues() {
        if (trimmed) throw new RuntimeException("Should not be using values if the histogram is trimmed");
        return counterMap.keySet();
    }

    public void trimIfNecessary() {
        if (trimmed) return;
        if (getSize() <= MAX_SIZE) return;
        trimmed = true;
        List<Counter> counters = new ArrayList<Counter>(counterMap.values());
        Collections.sort(counters);
        counterMap.clear();
        int countDown = MAX_SIZE;
        storageSize = 0;
        for (Counter counter : counters) {
            if (countDown-- == 0) break;
            counterMap.put(counter.getValue(), counter);
            storageSize += counter.getValue().length();
        }
    }

    public boolean isTrimmed() {
        return trimmed;
    }

    public List<Counter> getCounters() {
        List<Counter> counters = new ArrayList<Counter>(counterMap.values());
        Collections.sort(counters);
        return counters;
    }

    public boolean isTooMuchData() {
        return storageSize > MAX_STORAGE_SIZE;
    }

    public class Counter implements Comparable<Counter>, Serializable {
        private String value;
        private int count;

        public Counter(String value) {
            this.value = value;
        }

        public String getValue() {
            return value.length() > MAX_VALUE_LENGTH ? value.substring(0, MAX_VALUE_LENGTH) + "..." : value;
        }

        public int getCount() {
            return count;
        }

        public String getPercentage() {
            double percent = (double) count / total;
            return PERCENT.format(percent);
        }

        @Override
        public int compareTo(Counter counter) {
            int diff = counter.count - count;
            if (diff == 0) return value.compareTo(counter.value);
            return diff;
        }

        public String toString() {
            return count + " [" + value + "] " + getPercentage();
        }
    }
}