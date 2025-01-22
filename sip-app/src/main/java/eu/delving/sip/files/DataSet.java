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
import org.apache.jena.graph.Graph;

import javax.xml.validation.Validator;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * An individual data set within the Storage
 *
 *
 */

public interface DataSet extends Comparable<DataSet> {

    File getSipFile();

    String getSpec();

    SchemaVersion getSchemaVersion();

    RecDef getRecDef() throws StorageException;

    Validator newValidator() throws StorageException;

    Graph newShape() throws StorageException;

    DataSetState getState();

    Map<String, String> getDataSetFacts();

    Map<String, String> getHints();

    void setHints(Map<String, String> hints) throws StorageException;

    void deleteResults();

    File getSourceFile();

    InputStream openSourceInputStream() throws StorageException;

    File targetOutput() throws StorageException;

    Stats getStats();

    void setStats(Stats stats) throws StorageException;

    RecMapping getRecMapping(RecDefModel recDefModel) throws StorageException;

    RecMapping revertRecMapping(File previousMappingFile, RecDefModel recDefModel) throws StorageException;

    void setRecMapping(RecMapping recMapping, boolean freeze) throws StorageException;

    List<File> getRecMappingFiles() throws StorageException;

    ReportWriter openReportWriter(String prefix, Date time) throws StorageException;

    void finishReportWriter(String prefix, Date time) throws StorageException;

    ReportFile getReport() throws StorageException;

    void clean(int maxHistory) throws StorageException;

    OutputStream openProcessedOutputStream(String prefix, Date time) throws StorageException;

    void finishProcessedOutput(String prefix, Date time) throws StorageException;

    void deleteSource();

    void fromSipZip(File sipZipFile, ProgressListener progressListener) throws IOException, StorageException;

    File toSipZip(boolean sourceIncluded) throws StorageException;

    void remove() throws StorageException;

}
