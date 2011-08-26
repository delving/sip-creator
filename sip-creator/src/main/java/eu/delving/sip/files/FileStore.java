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

import eu.delving.metadata.FieldStatistics;
import eu.delving.metadata.RecordMapping;
import eu.delving.sip.ProgressListener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

/**
 * This interface describes how files are stored by the sip-creator
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public interface FileStore {

    void setTemplate(String name, RecordMapping recordMapping) throws FileStoreException;

    Map<String, RecordMapping> getTemplates() throws FileStoreException;

    void deleteTemplate(String name) throws FileStoreException;

    Map<String, DataSetStore> getDataSetStores();

    DataSetStore createDataSetStore(String spec) throws FileStoreException;

    public interface DataSetStore {

        String getSpec();

        Map<String,String> getDataSetFacts();

        Map<String, String> getHints();

        void setHints(Map<String, String> hints) throws FileStoreException;

        InputStream getImportedInputStream() throws FileStoreException;

        InputStream getSourceInputStream() throws FileStoreException;

        List<FieldStatistics> getStatistics();

        void setStatistics(List<FieldStatistics> fieldStatisticsList) throws FileStoreException;

        RecordMapping getRecordMapping(String metadataPrefix) throws FileStoreException;

        void setRecordMapping(RecordMapping recordMapping) throws FileStoreException;

        void remove() throws FileStoreException;

        List<File> getUploadFiles() throws FileStoreException;

        File getImportedFile();

        File getValidationFile(RecordMapping recordMapping);

        void importSource(File inputFile, ProgressListener progressListener) throws FileStoreException;

        void convertSource(ProgressListener progressListener) throws FileStoreException;

        void acceptSipZip(ZipInputStream zipInputStream, ProgressListener progressListener) throws IOException, FileStoreException;
    }

    String IMPORTED_FILE_NAME = "imported.xml.gz";
    String SOURCE_FILE_NAME = "source.xml.gz";
    String STATISTICS_FILE_NAME = "statistics.ser";
    String FACTS_FILE_NAME = "facts.txt";
    String HINTS_FILE_NAME = "hints.txt";
    String MAPPING_FILE_PATTERN = "mapping_%s.xml";
    String MAPPING_FILE_PREFIX = "mapping_";
    String MAPPING_FILE_SUFFIX = ".xml";
    String VALIDATION_FILE_PATTERN = "validation_%s.xml";

    String RECORD_ROOT_PATH = "recordRootPath";
    String RECORD_COUNT = "recordCount";
    String UNIQUE_ELEMENT_PATH = "uniqueElementPath";
}
