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

    String IMPORTED_FILE_NAME = "imported.xml.gz";
    String SOURCE_FILE_NAME = "source.xml.gz";
    String UNZIPPED_SOURCE_FILE_NAME = "records.xml";
    String ANALYSIS_STATS_FILE_NAME = "analysis_stats.ser";
    String SOURCE_STATS_FILE_NAME = "source_stats.ser";
    String FACTS_FILE_NAME = "dataset_facts.txt";
    String HINTS_FILE_NAME = "hints.txt";
    String PHANTOM_FILE_NAME = "phantom.txt";
    String MAPPING_FILE_PATTERN = "mapping_%s.xml";
    String MAPPING_FILE_PREFIX = "mapping_";
    String MAPPING_FILE_SUFFIX = ".xml";
    String VALIDATION_FILE_PATTERN = "validation_%s.int";
    String VALIDATION_FILE_PREFIX = "validation_";
    String REPORT_FILE_PATTERN = "report_%s.txt";
    String RECORD_DEFINITION_FILE_SUFFIX = "-record-definition.xml";
    String FACT_DEFINITION_FILE_NAME = "fact-definition-list.xml";

    String ENVELOPE_TAG = "delving-sip-source";
    String RECORD_TAG = "input";
    String RECORD_ROOT_PATH = "recordRootPath";
    String RECORD_COUNT = "recordCount";
    String UNIQUE_ELEMENT_PATH = "uniqueElementPath";
    String HARVEST_URL = "harvestUrl";
    String HARVEST_PREFIX = "harvestPrefix";
    Path RECORD_ROOT = new Path(String.format("/%s/%s", ENVELOPE_TAG, RECORD_TAG));
    Path UNIQUE_ELEMENT = new Path(String.format("/%s/%s/@id", ENVELOPE_TAG, RECORD_TAG));
}
