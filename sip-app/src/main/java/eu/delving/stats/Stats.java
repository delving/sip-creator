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

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import com.thoughtworks.xstream.converters.extended.ToAttributedValueConverter;
import eu.delving.metadata.Path;
import org.mapdb.DBMaker;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import static eu.delving.XStreamFactory.getStreamFor;

/**
 * Gather all the statistics together, identifying whether they are from imported or source.  Also convert one
 * to the other.
 */
@XStreamAlias("delving-statistics")
public class Stats {
    public static final int DEFAULT_MAX_UNIQUE_VALUE_LENGTH = 40;
    private static final int TOOLTIP_SIZE = 10;
    private static final int SMALL_SIZE = 100;
    private static final int SAMPLE_SIZE = 300;
    private static final int SAMPLE_MAX_VALUE_LENGTH = 100;
    private static final int HISTOGRAM_MAX_SIZE = 1000;
    private static final DecimalFormat PERCENT = new DecimalFormat("#0.00%");
    private static final double HISTOGRAM_OVERSAMPLING = 1.3;

    public static Stats read(InputStream in) {
        try {
            Reader inReader = new InputStreamReader(in, "UTF-8");
            XStream xstream = getStreamFor(Stats.class);
            Stats stats = (Stats) xstream.fromXML(inReader);
            stats.finish();
            return stats;
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static void write(Stats stats, OutputStream out) {
        try {
            Writer outWriter = new OutputStreamWriter(out, "UTF-8");
            getStreamFor(Stats.class).toXML(stats, outWriter);
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public void recordNamespace(String prefix, String uri) {
        namespaces.put(prefix, uri);
    }

    public void recordValue(Path path, String value) {
        ValueStats valueStats = fieldValueMap.get(path);
        if (valueStats == null) fieldValueMap.put(path, valueStats = new ValueStats());
        valueStats.recordOccurrence();
        if (maxUniqueValueLength == 0) maxUniqueValueLength = DEFAULT_MAX_UNIQUE_VALUE_LENGTH;
        if (value != null) valueStats.recordValue(value.trim(), maxUniqueValueLength);
        if (recordStats != null && value != null) {
            recordStats.recordNonemptyOccurrence(path);
        }
    }

    public void recordRecordEnd() {
        if (recordStats != null) recordStats.recordRecordEnd();
    }

    public void finish() {
        for (ValueStats stats : fieldValueMap.values()) stats.finish();
        if (recordStats != null) recordStats.finish();
    }

    @XStreamAsAttribute
    public String name;

    @XStreamAsAttribute
    public String prefix;

    @XStreamAsAttribute
    public int maxUniqueValueLength;

    public Map<String, String> namespaces = new HashMap<String, String>();

    public RecordStats recordStats = new RecordStats();

    @XStreamAlias("field-value-stats")
    public Map<Path, ValueStats> fieldValueMap = new HashMap<Path, ValueStats>();

    @XStreamAlias("record-stats")
    public static class RecordStats {

        @XStreamAsAttribute
        public int recordCount;

        @XStreamAlias("field-count")
        public Histogram fieldCount = new Histogram();

        @XStreamAlias("frequency-within-record")
        public Map<Path, Histogram> frequencies = new HashMap<Path, Histogram>();

        @XStreamOmitField
        private Map<Path, Integer> countPerRecord = new HashMap<Path, Integer>();

        public void recordNonemptyOccurrence(Path path) {
            Integer count = countPerRecord.get(path);
            countPerRecord.put(path, count == null ? 1 : count + 1);
        }

        public void recordRecordEnd() {
            for (Map.Entry<Path, Integer> entry : countPerRecord.entrySet()) {
                Histogram histogram = frequencies.get(entry.getKey());
                if (histogram == null) frequencies.put(entry.getKey(), histogram = new Histogram());
                histogram.recordValue(entry.getValue().toString());
            }
            fieldCount.recordValue(String.valueOf(countPerRecord.size()));
            countPerRecord.clear();
            recordCount++;
        }

        public void finish() {
            for (Histogram histogram : frequencies.values()) histogram.finish(recordCount);
            fieldCount.finish(recordCount);
        }
    }

    @XStreamAlias("value-stats")
    public static class ValueStats {

        @XStreamAsAttribute
        public int total;

        @XStreamAsAttribute
        public Boolean unique;

        @XStreamAlias("values")
        public Histogram values;

        @XStreamAlias("word-counts")
        public Histogram wordCounts;

        public Sample sample;

        @XStreamOmitField
        private Uniqueness uniqueness;

        @XStreamOmitField
        private boolean histogramTrimmed;

        public void recordOccurrence() {
            total++;
        }

        public void recordValue(String value, int maxUniqueValueLength) {
            if (value.isEmpty()) return;
            if (sample == null) sample = new Sample();
            sample.recordValue(value);
            if (wordCounts == null) wordCounts = new Histogram();
            int wordCount = 0;
            for (StringTokenizer token = new StringTokenizer(value); token.hasMoreTokens(); token.nextToken()) wordCount++;
            wordCounts.recordValue(String.valueOf(wordCount));
            if (!histogramTrimmed) {
                if (values == null) values = new Histogram();
                values.recordValue(value);
                if (values.isTrimmed()) histogramTrimmed = true;
            }
            if (unique == null || unique) {
                if (uniqueness == null) uniqueness = new Uniqueness(maxUniqueValueLength);
                unique = uniqueness.isStillUnique(value);
                if (!unique) uniqueness = null;
            }
        }

        public String getSummary() {
            if (sample != null) {
                if (unique != null && unique) return String.format("All %d values are completely unique", total);
                return (values == null ? "No" : (values.trimmed ? "Trimmed" : "Full"))+" histogram of values available";
            }
            if (total == 1) return "Element appears just once.";
            return String.format("Element appears %d times.", total);
        }

        public void finish() {
            if (values != null) {
                if (unique != null && unique) {
                    values = null;
                }
                else {
                    values.finish(total);
                }
            }
            if (wordCounts != null) wordCounts.finish(total);
        }

        public boolean isUnique() {
            return unique != null && unique;
        }
    }

    @XStreamAlias("random-sample")
    public static class Sample {

        @XStreamImplicit
        public Set<String> values = new HashSet<String>();

        public void recordValue(String value) {
            String trimmedValue = value.length() > SAMPLE_MAX_VALUE_LENGTH ? value.substring(0, SAMPLE_MAX_VALUE_LENGTH) + "..." : value;
            if (values.size() < SAMPLE_SIZE || Math.random() > 0.1) values.add(trimmedValue);
            if (values.size() > SAMPLE_SIZE * 2) {
                Iterator<String> walk = values.iterator();
                while (walk.hasNext()) {
                    walk.next();
                    if (Math.random() > 0.5) walk.remove();
                }
            }
        }

        public Set<String> getToolTip() {
            return getSelection(TOOLTIP_SIZE);
        }

        public Set<String> getDetails() {
            return getSelection(SMALL_SIZE);
        }

        private Set<String> getSelection(int size) {
            if (values.size() <= size) return values;
            List<String> list = new ArrayList<String>(values.size());
            list.addAll(values);
            Set<String> values = new HashSet<String>();
            while (values.size() < size) {
                int index = (int)(Math.random() * list.size());
                values.add(list.get(index));
                list.remove(index);
            }
            return values;
        }
    }

    @XStreamAlias("histogram")
    public static class Histogram {

        @XStreamAsAttribute
        public int present;

        @XStreamAsAttribute
        public int absent;

        @XStreamAsAttribute
        private boolean trimmed;

        @XStreamImplicit
        public Map<String, Counter> counterMap = new HashMap<String, Counter>((int) (HISTOGRAM_MAX_SIZE * HISTOGRAM_OVERSAMPLING));

        @XStreamOmitField
        private int storageSize;

        public void recordValue(String value) {
            Counter counter = counterMap.get(value);
            if (counter == null) {
                counterMap.put(value, counter = new Counter());
                counter.value = value;
            }
            storageSize += value.length();
            counter.count++;
            if (counterMap.size() > HISTOGRAM_MAX_SIZE * 2) {
                List<Counter> counters = new ArrayList<Counter>(counterMap.values());
                Collections.sort(counters);
                counterMap.clear();
                int countDown = HISTOGRAM_MAX_SIZE;
                storageSize = 0;
                for (Counter c : counters) {
                    if (countDown-- == 0) break;
                    counterMap.put(c.value, c);
                    storageSize += c.value.length();
                }
                trimmed = true;
            }
        }

        public boolean isTrimmed() {
            return trimmed;
        }

        public void finish(int total) {
            present = 0;
            for (Map.Entry<String, Counter> entry : counterMap.entrySet()) {
                present += entry.getValue().count;
                entry.getValue().value = entry.getKey();
                entry.getValue().setTotal(total);
            }
            absent = total - present;
        }

        public List<Counter> getToolTip() {
            return getSelection(TOOLTIP_SIZE);
        }

        public List<Counter> getDetails() {
            return getSelection(SMALL_SIZE);
        }

        private List<Counter> getSelection(int size) {
            List<Counter> counters = new ArrayList<Counter>(counterMap.values());
            Collections.sort(counters);
            return counters.size() <= size ? counters : counters.subList(0, size);
        }
    }

    @XStreamAlias("count")
    @XStreamConverter(value = ToAttributedValueConverter.class, strings = {"value"})
    public static class Counter implements Comparable<Counter> {
        @XStreamAsAttribute
        public int count;

        @XStreamAsAttribute
        public String percentage;

        @XStreamOmitField
        public String value;

        @XStreamOmitField
        public double proportion;

        public void setTotal(int total) {
            this.proportion = (double) count / total;
            this.percentage = PERCENT.format(proportion);
        }

        @Override
        public int compareTo(Counter counter) {
            int diff = counter.count - count;
            if (diff == 0) return value.compareTo(counter.value);
            return diff;
        }

        public String toString() {
            return count + " \"" + value + "\" " + percentage;
        }

        public Counter copy() {
            Counter fresh = new Counter();
            fresh.count = count;
            fresh.value = value;
            return fresh;
        }
    }

    public static class Uniqueness {
        private static final int HOLD_THRESHOLD = 5000;
        private Set<String> all = new HashSet<>(HOLD_THRESHOLD * 3 / 2);
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

}
