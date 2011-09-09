/*
 * Copyright 2010 DELVING BV
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

import com.thoughtworks.xstream.XStream;
import eu.delving.metadata.FactDefinition;
import eu.delving.metadata.Hasher;
import eu.delving.metadata.MetadataModel;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecordDefinition;
import eu.delving.metadata.RecordMapping;
import eu.delving.sip.ProgressListener;
import eu.delving.sip.xml.SourceConverter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Serializable;
import java.security.DigestOutputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static eu.delving.sip.files.DataSetState.ANALYZED;
import static eu.delving.sip.files.DataSetState.EMPTY;
import static eu.delving.sip.files.DataSetState.IMPORTED;
import static eu.delving.sip.files.DataSetState.IMPORTED_ANALYZED;
import static eu.delving.sip.files.DataSetState.IMPORTED_HINTS_SET;
import static eu.delving.sip.files.DataSetState.MAPPED;
import static eu.delving.sip.files.DataSetState.SOURCED;
import static eu.delving.sip.files.DataSetState.VALIDATED;

/**
 * This is an implementation of the Storage interface
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class StorageImpl extends StorageBase implements Storage {

    private File home;
    private final static Logger LOG = Logger.getAnonymousLogger();

    public StorageImpl(File home) throws StorageException {
        this.home = home;
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
    public Map<String, DataSet> getDataSets() {
        Map<String, DataSet> map = new TreeMap<String, DataSet>();
        File[] list = home.listFiles();
        if (list != null) {
            for (File file : list) {
                if (file.isDirectory()) {
                    map.put(file.getName(), new DataSetImpl(file));
                }
            }
        }
        return map;
    }

    @Override
    public DataSet createDataSet(String spec) throws StorageException {
        File directory = new File(home, spec);
        if (directory.exists()) {
            throw new StorageException(String.format("Data set directory %s already exists", directory.getAbsolutePath()));
        }
        if (!directory.mkdirs()) {
            throw new StorageException(String.format("Unable to create data set directory %s", directory.getAbsolutePath()));
        }
        return new DataSetImpl(directory);
    }

    public class DataSetImpl implements DataSet, Serializable {

        private File here;

        public DataSetImpl(File here) {
            this.here = here;
        }

        @Override
        public String getSpec() {
            return here.getName();
        }

        @Override
        public String getLatestPrefix() {
            File latestMapping = latestMappingFileOrNull(here);
            return latestMapping != null ? mappingPrefix(latestMapping) : null;
        }

        @Override
        public RecordMapping setLatestPrefix(String prefix, MetadataModel metadataModel) throws StorageException {
            File latestForPrefix = findLatestMappingFile(here, prefix);
            RecordMapping recordMapping;
            if (latestForPrefix.exists()) {
                if (!latestForPrefix.setLastModified(System.currentTimeMillis())) {
                    throw new StorageException("Couldn't touch the file to give it priority");
                }
                recordMapping = getRecordMapping(prefix, metadataModel);
            }
            else {
                recordMapping = new RecordMapping(prefix);
                setRecordMapping(recordMapping);
            }
            return recordMapping;
        }

        @Override
        public List<FactDefinition> getFactDefinitions() throws StorageException {
            try {
                File factDefFile = factDefinitionFile(here);
                XStream stream = recordStream();
                Reader reader = new InputStreamReader(new FileInputStream(factDefFile), "UTF-8");
                FactDefinition.List factDefinitions = (FactDefinition.List) stream.fromXML(reader);
                return factDefinitions.factDefinitions;
            }
            catch (Exception e) {
                throw new StorageException("Unable to load fact definitions", e);
            }
        }

        @Override
        public List<RecordDefinition> getRecordDefinitions(List<FactDefinition> factDefinitions) throws StorageException {
            List<RecordDefinition> definitions = new ArrayList<RecordDefinition>();
            try {
                List<File> recDefFile = findRecordDefinitionFiles(here);
                for (File file : recDefFile) {
                    RecordDefinition recordDefinition = readRecordDefinition(new FileInputStream(file), factDefinitions);
                    recordDefinition.initialize(factDefinitions);
                    definitions.add(recordDefinition);
                }
            }
            catch (Exception e) {
                throw new StorageException("Unable to load metadata model");
            }
            return definitions;
        }

        @Override
        public DataSetState getState() {
//            if (phantomFile(here).exists()) {
//                return PHANTOM;
//            }
            File imported = importedFile(here);
            File source = sourceFile(here);
            if (imported.exists()) {
                if (source.exists()) {
                    if (imported.lastModified() > source.lastModified()) {
                        return stateImportReadiness();
                    }
                    else {
                        return statePostSource();
                    }
                }
                else {
                    return stateImportReadiness();
                }
            }
            else if (source.exists()) {
                return statePostSource();
            }
            else {
                return EMPTY;
            }
        }

        private DataSetState stateImportReadiness() {
            if (statisticsFile(here, false).exists()) {
                return allHintsSet(getHints()) ? IMPORTED_HINTS_SET : IMPORTED_ANALYZED;
            }
            else {
                return IMPORTED;
            }
        }

        private DataSetState statePostSource() {
            if (statisticsFile(here, true).exists()) {
                File mapping = latestMappingFileOrNull(here);
                if (mapping != null) {
                    return validationFile(here, mapping).exists() ? VALIDATED : MAPPED;
                }
                else {
                    return ANALYZED;
                }
            }
            else {
                return SOURCED;
            }
        }

        @Override
        public Map<String, String> getDataSetFacts() {
            try {
                return readFacts(factsFile(here));
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
            try {
                writeFacts(hintsFile(here), hints);
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
            if (!isRecentlyImported()) {
                File sourceFile = sourceFile(here);
                delete(sourceFile);
            }
        }

        @Override
        public InputStream importedInput() throws StorageException {
            return zipIn(importedFile(here));
        }

        @Override
        public InputStream sourceInput() throws StorageException {
            return zipIn(sourceFile(here));
        }

        @Override
        public Statistics getLatestStatistics() {
            File analysis = statisticsFile(here, false);
            File source = statisticsFile(here, true);
            if (analysis.exists()) {
                if (source.exists()) {
                    return getStatistics(source.lastModified() >= analysis.lastModified());
                }
                else {
                    return getStatistics(false);
                }
            }
            else {
                return getStatistics(true);
            }
        }

        @Override
        public Statistics getStatistics(boolean sourceFormat) {
            File statisticsFile = statisticsFile(here, sourceFormat);
            if (statisticsFile.exists()) {
                try {
                    ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(statisticsFile)));
                    @SuppressWarnings("unchecked")
                    Statistics statistics = (Statistics) in.readObject();
                    in.close();
                    return statistics;
                }
                catch (Exception e) {
                    try {
                        delete(statisticsFile);
                    }
                    catch (StorageException e1) {
                        // give up
                    }
                }
            }
            return null;
        }

        @Override
        public void setStatistics(Statistics statistics) throws StorageException {
            File statisticsFile = statisticsFile(here, statistics.isSourceFormat());
            try {
                ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(statisticsFile)));
                out.writeObject(statistics);
                out.close();
            }
            catch (IOException e) {
                throw new StorageException(String.format("Unable to save statistics file to %s", statisticsFile.getAbsolutePath()), e);
            }
        }

        @Override
        public RecordMapping getRecordMapping(String prefix, MetadataModel metadataModel) throws StorageException {
            File file = findLatestMappingFile(here, prefix);
            if (file.exists()) {
                try {
                    FileInputStream is = new FileInputStream(file);
                    return RecordMapping.read(is, metadataModel);
                }
                catch (Exception e) {
                    throw new StorageException(String.format("Unable to read mapping from %s", file.getAbsolutePath()), e);
                }
            }
            else {
                return new RecordMapping(prefix);
            }
        }

        @Override
        public void setRecordMapping(RecordMapping recordMapping) throws StorageException {
            File file = new File(here, String.format(MAPPING_FILE_PATTERN, recordMapping.getPrefix()));
            try {
                FileOutputStream out = new FileOutputStream(file);
                RecordMapping.write(recordMapping, out);
                out.close();
            }
            catch (IOException e) {
                throw new StorageException(String.format("Unable to save mapping to %s", file.getAbsolutePath()), e);
            }
        }

        @Override
        public void setValidation(String metadataPrefix, BitSet validation, int recordCount) throws StorageException {
            File file = new File(here, String.format(VALIDATION_FILE_PATTERN, metadataPrefix));
            try {
                DataOutputStream out = new DataOutputStream(new FileOutputStream(file));
                int invalidCount = recordCount - validation.cardinality();
                out.writeInt(invalidCount);
                for (int index = validation.nextClearBit(0); index >= 0 && index < recordCount; index = validation.nextClearBit(index + 1)) {
                    out.writeInt(index);
                }
                out.close();
            }
            catch (IOException e) {
                throw new StorageException(String.format("Unable to save mapping to %s", file.getAbsolutePath()), e);
            }
        }

        @Override
        public PrintWriter reportWriter(RecordMapping recordMapping) throws StorageException {
            File file = new File(here, String.format(REPORT_FILE_PATTERN, recordMapping.getPrefix()));
            try {
                return new PrintWriter(file);
            }
            catch (IOException e) {
                throw new StorageException("Cannot read validation report", e);
            }
        }

        @Override
        public List<String> getReport(RecordMapping recordMapping) throws StorageException {
            try {
                File file = reportFile(here, recordMapping);
                return file.exists() ? FileUtils.readLines(file, "UTF-8") : null;
            }
            catch (IOException e) {
                throw new StorageException("Cannot read validation report", e);
            }
        }

        @Override
        public void externalToImported(File inputFile, ProgressListener progressListener) throws StorageException {
            int fileBlocks = (int) (inputFile.length() / BLOCK_SIZE);
            if (progressListener != null) progressListener.prepareFor(fileBlocks);
            File source = new File(here, IMPORTED_FILE_NAME);
            Hasher hasher = new Hasher();
            boolean cancelled = false;
            try {
                InputStream inputStream;
                if (inputFile.getName().endsWith(".xml")) {
                    inputStream = new FileInputStream(inputFile);
                }
                else if (inputFile.getName().endsWith(".xml.gz")) {
                    inputStream = new GZIPInputStream(new FileInputStream(inputFile));
                }
                else {
                    throw new IllegalArgumentException("Input file should be .xml or .xml.gz, but it is " + inputFile.getName());
                }
                OutputStream gzipOutputStream = new GZIPOutputStream(new FileOutputStream(source));
                byte[] buffer = new byte[BLOCK_SIZE];
                long totalBytesRead = 0;
                int bytesRead;
                while (-1 != (bytesRead = inputStream.read(buffer))) {
                    gzipOutputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                    if (progressListener != null) {
                        if (!progressListener.setProgress((int) (totalBytesRead / BLOCK_SIZE))) {
                            cancelled = true;
                            break;
                        }
                    }
                    hasher.update(buffer, bytesRead);
                }
                if (progressListener != null) progressListener.finished(!cancelled);
                inputStream.close();
                gzipOutputStream.close();
                delete(statisticsFile(here, false));
            }
            catch (Exception e) {
                if (progressListener != null) progressListener.finished(false);
                throw new StorageException("Unable to capture XML input into " + source.getAbsolutePath(), e);
            }
            if (cancelled) {
                delete(source);
            }
            else {
                File hashedSource = new File(here, hasher.prefixFileName(IMPORTED_FILE_NAME));
                if (hashedSource.exists()) {
                    delete(source);
                    throw new StorageException("This import was identical to the previous one. Discarded.");
                }
                if (!source.renameTo(hashedSource)) {
                    throw new StorageException(String.format("Unable to rename %s to %s", source.getAbsolutePath(), hashedSource.getAbsolutePath()));
                }
            }
        }

        @Override
        public void importedToSource(ProgressListener progressListener) throws StorageException {
            if (!isRecentlyImported()) {
                throw new StorageException("Import to source would be redundant, since source is newer");
            }
            if (!statisticsFile(here, false).exists()) {
                throw new StorageException("No analysis stats so conversion doesn't trust the record count");
            }
            try {
                Map<String, String> hints = getHints();
                Path recordRoot = getRecordRoot(hints);
                int recordCount = getRecordCount(hints);
                Path uniqueElement = getUniqueElement(hints);
                SourceConverter converter = new SourceConverter(recordRoot, recordCount, uniqueElement);
                converter.setProgressListener(progressListener);
                Hasher hasher = new Hasher();
                DigestOutputStream digestOut = hasher.createDigestOutputStream(sourceOutput());
                converter.parse(importedInput(), digestOut);
                File source = new File(here, SOURCE_FILE_NAME);
                File hashedSource = new File(here, hasher.prefixFileName(SOURCE_FILE_NAME));
                if (!source.renameTo(hashedSource)) {
                    throw new StorageException(String.format("Unable to rename %s to %s", source.getAbsolutePath(), hashedSource.getAbsolutePath()));
                }
            }
            catch (Exception e) {
                File source = new File(here, SOURCE_FILE_NAME);
                delete(source);
                throw new StorageException("Unable to convert source", e);
            }
        }

        @Override
        public List<File> getUploadFiles() throws StorageException {
            try {
                List<File> files = new ArrayList<File>();
                files.add(Hasher.ensureFileHashed(hintsFile(here)));
                for (File file : findLatestMappingFiles(here)) {
                    File validationFile = validationFile(here, file);
                    if (validationFile.exists()) {
                        files.add(Hasher.ensureFileHashed(file));
                        files.add(Hasher.ensureFileHashed(validationFile));
                    }
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
            boolean cancelled = false;
            if (progressListener != null) progressListener.prepareFor((int) (streamLength / BLOCK_SIZE));
            CountingInputStream counting = new CountingInputStream(inputStream);
            ZipInputStream zipInputStream = new ZipInputStream(counting);
            try {
                while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                    String fileName = zipEntry.getName();
                    if (fileName.equals(UNZIPPED_SOURCE_FILE_NAME)) {
                        Hasher hasher = new Hasher();
                        File source = new File(here, SOURCE_FILE_NAME);
                        GZIPOutputStream outputStream = new GZIPOutputStream(new FileOutputStream(source));
                        while (!cancelled && -1 != (bytesRead = zipInputStream.read(buffer))) {
                            outputStream.write(buffer, 0, bytesRead);
                            if (progressListener != null) {
                                if (!progressListener.setProgress((int) (counting.getByteCount() / BLOCK_SIZE))) {
                                    cancelled = true;
                                    break;
                                }
                            }
                            hasher.update(buffer, bytesRead);
                        }
                        if (progressListener != null) progressListener.finished(!cancelled);
                        outputStream.close();
                        File hashedSource = new File(here, hasher.prefixFileName(SOURCE_FILE_NAME));
                        if (!source.renameTo(hashedSource)) {
                            throw new StorageException(String.format("Unable to rename %s to %s", source.getAbsolutePath(), hashedSource.getAbsolutePath()));
                        }
                    }
                    else {
                        File file = new File(here, fileName);
                        IOUtils.copy(zipInputStream, new FileOutputStream(file));
                        if (progressListener != null && !progressListener.setProgress((int) (counting.getByteCount() / BLOCK_SIZE))) {
                            progressListener.finished(false);
                            break;
                        }
                    }
                }
            }
            catch (IOException e) {
                throw new StorageException("Unable to accept SipZip file", e);
            }
        }

        @Override
        public void remove() throws StorageException {
            delete(here);
        }

        public OutputStream sourceOutput() throws StorageException {
            return zipOut(SOURCE_FILE_NAME);
        }

        private InputStream zipIn(File file) throws StorageException {
            try {
                return new GZIPInputStream(new FileInputStream(file));
            }
            catch (IOException e) {
                throw new StorageException(String.format("Unable to create input stream from %s", file.getAbsolutePath()), e);
            }
        }

        private OutputStream zipOut(String fileName) throws StorageException {
            File file = new File(here, fileName);
            try {
                return new GZIPOutputStream(new FileOutputStream(file));
            }
            catch (IOException e) {
                throw new StorageException(String.format("Unable to create output stream from %s", file.getAbsolutePath()), e);
            }
        }

        @Override
        public String toString() {
            return getSpec();
        }
    }
}
