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

package eu.delving.sip.files;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import eu.delving.groovy.MetadataRecord;
import eu.delving.groovy.XmlNodePrinter;
import eu.delving.metadata.MappingResult;

import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.commons.io.FileUtils.deleteQuietly;

/**
 * Describes how a report is written during dataset processing
 *
 *
 */

public class ReportWriter {
    private File reportJsonFile;
    private JsonGenerator json = null;
    private Map<ReportType, AtomicInteger> counters = new HashMap<>();

    public enum ReportType {
        INVALID,
        DISCARDED,
        UNEXPECTED,
        WARNING
    }

    public ReportWriter(File reportJsonFile)
            throws IOException, XPathExpressionException {
        this.reportJsonFile = reportJsonFile;
        if (reportJsonFile != null) {
            JsonFactory jsonFactory = JsonFactory.builder().build();
            json = jsonFactory.createGenerator(reportJsonFile, JsonEncoding.UTF8);
            json.writeStartObject();
            json.writeFieldName("records");
            json.writeStartObject();
        }
        for (ReportType reportType : ReportType.values()) {
            counters.put(reportType, new AtomicInteger(0));
        }
    }

    public void warn(MetadataRecord inputRecord, MappingResult mappingResult, List<String> events,
                     Map<String, String> facts) throws IOException {
        counters.get(ReportType.WARNING).incrementAndGet();
        if (json != null) {
            json.writeFieldName(inputRecord.getId());
            json.writeStartObject();
            json.writeFieldName("type");
            json.writeString(ReportType.WARNING.name());
            json.writeFieldName("recordNumber");
            json.writeNumber(inputRecord.getRecordNumber());
            json.writeFieldName("message");
            json.writeString(events.size() + " warning(s) for " + inputRecord.getId());
            json.writeFieldName("warnings");
            json.writeStartArray();
            for (String event : events) {
                json.writeString(event);
            }
            json.writeEndArray();
            if (mappingResult != null) {
                json.writeFieldName("output");
                json.writeString(toXml(mappingResult, facts));
            }
            json.writeEndObject();
        }
    }

    public void invalid(MetadataRecord inputRecord, MappingResult mappingResult, Exception e,
                        Map<String, String> facts) throws IOException {
        counters.get(ReportType.INVALID).incrementAndGet();
        if (json != null) {
            json.writeFieldName(inputRecord.getId());
            json.writeStartObject();
            json.writeFieldName("type");
            json.writeString(ReportType.INVALID.name());
            json.writeFieldName("recordNumber");
            json.writeNumber(inputRecord.getRecordNumber());
            json.writeFieldName("error");
            json.writeString(e.getClass().getCanonicalName());
            json.writeFieldName("message");
            json.writeString(e.getMessage());
            if (mappingResult != null) {
                json.writeFieldName("output");
                json.writeString(toXml(mappingResult, facts));
            }
            json.writeEndObject();
        }
    }

    public void discarded(MetadataRecord inputRecord, MappingResult mappingResult, Exception e,
                          Map<String, String> facts) throws IOException {
        counters.get(ReportType.DISCARDED).incrementAndGet();
        if (json != null) {
            json.writeFieldName(inputRecord.getId());
            json.writeStartObject();
            json.writeFieldName("type");
            json.writeString(ReportType.DISCARDED.name());
            json.writeFieldName("recordNumber");
            json.writeNumber(inputRecord.getRecordNumber());
            json.writeFieldName("message");
            json.writeString(e.getMessage());
            json.writeFieldName("input");
            json.writeString(toXml(inputRecord));
            json.writeEndObject();
        }
    }

    public void unexpected(MetadataRecord inputRecord, MappingResult mappingResult, Exception e,
                           Map<String, String> facts) throws IOException {
        counters.get(ReportType.UNEXPECTED).incrementAndGet();
        if (json != null) {
            json.writeFieldName(inputRecord.getId());
            json.writeStartObject();
            json.writeFieldName("type");
            json.writeString(ReportType.UNEXPECTED.name());
            json.writeFieldName("recordNumber");
            json.writeNumber(inputRecord.getRecordNumber());
            json.writeFieldName("message");
            json.writeString(e.getMessage());
            json.writeFieldName("input");
            json.writeString(toXml(inputRecord));
            json.writeEndObject();
        }
    }

    public void abort() {
        try {
            if (json != null) {
                json.close();
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to close", e);
        }
        deleteQuietly(reportJsonFile);
    }

    public void finish(int totalCount, int processedCount) {
        try {
            if (json != null) {
                json.writeEndObject();
                json.writeFieldName("conclusions");
                json.writeStartObject();
                json.writeFieldName("total");
                json.writeNumber(totalCount);
                json.writeFieldName("processed");
                json.writeNumber(processedCount);
                for (ReportType reportType : ReportType.values()) {
                    json.writeFieldName(reportType.name());
                    json.writeNumber(counters.get(reportType).get());
                }
                json.writeEndObject();
                json.writeEndObject();
                json.close();
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to finish report", e);
        }
    }

    private String toXml(MappingResult result, Map<String, String> facts) {
        return result.toXml(facts.getOrDefault("orgId", "unknown"),
            facts.getOrDefault("spec", "unknown"));
    }

    private String toXml(MetadataRecord inputRecord) {
        return XmlNodePrinter.toXml(inputRecord.getRootNode());
    }

}
