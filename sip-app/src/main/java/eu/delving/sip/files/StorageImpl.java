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

import eu.delving.metadata.*;
import eu.delving.schema.Fetcher;
import eu.delving.schema.SchemaRepository;
import eu.delving.schema.SchemaVersion;
import eu.delving.sip.base.CancelException;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.base.SwingHelper;
import eu.delving.stats.Stats;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;
import org.apache.http.client.HttpClient;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.SAXException;

import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static eu.delving.schema.SchemaType.RECORD_DEFINITION;
import static eu.delving.schema.SchemaType.VALIDATION_SCHEMA;
import static eu.delving.sip.files.Storage.FileType.*;
import static eu.delving.sip.files.StorageHelper.*;

/**
 * This is an implementation of the Storage interface, with most of the functionality built into the inner class
 * which implements the DataSet interface.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class StorageImpl implements Storage {
    private File home;
    private SchemaFactory schemaFactory;
    private SchemaRepository schemaRepository;
    private LSResourceResolver resolver;

    public StorageImpl(File home, Fetcher fetcher, HttpClient httpClient) throws StorageException {
        this.home = home;
        if (httpClient != null) this.resolver = new CachingResourceResolver(this, httpClient);
        try {
            if (fetcher != null) this.schemaRepository = new SchemaRepository(fetcher);
        }
        catch (IOException e) {
            throw new StorageException("Unable to create Schema Repository", e);
        }
        if (!home.exists()) {
            if (!home.mkdirs()) {
                throw new StorageException(String.format("Unable to create storage directory in %s", home.getAbsolutePath()));
            }
        }
    }

    @Override
    public String getUsername() {
        return StorageFinder.getUser(home);
    }

    @Override
    public String getHostPort() {
        return StorageFinder.getHostPort(home);
    }

    @Override
    public File cache(String fileName) {
        File cacheDir = new File(home, CACHE_DIR);
        if (!cacheDir.exists() && !cacheDir.mkdirs()) throw new RuntimeException("Couldn't create cache dir");
        return new File(cacheDir, fileName);
    }

    @Override
    public Map<String, DataSet> getDataSets() {
        Map<String, DataSet> map = new TreeMap<String, DataSet>();
        File[] list = home.listFiles();
        if (list != null) {
            for (File directory : list) {
                if (!directory.isDirectory()) continue;
                if (directory.getName().equals(CACHE_DIR)) continue;
                boolean hasFiles = false;
                File[] files = directory.listFiles();
                if (files != null) {
                    for (File file : files) if (file.isFile()) hasFiles = true;
                    if (!hasFiles) continue;
                    map.put(directory.getName(), new DataSetImpl(directory));
                }
            }
        }
        return map;
    }

    @Override
    public DataSet createDataSet(String spec, String organization) throws StorageException {
        File directory = createDataSetDirectory(home, spec, organization);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new StorageException(String.format("Unable to create data set directory %s", directory.getAbsolutePath()));
        }
        return new DataSetImpl(directory);
    }

    public class DataSetImpl implements DataSet, Serializable {

        private File here;
        private Map<String, String> dataSetFacts;

        public DataSetImpl(File here) {
            this.here = here;
        }

        @Override
        public String getSpec() {
            return getSpecFromDirectory(here);
        }

        @Override
        public String getOrganization() {
            return getOrganizationFromDirectory(here);
        }

        @Override
        public File getMediaDirectory() {
            return new File(here, MEDIA_DIR);
        }

        @Override
        public List<SchemaVersion> getSchemaVersions() {
            String fact = getDataSetFacts().get(SCHEMA_VERSIONS);
            List<SchemaVersion> schemaVersions = new ArrayList<SchemaVersion>();
            if (fact == null) {
                schemaVersions = temporarilyHackedSchemaVersions(here);
            }
            else {
                for (String sv : fact.split(" *, *")) schemaVersions.add(new SchemaVersion(sv));
            }
            return schemaVersions;
        }

        @Override
        public RecDef getRecDef(String prefix) throws StorageException {
            for (SchemaVersion walk : getSchemaVersions()) {
                if (!prefix.equals(walk.getPrefix())) continue;
                return recDef(walk);
            }
            throw new StorageException("No record definition for prefix " + prefix);
        }

        @Override
        public Validator newValidator(String prefix) throws StorageException {
            for (SchemaVersion walk : getSchemaVersions()) {
                if (!prefix.equals(walk.getPrefix())) continue;
                return validator(walk);
            }
            throw new StorageException("No validation schema for prefix " + prefix);
        }

        @Override
        public boolean isValidated(String prefix) throws StorageException {
            return findLatestFile(here, VALIDATION, prefix).exists();
        }

        @Override
        public DataSetState getState(String prefix) {
            File imported = importedFile(here);
            File source = sourceFile(here);
            if (imported.exists()) {
                if (source.exists() && source.lastModified() >= imported.lastModified()) {
                    return postSourceState(source, prefix);
                }
                else {
                    return importedState(imported);
                }
            }
            else if (source.exists()) {
                return postSourceState(source, prefix);
            }
            else {
                return DataSetState.NO_DATA;
            }
        }

        private DataSetState importedState(File imported) {
            File statistics = statsFile(here, false, null);
            if (statistics.exists() && statistics.lastModified() >= imported.lastModified()) {
                return allHintsSet(getHints()) ? DataSetState.DELIMITED : DataSetState.ANALYZED_IMPORT;
            }
            else {
                return DataSetState.IMPORTED;
            }
        }

        private DataSetState postSourceState(File source, String prefix) {
            File statistics = statsFile(here, true, null);
            if (statistics.exists() && statistics.lastModified() >= source.lastModified()) {
                File mapping = findLatestFile(here, MAPPING, prefix);
                if (mapping.exists()) {
                    File validation = findLatestFile(here, VALIDATION, prefix);
                    return validation.exists() ? DataSetState.VALIDATED : DataSetState.MAPPING;
                }
                else {
                    return DataSetState.ANALYZED_SOURCE;
                }
            }
            else {
                return DataSetState.SOURCED;
            }
        }

        @Override
        public Map<String, String> getDataSetFacts() {
            try {
                if (dataSetFacts == null) dataSetFacts = readFacts(factsFile(here));
                return dataSetFacts;
            }
            catch (IOException e) {
                return new TreeMap<String, String>();
            }
        }

        @Override
        public Map<String, String> getHints() {
            try {
                return readFacts(hintsFile(here));
            }
            catch (IOException e) {
                return new TreeMap<String, String>();
            }
        }

        @Override
        public void setHints(Map<String, String> hints) throws StorageException {
            File hintsFile = new File(here, FileType.HINTS.getName());
            try {
                writeFacts(hintsFile, hints);
            }
            catch (IOException e) {
                throw new StorageException("Unable to set hints", e);
            }
        }

        @Override
        public boolean isRecentlyImported() {
            File importedFile = importedFile(here);
            File sourceFile = sourceFile(here);
            return importedFile.exists() && (!sourceFile.exists() || importedFile.lastModified() > sourceFile.lastModified());
        }

        @Override
        public void deleteConverted() throws StorageException {
            if (!isRecentlyImported()) delete(sourceFile(here));
        }

        @Override
        public boolean deleteValidation(String prefix) throws StorageException {
            boolean deleted = false;
            for (File file : validationFiles(here, prefix)) {
                delete(file);
                deleted = true;
            }
            return deleted;
        }

        @Override
        public void deleteAllValidations() throws StorageException {
            for (File file : validationFiles(here)) delete(file);
        }

        @Override
        public File importedOutput() {
            return new File(here, IMPORTED.getName());
        }

        @Override
        public InputStream openImportedInputStream() throws StorageException {
            return zipIn(findOrCreate(here, IMPORTED));
        }

        @Override
        public File sourceOutput() {
            return new File(here, SOURCE.getName());
        }

        @Override
        public InputStream openSourceInputStream() throws StorageException {
            return zipIn(sourceFile(here));
        }

        @Override
        public File renameInvalidSource() throws StorageException {
            File sourceFile = sourceFile(here);
            File renamedFile = new File(here, String.format("%s.error", sourceFile.getName()));
            try {
                FileUtils.moveFile(sourceFile, renamedFile);
            }
            catch (IOException e) {
                throw new StorageException("Unable to rename source file to error", e);
            }
            return renamedFile;
        }

        @Override
        public File renameInvalidImport() throws StorageException {
            File importFile = importedFile(here);
            File renamedFile = new File(here, String.format("%s.error", importFile.getName()));
            try {
                FileUtils.moveFile(importFile, renamedFile);
            }
            catch (IOException e) {
                throw new StorageException("Unable to rename import file to error", e);
            }
            return renamedFile;
        }

        @Override
        public Stats getLatestStats() {
            File analysis = statsFile(here, false, null);
            File source = statsFile(here, true, null);
            if (analysis.exists()) {
                if (source.exists()) {
                    return getStats(source.lastModified() >= analysis.lastModified(), null);
                }
                else {
                    return getStats(false, null);
                }
            }
            else {
                return getStats(true, null);
            }
        }

        @Override
        public Stats getStats(boolean sourceFormat, String prefix) {
            File statsFile = statsFile(here, sourceFormat, prefix);
            if (statsFile.exists()) {
                InputStream in = null;
                try {
                    in = zipIn(statsFile);
                    return Stats.read(in);
                }
                catch (Exception e) {
                    FileUtils.deleteQuietly(statsFile);
                }
                finally {
                    IOUtils.closeQuietly(in);
                }
            }
            return null;
        }

        @Override
        public void setStats(Stats stats, boolean sourceFormat, String prefix) throws StorageException {
            File statsFile = statsFile(here, sourceFormat, prefix);
            if (stats == null) {
                delete(statsFile);
            }
            else {
                OutputStream out = null;
                try {
                    out = zipOut(statsFile);
                    Stats.write(stats, out);
                }
                finally {
                    IOUtils.closeQuietly(out);
                }
            }
        }

        @Override
        public RecMapping getRecMapping(String prefix, RecDefModel recDefModel) throws StorageException {
            File file = findLatestFile(here, MAPPING, prefix);
            if (file.exists()) {
                try {
                    return RecMapping.read(file, recDefModel);
                }
                catch (Exception e) {
                    throw new StorageException(String.format("Unable to read mapping from %s", file.getAbsolutePath()), e);
                }
            }
            else {
                try {
                    for (SchemaVersion schemaVersion : getSchemaVersions()) {
                        if (prefix.equals(schemaVersion.getPrefix()))
                            return RecMapping.create(recDefModel.createRecDefTree(schemaVersion));
                    }
                    throw new StorageException("Unable to find version for " + prefix);
                }
                catch (MetadataException e) {
                    throw new StorageException("Unable to load record definition", e);
                }
            }
        }

        @Override
        public RecMapping revertRecMapping(File previousMappingFile, RecDefModel recDefModel) throws StorageException {
            try {
                RecMapping previousMapping = RecMapping.read(previousMappingFile, recDefModel);
                setRecMapping(previousMapping, false);
                return previousMapping;
            }
            catch (MetadataException e) {
                throw new StorageException("Unable to fetch previous mapping", e);
            }
        }

        @Override
        public void setRecMapping(RecMapping recMapping, boolean freeze) throws StorageException {
            File file = new File(here, FileType.MAPPING.getName(recMapping.getPrefix()));
            RecMapping.write(file, recMapping);
            if (freeze) {
                try {
                    Hasher.ensureFileHashed(file);
                }
                catch (IOException e) {
                    throw new StorageException("Unable to hash the mapping file name", e);
                }
            }
        }

        @Override
        public List<File> getRecMappingFiles(String prefix) throws StorageException {
            return findHashedMappingFiles(here, prefix);
        }

        @Override
        public void setValidation(String metadataPrefix, BitSet validation, int recordCount) throws StorageException {
            if (validation == null) {
                deleteValidation(metadataPrefix);
            }
            else {
                File file = new File(here, FileType.VALIDATION.getName(metadataPrefix));
                DataOutputStream out = null;
                try {
                    out = new DataOutputStream(new FileOutputStream(file));
                    int invalidCount = recordCount - validation.cardinality();
                    out.writeInt(invalidCount);
                    for (int index = validation.nextClearBit(0); index >= 0 && index < recordCount; index = validation.nextClearBit(index + 1)) {
                        out.writeInt(index);
                    }
                }
                catch (IOException e) {
                    throw new StorageException(String.format("Unable to save mapping to %s", file.getAbsolutePath()), e);
                }
                finally {
                    IOUtils.closeQuietly(out);
                }
            }
        }

        @Override
        public PrintWriter openReportWriter(String prefix) throws StorageException {
            File file = new File(here, FileType.REPORT.getName(prefix));
            try {
                return new PrintWriter(file);
            }
            catch (IOException e) {
                throw new StorageException("Cannot read validation report", e);
            }
        }

        @Override
        public List<String> getReport(String prefix) throws StorageException {
            try {
                File file = reportFile(here, prefix);
                return file.exists() ? FileUtils.readLines(file, "UTF-8") : null;
            }
            catch (IOException e) {
                throw new StorageException("Cannot read validation report", e);
            }
        }

        @Override
        public void deleteSource() throws StorageException {
            for (File file : findSourceFiles(here)) delete(file);
        }

        @Override
        public List<File> getUploadFiles() throws StorageException {
            try {
                List<File> files = new ArrayList<File>();
                files.add(Hasher.ensureFileHashed(hintsFile(here)));
                for (SchemaVersion schemaVersion : getSchemaVersions()) {
                    String prefix = schemaVersion.getPrefix();
                    files.add(findLatestHashed(here, MAPPING, prefix));
                    files.add(findLatestHashed(here, VALIDATION, prefix));
                    files.add(findLatestHashed(here, RESULT_STATS, prefix));
                }
                files.add(Hasher.ensureFileHashed(sourceFile(here)));
                return files;
            }
            catch (IOException e) {
                throw new StorageException("Unable to collect upload files", e);
            }
        }

        @Override
        public void fromSipZip(InputStream inputStream, long streamLength, ProgressListener progressListener) throws StorageException {
            ZipEntry zipEntry;
            byte[] buffer = new byte[BLOCK_SIZE];
            int bytesRead;
            progressListener.prepareFor((int) (streamLength / BLOCK_SIZE));
            CountingInputStream counting = new CountingInputStream(inputStream);
            ZipInputStream zipInputStream = new ZipInputStream(counting);
            String unzippedName = FileType.SOURCE.getName().substring(0, FileType.SOURCE.getName().length() - ".gz".length());
            try {
                while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                    String fileName = zipEntry.getName();
                    if (fileName.equals(unzippedName)) {
                        Hasher hasher = new Hasher();
                        File source = new File(here, FileType.SOURCE.getName());
                        GZIPOutputStream outputStream = null;
                        try {
                            outputStream = new GZIPOutputStream(new FileOutputStream(source));
                            while (-1 != (bytesRead = zipInputStream.read(buffer))) {
                                outputStream.write(buffer, 0, bytesRead);
                                progressListener.setProgress((int) (counting.getByteCount() / BLOCK_SIZE));
                                hasher.update(buffer, bytesRead);
                            }
                        }
                        finally {
                            IOUtils.closeQuietly(outputStream);
                        }
                        File hashedSource = new File(here, hasher.prefixFileName(FileType.SOURCE.getName()));
                        if (hashedSource.exists()) {
                            FileUtils.deleteQuietly(source); // already got it
                        }
                        else {
                            FileUtils.moveFile(source, hashedSource);
                        }
                    }
                    else {
                        File file = new File(here, fileName);
                        OutputStream output = null;
                        try {
                            output = new FileOutputStream(file);
                            IOUtils.copy(zipInputStream, output);
                        }
                        finally {
                            IOUtils.closeQuietly(output);
                        }
                        progressListener.setProgress((int) (counting.getByteCount() / BLOCK_SIZE));
                    }
                }
            }
            catch (CancelException e) {
                throw new StorageException("Cancellation", e);
            }
            catch (IOException e) {
                throw new StorageException("Unable to accept SipZip file", e);
            }
        }

        @Override
        public void remove() throws StorageException {
            if (here.listFiles(FILE_FILTER).length == 0) return;
            for (File file : here.listFiles(ATTIC_FILTER)) delete(file);
            String now = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
            File saveDirectory = new File(here, now);
            try {
                for (File file : here.listFiles(FILE_FILTER)) FileUtils.moveFileToDirectory(file, saveDirectory, true);
            }
            catch (IOException e) {
                throw new StorageException(String.format("Unable to save files in %s to directory %s", here.getName(), now), e);
            }
        }

        public File getHere() {
            return here;
        }

        @Override
        public String toString() {
            return getSpec();
        }
    }

    private SchemaFactory schemaFactory() {
        if (schemaFactory == null) {
            schemaFactory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
            if (resolver != null) schemaFactory.setResourceResolver(resolver);
        }
        return schemaFactory;
    }

    private RecDef recDef(SchemaVersion schemaVersion) throws StorageException {
        String fileName = schemaVersion.getFullFileName(RECORD_DEFINITION);
        try {
            File file = cache(fileName);
            if (!file.exists() || SwingHelper.isDevelopmentMode()) {
                String recDefXML = schemaRepository.getSchema(schemaVersion, RECORD_DEFINITION);
                FileUtils.write(file, recDefXML, "UTF-8");
            }
            return RecDef.read(new FileInputStream(file));
        }
        catch (IOException e) {
            throw new StorageException("Unable to load " + fileName, e);
        }
    }

    private Validator validator(SchemaVersion schemaVersion) throws StorageException {
        String fileName = schemaVersion.getFullFileName(VALIDATION_SCHEMA);
        try {
            File file = cache(fileName);
            if (!file.exists() || SwingHelper.isDevelopmentMode()) {
                String valXML = schemaRepository.getSchema(schemaVersion, VALIDATION_SCHEMA);
                FileUtils.write(file, valXML, "UTF-8");
            }
            return schemaFactory().newSchema(file).newValidator();
        }
        catch (SAXException e) {
            throw new StorageException("Unable to create a validator", e);
        }
        catch (IOException e) {
            throw new StorageException("Unable to load " + fileName, e);
        }
    }

}
