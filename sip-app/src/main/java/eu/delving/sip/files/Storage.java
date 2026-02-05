/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.sip.files;

import eu.delving.metadata.Path;

import java.io.File;
import java.util.Map;

/**
 * This interface describes how files are maintained by the SIP-Creator
 *
 *
 */

public interface Storage {

    File cache(String fileName);

    Map<String, DataSet> getDataSets();

    DataSet createDataSet(String sipFileName) throws StorageException;

    DataSet reloadDataSet(DataSet dataSet);

    enum FileType {
        SOURCE("source.xml.gz", null, null, null, 2),
        SOURCE_STATS("stats-source.xml.gz"),
        FACTS("narthex_facts.txt"),
        HINTS("hints.txt"),
        MAPPING(null, "mapping_", ".xml", "mapping_%s.xml", 30),
        REPORT(null, "report_", null, "report_%s.txt", 1),
        REPORT_INDEX(null, "report_", null, "report_%s.long", 1),
        REPORT_CONCLUSION(null, "report-conclusion_", null, "report-conclusion_%s.txt", 1),
        SOURCE_ZSTD("source.xml.zst", null, null, null, 2),
        REPORT_JSON("report.json", "report_", null, "report_%s.json", 10),
        PROCESSED("processed.rdf.zst", "processed_", null, "processed_%s.rdf.zst", 10),
        SOURCE_STATS_ZSTD("stats-source.xml.zst"),
        FACTS_JSON("sip.json");

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

        public String getName(String sub) {
            if (pattern == null) throw new RuntimeException("No pattern");
            return String.format(pattern, sub);
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

    String FACTS_TAG = "facts";
    String CONSTANT_TAG = "constant";
    String MAX_UNIQUE_VALUE_LENGTH = "maxUniqueValueLength";
    String UNIQUE_VALUE_CONVERTER = "uniqueValueConverter";
    String SCHEMA_VERSIONS = "schemaVersions";
    String XSD_VALIDATION = "xsdValidation";
    String SHACL_VALIDATION = "shaclValidation";
    String THEME_MODE = "themeMode";
    String TELEMETRY_ENABLED = "telemetryEnabled";
    String NARTHEX_URL = "narthexUrl";
    String NARTHEX_USERNAME = "narthexUsername";
    String NARTHEX_PASSWORD = "narthexPassword";
    String SOURCE_INCLUDED = "sourceIncluded";
    String NARTHEX_DATASET_NAME = "narthexDatasetName";
    String NARTHEX_PREFIX = "narthexPrefix";
    String CACHE_DIR = "__cache__";
    String FRAME_ARRANGEMENTS_FILE = "frame-arrangements.xml";

    String POCKETS = "pockets";
    String POCKET = "pocket";
    String POCKET_ID = "id";
    Path RECORD_CONTAINER = Path.create(String.format("/%s/%s", POCKETS, POCKET));
    Path UNIQUE_ELEMENT = Path.create(String.format("/%s/%s/@%s", POCKETS, POCKET, POCKET_ID));

    int MAPPING_SAVE_DELAY = 1000;
    int MAPPING_FREEZE_INTERVAL = 60000;
}
