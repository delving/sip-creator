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
import eu.delving.metadata.RecMapping;
import eu.delving.schema.SchemaRepository;
import eu.delving.schema.SchemaResponse;
import eu.delving.schema.SchemaVersion;
import eu.delving.sip.base.CancelException;
import eu.delving.sip.base.ProgressListener;
import eu.delving.stats.Stats;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.SAXException;

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static eu.delving.schema.SchemaType.RECORD_DEFINITION;
import static eu.delving.schema.SchemaType.VALIDATION_SCHEMA;
import static eu.delving.sip.files.Storage.FileType.MAPPING;
import static eu.delving.sip.files.StorageHelper.*;

/**
 * This is an implementation of the Storage interface, with most of the functionality built into the inner class
 * which implements the DataSet interface.
 *
 *
 */

public class StorageImpl implements Storage {
    private File home;
    private static SchemaFactory schemaFactory;
    private SchemaRepository schemaRepository;
    private static LSResourceResolver resolver;

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
                if (!directory.isDirectory() || directory.getName().equals(CACHE_DIR)) continue;
                boolean hasFiles = false; // empty ones will not appear
                File[] files = directory.listFiles();
                if (files != null) {
                    for (File file : files) if (file.isFile()) hasFiles = true;
                    if (!hasFiles) continue;
                    DataSetImpl impl = new DataSetImpl(directory,  schemaRepository);
                    map.put(directory.getName(), impl);
                }
            }
        }
        return map;
    }

    @Override
    public DataSet createDataSet(String sipFileName) throws StorageException {
        File directory = createDataSetDirectory(home, sipFileName);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new StorageException(String.format("Unable to create data set directory %s", directory.getAbsolutePath()));
        }
        return new DataSetImpl(directory, schemaRepository);
    }

    public static class DataSetImpl implements DataSet, Serializable {

        private File here;
        private Map<String, String> dataSetFacts;
        private final SchemaRepository schemaRepository;

        public DataSetImpl(File here, SchemaRepository schemaRepository) {
            this.schemaRepository = schemaRepository;
            this.here = here;
        }

        @Override
        public File getSipFile() {
            return here;
        }

        @Override
        public String getSpec() {
            return StorageHelper.datasetNameFromSipZip(here);
        }

        @Override
        public SchemaVersion getSchemaVersion() {
            String fact = getDataSetFacts().get(SCHEMA_VERSIONS);
            if (fact == null) {
                return new SchemaVersion("unknown", "0.0.0");
            }
            return new SchemaVersion(fact);
        }

        @Override
        public RecDef getRecDef() throws StorageException {
            return recDef(getSchemaVersion());
        }

        @Override
        public Validator newValidator() throws StorageException {
            return validator(getSchemaVersion());
        }

        @Override
        public DataSetState getState() {
            File source = sourceFile(here);
            if (source.exists()) {
                return postSourceState(source);
            }
            else {
                return DataSetState.ABSENT;
            }
        }

        private DataSetState postSourceState(File source) {
            File statistics = statsFile(here);
            if (statistics.exists() && statistics.lastModified() >= source.lastModified()) {
                File mapping = findLatestFile(here, MAPPING, getSchemaVersion().getPrefix());
                if (mapping.exists()) {
                    return targetOutput().exists() ? DataSetState.PROCESSED : DataSetState.MAPPING;
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
        public void deleteResults() {
            String prefix = getSchemaVersion().getPrefix();
            delete(targetFile(here, dataSetFacts, prefix));
            delete(reportFile(here, prefix));
            delete(reportIndexFile(here, prefix));
            delete(reportConclusionFile(here, prefix));
        }

        @Override
        public InputStream openSourceInputStream() throws StorageException {
            return zipIn(sourceFile(here));
        }

        @Override
        public File targetOutput() {
            return targetFile(here, dataSetFacts, getSchemaVersion().getPrefix());
        }

        @Override
        public Stats getStats() {
            File statsFile = statsFile(here);
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
        public void setStats(Stats stats) throws StorageException {
            File statsFile = statsFile(here);
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
        public RecMapping getRecMapping(RecDefModel recDefModel) throws StorageException {
            File file = findLatestFile(here, MAPPING, getSchemaVersion().getPrefix());
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
                    return RecMapping.create(recDefModel.createRecDefTree(getSchemaVersion()));
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
        public List<File> getRecMappingFiles() throws StorageException {
            return findHashedPrefixFiles(here, MAPPING, getSchemaVersion().getPrefix());
        }

        @Override
        public ReportWriter openReportWriter(RecDef recDef) throws StorageException {
            File reportFile = new File(here, FileType.REPORT.getName(recDef.prefix));
            File reportIndexFile = new File(here, FileType.REPORT_INDEX.getName(recDef.prefix));
            File reportConclusionFile = new File(here, FileType.REPORT_CONCLUSION.getName(recDef.prefix));
            try {
                return new ReportWriter(reportFile, reportIndexFile, reportConclusionFile);
            }
            catch (IOException e) {
                throw new StorageException("Cannot read validation report", e);
            }
            catch (XPathExpressionException e) {
                throw new StorageException("Cannot create xpath expression", e);
            }
        }

        @Override
        public ReportFile getReport() throws StorageException {
            try {
                String prefix = getSchemaVersion().getPrefix();
                File reportFile = reportFile(here, prefix);
                File reportIndexFile = reportIndexFile(here, prefix);
                File reportConclusionFile = reportConclusionFile(here, prefix);
                if (!(reportFile.exists() && reportIndexFile.exists() && reportConclusionFile.exists())) return null;
                return new ReportFile(reportFile, reportIndexFile, reportConclusionFile, this, prefix);
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
        public void fromSipZip(File sipZipFile, ProgressListener progressListener) throws StorageException {
            delete(here);
            here.mkdir();
            long streamLength = sipZipFile.length();

            try (InputStream inputStream = new FileInputStream(sipZipFile) ){
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
        public File toSipZip(boolean sourceIncluded) throws StorageException {
            try {
                // check if the dataset is based on a harvest
                Map<String, String> hints = getHints();
                hints.put("pockets", "true");
                hints.remove("recordRootPath");
                hints.remove("uniqueIdPath");
                setHints(hints);
                // gather the files together
                List<File> files = new ArrayList<File>();
                // narthexFacts take only the chosen SCHEMA_VERSIONS
                Map<String, String> narthexFacts = new TreeMap<String, String>(dataSetFacts);
                // for the mapping matching the prefix
                SchemaVersion schemaVersion = getSchemaVersion();
                narthexFacts.put(SCHEMA_VERSIONS, schemaVersion.toString());
                addLatestNoHash(here, MAPPING, schemaVersion.getPrefix(), files);
                File recDef = new File(here, schemaVersion.getFullFileName(RECORD_DEFINITION));
                if (recDef.exists()) files.add(recDef);
                File valSchema = new File(here, schemaVersion.getFullFileName(VALIDATION_SCHEMA));
                if (valSchema.exists()) files.add(valSchema);
                files.add(hintsFile(here));
                writeFacts(narthexFactsFile(here), narthexFacts);
                files.add(narthexFactsFile(here));
                if (sourceIncluded) files.add(sourceFile(here));
                File sipZip = sipZip(HomeDirectory.UP_DIR, getSpec(), getSchemaVersion().getPrefix());
                FileOutputStream fos = new FileOutputStream(sipZip);
                ZipOutputStream zos = new ZipOutputStream(fos);
                byte[] buffer = new byte[1024];
                for (File file : files) {
                    FileInputStream fis = new FileInputStream(file);
                    zos.putNextEntry(new ZipEntry(file.getName()));
                    int length;
                    while ((length = fis.read(buffer)) > 0) zos.write(buffer, 0, length);
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
            delete(here);
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
                    FileUtils.write(file, recDefResponse.getSchemaText(), "UTF-8");
                }
                return RecDef.read(new FileInputStream(file));
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
                    FileUtils.write(file, valResponse.getSchemaText(), "UTF-8");
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

    private static SchemaFactory schemaFactory(String prefix) {
        if (schemaFactory == null) {
            schemaFactory = XMLToolFactory.schemaFactory(prefix);
            if (resolver != null) schemaFactory.setResourceResolver(resolver);
        }
        return schemaFactory;
    }
}
