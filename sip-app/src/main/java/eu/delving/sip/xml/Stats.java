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

package eu.delving.sip.xml;

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
    private static final int SAMPLE_SIZE = 300;
    private static final int SAMPLE_SMALL_SIZE = 30;
    private static final int SAMPLE_MAX_VALUE_LENGTH = 40;
    private static final int HISTOGRAM_MAX_STORAGE = 1024 * 512;
    private static final int HISTOGRAM_MAX_SIZE = 5000;
    private static final int HISTOGRAM_MAX_SAMPLE_SIZE = 15;
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
        if (recordStats != null) throw new RuntimeException("Already known!");
        recordStats = new RecordStats();
        recordStats.recordRoot = recordRoot;
    }

    public void recordValue(Path path, String value) {
        PathStats pathStats = fieldValueMap.get(path);
        if (pathStats == null) fieldValueMap.put(path, pathStats = new PathStats());
        pathStats.recordOccurrence();
        if (value != null) pathStats.recordValue(value.trim());
        if (recordStats != null) recordStats.recordOccurrence(path);
    }

    public void finish() {
        for (PathStats stats : fieldValueMap.values()) stats.finish();
    }

    @XStreamAsAttribute
    public boolean sourceFormat;

    @XStreamAsAttribute
    public String prefix;

    public Map<String, String> namespaces = new HashMap<String, String>();

    public RecordStats recordStats;

    @XStreamAlias("field-value-stats")
    public Map<Path, PathStats> fieldValueMap = new HashMap<Path, PathStats>();

    @XStreamAlias("record-stats")
    public static class RecordStats {

        @XStreamAsAttribute
        public Path recordRoot;

        @XStreamAsAttribute
        public int recordCount;

        @XStreamAlias("field-frequency-stats")
        public Map<Path, Histogram> fieldFrequencyStats = new HashMap<Path, Histogram>();

        @XStreamOmitField
        private Map<Path, Integer> countPerRecord = new HashMap<Path, Integer>();

        public void recordOccurrence(Path path) {
            if (path.size() < recordRoot.size()) return;
            if (!path.equals(recordRoot)) {
                Integer count = countPerRecord.get(path);
                countPerRecord.put(path, count == null ? 1 : count + 1);
            }
            else {
                for (Map.Entry<Path, Integer> entry : countPerRecord.entrySet()) {
                    Histogram histogram = fieldFrequencyStats.get(entry.getKey());
                    if (histogram == null) fieldFrequencyStats.put(entry.getKey(), histogram = new Histogram());
                    histogram.recordValue(entry.getValue().toString());
                }
                countPerRecord.clear();
                recordCount++;
            }
        }
    }

    @XStreamAlias("path-stats")
    public static class PathStats {

        @XStreamAsAttribute
        public int total;

        @XStreamAsAttribute
        public boolean unique = true;

        public Histogram histogram;

        public Sample sample;

        @XStreamOmitField
        private Uniqueness uniqueness;

        @XStreamOmitField
        private boolean histogramExploded;

        public void recordOccurrence() {
            total++;
        }

        public void recordValue(String value) {
            if (sample == null) sample = new Sample();
            sample.recordValue(value);
            if (!histogramExploded) {
                if (histogram == null) histogram = new Histogram();
                if (!histogram.recordValue(value)) {
                    histogramExploded = true;
                }
                if (histogram.trimmed) histogramExploded = true;
            }
            if (unique) {
                if (uniqueness == null) uniqueness = new Uniqueness();
                if (!uniqueness.isStillUnique(value)) {
                    unique = false;
                    uniqueness = null;
                }
            }
        }

        public boolean hasValues() {
            return sample != null;
        }

        public String getSummary() {
            if (sample != null) {
                if (unique) {
                    return String.format("All %d values are completely unique", total);
                }
                if (histogram == null) {
                    return String.format("Histogram became too large, discarded.");
                }
                if (histogram.trimmed) {
                    return String.format("Histogram begame too large, so histogram is incomplete.");
                }
                if (histogram.counterMap.size() != 1) {
                    return String.format("There were %d different values, not all unique.", histogram.counterMap.size());
                }
                Counter counter = histogram.counterMap.values().iterator().next();
                return String.format("The single value '%s' appears %d times.", counter.value, counter.count);
            }
            if (total == 1) return "Element appears just once.";
            return String.format("Element appears %d times.", total);
        }

        public void finish() {
            if (histogram != null) histogram.finish(total);
        }
    }

    @XStreamAlias("random-sample")
    public static class Sample {

        @XStreamImplicit
        public Set<String> values = new TreeSet<String>();

        public void recordValue(String value) {
            if (values.size() < SAMPLE_SIZE || Math.random() > 0.1) {
                values.add(value);
            }
            if (values.size() > SAMPLE_SIZE * 2) {
                Iterator<String> walk = values.iterator();
                while (walk.hasNext()) {
                    walk.next();
                    if (Math.random() > 0.5) {
                        walk.remove();
                    }
                }
            }
        }

        public List<String> getFirstValues() {
            List<String> first = new ArrayList<String>(SAMPLE_SMALL_SIZE);
            Iterator<String> it = values.iterator();
            for (int walk = 0; walk < SAMPLE_SMALL_SIZE; walk++) {
                if (it.hasNext()) {
                    String value = it.next();
                    first.add(value.length() > SAMPLE_MAX_VALUE_LENGTH ? value.substring(0, SAMPLE_MAX_VALUE_LENGTH) + "..." : value);
                }
            }
            return first;
        }
    }

    @XStreamAlias("histogram")
    public static class Histogram {

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

        public List<Counter> getFirstValues() {
            List<Counter> values = new ArrayList<Counter>(counterMap.size());
            values.addAll(counterMap.values());
            Collections.sort(values);
            return values.subList(0, Math.min(HISTOGRAM_MAX_SAMPLE_SIZE, values.size()));
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
            for (Map.Entry<String, Counter> entry : counterMap.entrySet()) {
                entry.getValue().value = entry.getKey();
                entry.getValue().setPercentageTotal(total);
            }
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

        public void setPercentageTotal(int total) {
            double percent = (double) count / total;
            this.percentage = PERCENT.format(percent);
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
    }

    private static XStream stream() {
        XStream stream = new XStream(new PureJavaReflectionProvider());
        stream.registerConverter(new Tag.Converter());
        stream.registerConverter(new Path.Converter());
        stream.processAnnotations(Stats.class);
        return stream;
    }

}
