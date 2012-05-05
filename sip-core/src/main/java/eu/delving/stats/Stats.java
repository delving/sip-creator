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
import com.thoughtworks.xstream.annotations.*;
import com.thoughtworks.xstream.converters.extended.ToAttributedValueConverter;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import eu.delving.metadata.Path;
import eu.delving.metadata.Tag;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;

/**
 * Gather all the statistics together, identifying whether they are from imported or source.  Also convert one
 * to the other.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

@XStreamAlias("delving-statistics")
public class Stats {
    public static final int DEFAULT_MAX_UNIQUE_VALUE_LENGTH = 40;
    private static final int SAMPLE_SIZE = 200;
    private static final int SAMPLE_SMALL_SIZE = 30;
    private static final int SAMPLE_MAX_VALUE_LENGTH = 100;
    private static final int HISTOGRAM_MAX_STORAGE = 1024 * 512;
    private static final int HISTOGRAM_MAX_SIZE = 1000;
    private static final DecimalFormat PERCENT = new DecimalFormat("#0.00%");
    private static final double HISTOGRAM_OVERSAMPLING = 1.3;

    public static Stats read(InputStream in) {
        try {
            Reader inReader = new InputStreamReader(in, "UTF-8");
            Stats stats = (Stats) stream().fromXML(inReader);
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
            stream().toXML(stats, outWriter);
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public void recordNamespace(String prefix, String uri) {
        namespaces.put(prefix, uri);
    }

    public void setRecordRoot(Path recordRoot) {
        this.recordRoot = recordRoot;
        if (recordStats != null) throw new RuntimeException("Already known!");
        recordStats = new RecordStats();
    }

    public void recordValue(Path path, String value) {
        ValueStats valueStats = fieldValueMap.get(path);
        if (valueStats == null) fieldValueMap.put(path, valueStats = new ValueStats());
        valueStats.recordOccurrence();
        if (maxUniqueValueLength == 0) maxUniqueValueLength = DEFAULT_MAX_UNIQUE_VALUE_LENGTH;
        if (value != null) valueStats.recordValue(value.trim(), maxUniqueValueLength);
        if (recordStats != null) {
            if (path.equals(recordRoot)) {
                recordStats.recordRecordEnd();
            }
            else if (value != null) {
                recordStats.recordNonemptyOccurrence(path);
            }
        }
    }

    public void finish() {
        for (ValueStats stats : fieldValueMap.values()) stats.finish();
        if (recordStats != null) recordStats.finish();
    }

    @XStreamAsAttribute
    public String name;

    @XStreamAsAttribute
    public boolean sourceFormat;

    @XStreamAsAttribute
    public String prefix;

    @XStreamAsAttribute
    public Path recordRoot;

    @XStreamAsAttribute
    public int maxUniqueValueLength;

    public Map<String, String> namespaces = new HashMap<String, String>();

    public RecordStats recordStats;

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
        private boolean histogramExploded;

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
            if (!histogramExploded) {
                if (values == null) values = new Histogram();
                if (!values.recordValue(value)) {
                    histogramExploded = true;
                }
                if (values.trimmed) histogramExploded = true;
            }
            if (unique == null || unique) {
                if (uniqueness == null) uniqueness = new Uniqueness(maxUniqueValueLength);
                unique = uniqueness.isStillUnique(value);
                if (!unique) uniqueness = null;
            }
        }

        public boolean hasValues() {
            return sample != null;
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
                if (values.counterMap.size() == 1 || unique != null && unique) {
                    values = null;
                }
                else {
                    values.finish(total);
                }
            }
            if (wordCounts != null) wordCounts.finish(total);
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

        public Set<String> getSelection() {
            if (values.size() <= SAMPLE_SMALL_SIZE) return values;
            List<String> list = new ArrayList<String>(values.size());
            list.addAll(values);
            Set<String> values = new HashSet<String>();
            while (values.size() < SAMPLE_SMALL_SIZE) {
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

        @XStreamImplicit
        public Map<String, Counter> counterMap = new HashMap<String, Counter>((int) (HISTOGRAM_MAX_SIZE * HISTOGRAM_OVERSAMPLING));

        @XStreamOmitField
        private int storageSize;

        @XStreamOmitField
        private boolean trimmed;

        public boolean recordValue(String value) {
            Counter counter = counterMap.get(value);
            if (counter == null) {
                counterMap.put(value, counter = new Counter());
                counter.value = value;
            }
            storageSize += value.length();
            counter.count++;
            trimIfNecessary();
            return storageSize <= HISTOGRAM_MAX_STORAGE;
        }

        private void trimIfNecessary() {
            if (counterMap.size() <= HISTOGRAM_MAX_SIZE) return;
            trimmed = true;
            List<Counter> counters = new ArrayList<Counter>(counterMap.values());
            Collections.sort(counters);
            counterMap.clear();
            int countDown = HISTOGRAM_MAX_SIZE;
            storageSize = 0;
            for (Counter counter : counters) {
                if (countDown-- == 0) break;
                counterMap.put(counter.value, counter);
                storageSize += counter.value.length();
            }
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
            return count + " [" + value + "] " + percentage;
        }

        public Counter copy() {
            Counter fresh = new Counter();
            fresh.count = count;
            fresh.value = value;
            return fresh;
        }
    }

    private static XStream stream() {
        XStream stream = new XStream(new PureJavaReflectionProvider());
        stream.registerConverter(new Tag.Converter());
        stream.registerConverter(new Path.Converter());
        stream.processAnnotations(Stats.class);
        return stream;
    }

}
