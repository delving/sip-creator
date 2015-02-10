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

import eu.delving.metadata.Path;

import java.io.File;
import java.util.Map;

/**
 * This interface describes how files are maintained by the SIP-Creator
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public interface Storage {

    String getUsername();

    String getHostPort();

    File cache(String fileName);

    Map<String, DataSet> getDataSets();

    DataSet createDataSet(String spec, String organization) throws StorageException;

    enum FileType {
        IMPORTED("imported.xml.gz"),
        SOURCE("source.xml.gz", null, null, null, 2),
        IMPORT_STATS("stats-import.xml.gz"),
        SOURCE_STATS("stats-source.xml.gz"),
        FACTS("dataset_facts.txt"),
        HINTS("hints.txt"),
        MAPPING(null, "mapping_", ".xml", "mapping_%s.xml", 30),
        VALIDATION(null, "validation_", ".int", "validation_%s.int", 1),
        RESULT_STATS(null, "stats-result_", ".xml.gz", "stats-result_%s.xml.gz", 1),
        REPORT(null, "report_", null, "report_%s.txt", 1),
        REPORT_INDEX(null, "report_", null, "report_%s.long", 1),
        REPORT_CONCLUSION(null, "report-conclusion_", null, "report-conclusion_%s.txt", 1);


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

        public String getName(String prefix) {
            if (pattern == null) throw new RuntimeException("No pattern");
            return String.format(pattern, prefix);
        }

        public String getPrefix() {
            return prefix;
        }

        public String getSuffix() {
            return suffix;
        }

        public int getHistorySize() {
            return historySize;
        }
    }

    String HARVEST_TAG = "delving-harvest";
    String ENVELOPE_TAG = "delving-sip-source";
    String TEXT_TAG = "text_chunk";
    String OUTPUT_TAG = "delving-output";
    String FACTS_TAG = "facts";
    String CONSTANT_TAG = "constant";
    String UNIQUE_ATTR = "id";
    String RECORD_TAG = "input";
    String RECORD_ROOT_PATH = "recordRootPath";
    String RECORD_COUNT = "recordCount";
    String UNIQUE_ELEMENT_PATH = "uniqueElementPath";
    String MAX_UNIQUE_VALUE_LENGTH = "maxUniqueValueLength";
    String UNIQUE_VALUE_CONVERTER = "uniqueValueConverter";
    String SCHEMA_VERSIONS = "schemaVersions";
    String HARVEST_URL = "harvestUrl";
    String HARVEST_PREFIX = "harvestPrefix";
    String HARVEST_SPEC = "harvestSpec";
    String CACHE_DIR = "Cache";
    String MEDIA_DIR = "Media";
    String INDEX_FILE = "media-files.xml";
    String HELP_FILE = "help.html";
    String FRAME_ARRANGEMENTS_FILE = "frame-arrangements.xml";
    String STANDALONE_DIR = "StandaloneDataSets";
    Path RECORD_ROOT = Path.create(String.format("/%s/%s", ENVELOPE_TAG, RECORD_TAG));
    Path UNIQUE_ELEMENT = Path.create(String.format("/%s/%s/@%s", ENVELOPE_TAG, RECORD_TAG, UNIQUE_ATTR));
    Path CONSTANT_PATH = Path.create(String.format("/%s", CONSTANT_TAG));
    long MAPPING_FREEZE_INTERVAL = 60000;
}
