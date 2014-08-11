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
import eu.delving.metadata.Hasher;
import eu.delving.metadata.MetadataException;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefModel;
import eu.delving.metadata.RecDefNamespaceContext;
import eu.delving.metadata.RecMapping;
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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static eu.delving.schema.SchemaType.RECORD_DEFINITION;
import static eu.delving.schema.SchemaType.VALIDATION_SCHEMA;
import static eu.delving.sip.files.Storage.FileType.IMPORTED;
import static eu.delving.sip.files.Storage.FileType.MAPPING;
import static eu.delving.sip.files.Storage.FileType.SOURCE;
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
    public Map<String, DataSet> getDataSets(boolean narthex) {
        Map<String, DataSet> map = new TreeMap<String, DataSet>();
        File[] list = home.listFiles();
        if (list != null) {
            for (File directory : list) {
                if (!directory.isDirectory()) continue;
                if (narthex) {
                    Matcher matcher = NARTHEX_DATASET_PATTERN.matcher(directory.getName());
                    if (!matcher.matches()) continue;
                    DataSetImpl impl = new DataSetImpl(directory);
                    map.put(impl.getNarthexSipZipName(), impl);
                }
                else {
                    Matcher matcher = HUB_DATASET_PATTERN.matcher(directory.getName());
                    if (!matcher.matches()) continue;
                    boolean hasFiles = false; // empty ones will not appear
                    File[] files = directory.listFiles();
                    if (files != null) {
                        for (File file : files) if (file.isFile()) hasFiles = true;
                        if (!hasFiles) continue;
                        DataSetImpl impl = new DataSetImpl(directory);
                        map.put(impl.getSpec(), impl);
                    }
                }
            }
        }
        return map;
    }

    @Override
    public DataSet createDataSet(boolean narthex, String spec, String organization) throws StorageException {
        File directory = createDataSetDirectory(home, narthex, spec, organization);
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
            return targetOutput(prefix).exists();
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
            File statistics = statsFile(here, false);
            if (statistics.exists() && statistics.lastModified() >= imported.lastModified()) {
                return allHintsSet(getHints()) ? DataSetState.DELIMITED : DataSetState.ANALYZED_IMPORT;
            }
            else {
                return DataSetState.IMPORTED;
            }
        }

        private DataSetState postSourceState(File source, String prefix) {
            File statistics = statsFile(here, true);
            if (statistics.exists() && statistics.lastModified() >= source.lastModified()) {
                File mapping = findLatestFile(here, MAPPING, prefix);
                if (mapping.exists()) {
                    return targetOutput(prefix).exists() ? DataSetState.PROCESSED : DataSetState.MAPPING;
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
            File targetFile = targetFile(here, dataSetFacts, prefix);
            boolean deleted = targetFile.exists();
            delete(targetFile);
            return deleted;
        }

        @Override
        public File importedOutput() {
            return new File(here, IMPORTED.getName());
        }

        @Override
        public InputStream openImportedInputStream() throws StorageException {
            return zipIn(new File(here, IMPORTED.getName()));
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
            return targetFile(here, dataSetFacts, prefix);
        }

        @Override
        public Stats getLatestStats() {
            File analysis = statsFile(here, false);
            File source = statsFile(here, true);
            if (analysis.exists()) {
                if (source.exists()) {
                    return getStats(source.lastModified() >= analysis.lastModified());
                }
                else {
                    return getStats(false);
                }
            }
            else {
                return getStats(true);
            }
        }

        @Override
        public Stats getStats(boolean sourceFormat) {
            File statsFile = statsFile(here, sourceFormat);
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
        public void setStats(Stats stats, boolean sourceFormat) throws StorageException {
            File statsFile = statsFile(here, sourceFormat);
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
                LinkCheckExtractor linkCheckExtractor = new LinkCheckExtractor(recDef.fieldMarkers, new RecDefNamespaceContext(recDef.namespaces));
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
                if (!(reportFile.exists() && reportIndexFile.exists())) return null;
                return new ReportFile(reportFile, reportIndexFile, linkFile(here, prefix), this, prefix);
            }
            catch (IOException e) {
                throw new StorageException("Cannot read validation report", e);
            }
        }

        @Override
        public void deleteSource() {
            for (File file : findSourceFiles(here)) delete(file);
        }

        @Override
        public List<File> getUploadFiles() throws StorageException {
            List<File> files = new ArrayList<File>();
            for (SchemaVersion schemaVersion : getSchemaVersions()) {
                String prefix = schemaVersion.getPrefix();
                File targetFile = targetOutput(prefix);
                if (targetFile.exists()) files.add(targetFile);
            }
            return files;
        }

        @Override
        public File sipZipFile(String fileName) throws StorageException {
            return new File(here, fileName);
        }

        @Override
        public void fromSipZip(File sipZipFile, ProgressListener progressListener) throws StorageException {
            long streamLength = sipZipFile.length();
            try {
                InputStream inputStream = new FileInputStream(sipZipFile);
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
                            File source = new File(here, FileType.SOURCE.getName());
                            GZIPOutputStream outputStream = null;
                            try {
                                outputStream = new GZIPOutputStream(new FileOutputStream(source));
                                while (-1 != (bytesRead = zipInputStream.read(buffer))) {
                                    outputStream.write(buffer, 0, bytesRead);
                                    progressListener.setProgress((int) (counting.getByteCount() / BLOCK_SIZE));
                                }
                            }
                            finally {
                                IOUtils.closeQuietly(outputStream);
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
            }
            catch (IOException e) {
                throw new StorageException("Unable to accept SipZip file", e);
            }
        }

        @Override
        public File toSipZip() throws StorageException {
            try {
                List<File> files = new ArrayList<File>();
                files.add(hintsFile(here));
                files.add(factsFile(here));
                for (SchemaVersion schemaVersion : getSchemaVersions()) {
                    addLatestNoHash(here, MAPPING, schemaVersion.getPrefix(), files);
                    File recDef = new File(here, schemaVersion.getFullFileName(RECORD_DEFINITION));
                    if (recDef.exists()) files.add(recDef);
                    File valSchema = new File(here, schemaVersion.getFullFileName(VALIDATION_SCHEMA));
                    if (valSchema.exists()) files.add(valSchema);
                }
                files.add(sourceFile(here));
                for (File file : sipZips(here)) delete(file);
                File sipZip = sipZip(here, getSpec(), StorageFinder.getUser(home));
                FileOutputStream fos = new FileOutputStream(sipZip);
                ZipOutputStream zos = new ZipOutputStream(fos);
                byte[] buffer = new byte[1024];
                for (File file : files) {
                    FileInputStream fis = new FileInputStream(file);
                    zos.putNextEntry(new ZipEntry(file.getName()));
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, length);
                    }
                    zos.closeEntry();
                    fis.close();
                }
                zos.close();
                return sipZip;
            }
            catch (IOException e) {
                throw new StorageException("Unable to collect files for sip zip", e);
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

        private RecDef recDef(SchemaVersion schemaVersion) throws StorageException {
            String fileName = schemaVersion.getFullFileName(RECORD_DEFINITION);
            try {
                File file = new File(here, fileName);
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


        private synchronized Validator validator(SchemaVersion schemaVersion) throws StorageException {
            String fileName = schemaVersion.getFullFileName(VALIDATION_SCHEMA);
            try {
                File file = new File(here, fileName);
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

        public String getNarthexSipZipName() {
            List<File> zips = sipZips(here);
            if (zips.isEmpty()) {
                return getSpec();
            }
            else {
                return zips.get(0).getName();
            }
        }
    }

    private SchemaFactory schemaFactory(String prefix) {
        if (schemaFactory == null) {
            schemaFactory = XMLToolFactory.schemaFactory(prefix);
            if (resolver != null) schemaFactory.setResourceResolver(resolver);
        }
        return schemaFactory;
    }

}
