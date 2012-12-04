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

import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefModel;
import eu.delving.metadata.RecMapping;
import eu.delving.schema.SchemaVersion;
import eu.delving.sip.base.ProgressListener;
import eu.delving.stats.Stats;

import javax.xml.validation.Validator;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

/**
 * An individual data set within the Storage
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public interface DataSet extends Comparable<DataSet> {

    String getSpec();

    String getOrganization();

    File getMediaDirectory();

    List<SchemaVersion> getSchemaVersions();

    RecDef getRecDef(String prefix) throws StorageException;

    Validator newValidator(String prefix) throws StorageException;

    boolean isValidated(String prefix) throws StorageException;

    DataSetState getState(String prefix);

    Map<String, String> getDataSetFacts();

    void setDataSetFacts(Map<String, String> dataSetFacts) throws StorageException;

    Map<String, String> getHints();

    void setHints(Map<String, String> hints) throws StorageException;

    boolean isRecentlyImported();

    void deleteConverted() throws StorageException;

    boolean deleteValidation(String prefix) throws StorageException;

    void deleteAllValidations() throws StorageException;

    File importedOutput();

    InputStream openImportedInputStream() throws StorageException;

    File sourceOutput();

    InputStream openSourceInputStream() throws StorageException;

    File renameInvalidSource() throws StorageException;

    File renameInvalidImport() throws StorageException;

    Stats getLatestStats();

    Stats getStats(boolean sourceFormat, String prefix);

    void setStats(Stats stats, boolean sourceFormat, String prefix) throws StorageException;

    RecMapping getRecMapping(String prefix, RecDefModel recDefModel) throws StorageException;

    RecMapping revertRecMapping(File previousMappingFile, RecDefModel recDefModel) throws StorageException;

    void setRecMapping(RecMapping recMapping, boolean freeze) throws StorageException;

    List<File> getRecMappingFiles(String prefix) throws StorageException;

    void setValidation(String metadataPrefix, BitSet validation, int recordCount) throws StorageException;

    PrintWriter openReportWriter(String prefix) throws StorageException;

    List<String> getReport(String prefix) throws StorageException;

    void deleteSource() throws StorageException;

    List<File> getUploadFiles() throws StorageException;

    void fromSipZip(InputStream inputStream, long streamLength, ProgressListener progressListener) throws IOException, StorageException;

    void remove() throws StorageException;

}

