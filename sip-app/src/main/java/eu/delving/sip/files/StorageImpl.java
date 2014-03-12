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

import eu.delving.XMLToolFactory;
import eu.delving.metadata.*;
import eu.delving.schema.SchemaRepository;
import eu.delving.schema.SchemaResponse;
import eu.delving.schema.SchemaVersion;
import eu.delving.sip.base.CancelException;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.xml.LinkCheckExtractor;
import eu.delving.stats.Stats;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.SAXException;

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPathExpressionException;
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

    public StorageImpl(File home, SchemaRepository schemaRepository, LSResourceResolver resolver) throws StorageException {
        this.home = home;
        this.schemaRepository = schemaRepository;
        this.resolver = resolver;
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
                for (String sv : fact.split(" *, *")) {
                    SchemaVersion schemaVersion = new SchemaVersion(sv);
                    if ("raw".equals(schemaVersion.getPrefix())) continue;
                    schemaVersions.add(schemaVersion);
                }
            }
            Collections.sort(schemaVersions);
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
        public boolean isProcessed(String prefix) throws StorageException {
            return findLatestFile(here, TARGET, prefix).exists();
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
                    List<File> targetFiles = findHashedPrefixFiles(here, TARGET, prefix);
                    return targetFiles.isEmpty() ? DataSetState.MAPPING : DataSetState.PROCESSED;
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
        public void setDataSetFacts(Map<String, String> dataSetFacts) throws StorageException {
            File factsFile = new File(here, FileType.FACTS.getName());
            try {
                writeFacts(factsFile, dataSetFacts);
            }
            catch (IOException e) {
                throw new StorageException("Unable to set hints", e);
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
        public boolean deleteTarget(String prefix) throws StorageException {
            boolean deleted = false;
            for (File file : targetFiles(here, prefix)) {
                delete(file);
                deleted = true;
            }
            return deleted;
        }

        @Override
        public void deleteAllTargets() throws StorageException {
            for (File file : targetFiles(here)) delete(file);
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
        public File targetOutput(String prefix) {
            return new File(here, TARGET.getName(prefix));
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
                        if (prefix.equals(schemaVersion.getPrefix())) {
                            return RecMapping.create(recDefModel.createRecDefTree(schemaVersion));
                        }
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
            return findHashedPrefixFiles(here, MAPPING, prefix);
        }

        @Override
        public ReportWriter openReportWriter(RecDef recDef) throws StorageException {
            File reportFile = new File(here, FileType.REPORT.getName(recDef.prefix));
            File reportIndexFile = new File(here, FileType.REPORT_INDEX.getName(recDef.prefix));
            try {
                LinkCheckExtractor linkCheckExtractor = new LinkCheckExtractor(recDef.fieldMarkers, new XPathContext(recDef.namespaces));
                return new ReportWriter(reportFile, reportIndexFile, linkCheckExtractor);
            }
            catch (IOException e) {
                throw new StorageException("Cannot read validation report", e);
            }
            catch (XPathExpressionException e) {
                throw new StorageException("Cannot create xpath expression", e);
            }
        }

        @Override
        public ReportFile getReport(String prefix) throws StorageException {
            try {
                File reportFile = reportFile(here, prefix);
                File reportIndexFile = reportIndexFile(here, prefix);
                File targetFile = targetFile(here, prefix);
                if (!(reportFile.exists() && reportIndexFile.exists() && targetFile.exists())) return null;
                return new ReportFile(reportFile, reportIndexFile, targetFile, linkFile(here, prefix), this, prefix);
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
                    addLatestHashed(here, MAPPING, prefix, files);
                    addLatestHashed(here, RESULT_STATS, prefix, files);
                    addLatestHashed(here, LINKS, prefix, files);
                    addLatestHashed(here, TARGET, prefix, files);
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

        @Override
        public int compareTo(DataSet dataSet) {
            return getSpec().compareTo(dataSet.getSpec());
        }
    }

    private SchemaFactory schemaFactory(String prefix) {
        if (schemaFactory == null) {
            schemaFactory = XMLToolFactory.schemaFactory(prefix);
            if (resolver != null) schemaFactory.setResourceResolver(resolver);
        }
        return schemaFactory;
    }

    private RecDef recDef(SchemaVersion schemaVersion) throws StorageException {
        String fileName = schemaVersion.getFullFileName(RECORD_DEFINITION);
        try {
            File file = cache(fileName);
            if (!file.exists()) {
                SchemaResponse recDefResponse = schemaRepository.getSchema(schemaVersion, RECORD_DEFINITION);
                if (recDefResponse == null) {
                    throw new StorageException("No rec-def found for " + schemaVersion);
                }
                if (recDefResponse.isValidated()) {
                    FileUtils.write(file, recDefResponse.getSchemaText(), "UTF-8");
                }
                return RecDef.read(new ByteArrayInputStream(recDefResponse.getSchemaText().getBytes("UTF-8")));
            }
            else {
                return RecDef.read(new FileInputStream(file));
            }
        }
        catch (IOException e) {
            throw new StorageException("Unable to load " + fileName, e);
        }
    }

    private Validator validator(SchemaVersion schemaVersion) throws StorageException {
        String fileName = schemaVersion.getFullFileName(VALIDATION_SCHEMA);
        try {
            File file = cache(fileName);
            SchemaFactory schemaFactory = schemaFactory(schemaVersion.getPrefix());
            if (!file.exists()) {
                SchemaResponse valResponse = schemaRepository.getSchema(schemaVersion, VALIDATION_SCHEMA);
                if (valResponse == null) {
                    throw new StorageException("No validation XSD foudn for " + schemaVersion);
                }
                if (valResponse.isValidated()) {
                    FileUtils.write(file, valResponse.getSchemaText(), "UTF-8");
                }
                StreamSource source = new StreamSource(new StringReader(valResponse.getSchemaText()));
                return schemaFactory.newSchema(source).newValidator();
            }
            else {
                return schemaFactory.newSchema(file).newValidator();
            }
        }
        catch (SAXException e) {
            throw new StorageException("Unable to create a validator: " + schemaVersion, e);
        }
        catch (IOException e) {
            throw new StorageException("Unable to load " + fileName, e);
        }
    }

}
