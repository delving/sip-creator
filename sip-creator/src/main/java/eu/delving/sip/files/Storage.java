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

import eu.delving.metadata.Path;

import java.util.Map;

/**
 * This interface describes how files are maintained by the sip-creator
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public interface Storage {

    String getUsername();

    Map<String, DataSet> getDataSets();

    DataSet createDataSet(String spec) throws StorageException;

    enum FileType {
        IMPORTED("imported.xml.gz"),
        SOURCE("source.xml.gz", null, null, null, 2),
        ANALYSIS_STATS("analysis_stats.ser"),
        SOURCE_STATS("source_stats.ser"),
        FACTS("dataset_facts.txt"),
        HINTS("hints.txt"),
        MAPPING(null, "mapping_", ".xml", "mapping_%s.xml", 30),
        VALIDATION(null, "validation_", null, "validation_%s.int", 1),
        REPORT(null, null, null, "report_%s.txt", 1),
        RECORD_DEFINITION(null, null, "-record-definition.xml", null, 1),
        FACT_DEFINITION("fact-definition-list.xml"),
        PHANTOM("phantom.txt");

        private String name, prefix, suffix, pattern;
        private int historySize = 1;

        FileType(String name, String prefix, String suffix, String pattern, int historySize) {
            this.name = name;
            this.prefix = prefix;
            this.suffix = suffix;
            this.pattern = pattern;
            this.historySize = historySize;
        }

        FileType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public String getPrefix() {
            return prefix;
        }

        public String getSuffix() {
            return suffix;
        }

        public String getPattern() {
            return pattern;
        }

        public int getHistorySize() {
            return historySize;
        }
    }

    String ENVELOPE_TAG = "delving-sip-source";
    String UNIQUE_TAG = "delving-unique-id";
    String CONTENT_TAG = "content";
    String RECORD_TAG = "input";
    String RECORD_ROOT_PATH = "recordRootPath";
    String RECORD_COUNT = "recordCount";
    String UNIQUE_ELEMENT_PATH = "uniqueElementPath";
    String HARVEST_URL = "harvestUrl";
    String HARVEST_PREFIX = "harvestPrefix";
    String HARVEST_SPEC = "harvestSpec";
    Path RECORD_ROOT = new Path(String.format("/%s/%s", ENVELOPE_TAG, RECORD_TAG));
    Path UNIQUE_ELEMENT = new Path(String.format("/%s/%s/%s", ENVELOPE_TAG, RECORD_TAG, UNIQUE_TAG));
}
