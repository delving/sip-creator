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
    private static final int SAMPLE_SIZE = 15;
    private static final int MAX_VALUE_LENGTH = 40;
    private static final DecimalFormat PERCENT = new DecimalFormat("#0.00%");
    private static final double OVERSAMPLING = 1.2;
    private int maxStorageSize, maxSize;
    private int total;
    private int storageSize;
    private Map<String, Counter> counterMap = new HashMap<String, Counter>();
    private boolean trimmed;

    public Histogram(int maxStorageSize, int maxSize) {
        this.maxStorageSize = maxStorageSize;
        this.maxSize = maxSize;
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

    public int getMaxSize() {
        return maxSize;
    }

    public int getMaxStorageSize() {
        return maxStorageSize;
    }

    public int getSize() {
        return counterMap.size();
    }

    public List<Counter> getFirstValues() {
        List<Counter> values = new ArrayList<Counter>(SAMPLE_SIZE);
        Iterator<Counter> it = counterMap.values().iterator();
        for (int walk = 0; walk < SAMPLE_SIZE; walk++) if (it.hasNext()) values.add(it.next());
        return values;
    }

    public Set<String> getValues() {
        if (trimmed) {
            throw new RuntimeException("Should not be using values if the histogram is trimmed");
        }
        return counterMap.keySet();
    }

    public boolean isTrimmed() {
        return trimmed;
    }

    public Collection<Counter> getTrimmedCounters() {
        List<Counter> counters = new ArrayList<Counter>(counterMap.values());
        Collections.sort(counters);
        int size = counters.size();
        if (size > maxSize) {
            trimmed = true;
            for (int walk = maxSize; walk < size; walk++) { // remove excess
                Counter c = counters.get(walk);
                storageSize -= c.getValue().length();
                counterMap.remove(c.getValue());
            }
            return counters.subList(0, maxSize);
        }
        else {
            return counters;
        }
    }

    public boolean isTooLarge() {
        return counterMap.size() > (int) (maxSize * OVERSAMPLING);
    }

    public boolean isTooMuchData() {
        return storageSize > maxStorageSize;
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
            if (diff == 0) {
                return value.compareTo(counter.value);
            }
            return diff;
        }

        public String toString() {
            return count + " [" + value + "] " + getPercentage();
        }
    }
}