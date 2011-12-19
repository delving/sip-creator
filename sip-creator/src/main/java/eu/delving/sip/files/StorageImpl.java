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

import eu.delving.metadata.*;
import eu.delving.sip.ProgressListener;
import eu.delving.sip.xml.SourceConverter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;

import java.io.*;
import java.security.DigestOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static eu.delving.sip.files.DataSetState.*;

/**
 * This is an implementation of the Storage interface
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class StorageImpl extends StorageBase implements Storage {
    private File home;

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
        for (File directory : list) {
            if (!directory.isDirectory()) continue;
            boolean hasFiles = false;
            for (File sub : directory.listFiles()) if (sub.isFile()) hasFiles = true;
            if (!hasFiles) continue;
            map.put(directory.getName(), new DataSetImpl(directory));
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
        public String getLatestPrefix() {
            File latestMapping = latestMappingFileOrNull(here);
            if (latestMapping != null) return extractName(latestMapping, FileType.MAPPING);
            Iterator<File> defWalk = findRecordDefinitionFiles(here).iterator();
            if (!defWalk.hasNext()) return null;
            String name = defWalk.next().getName();
            return name.substring(0, name.length() - Storage.FileType.RECORD_DEFINITION.getSuffix().length());
        }

        @Override
        public List<FactDefinition> getFactDefinitions() throws StorageException {
            try {
                return FactDefinition.read(factDefinitionFile(here));
            }
            catch (FileNotFoundException e) {
                throw new StorageException("Unable to load fact definitions", e);
            }
        }

        @Override
        public List<RecordDefinition> getRecordDefinitions(List<FactDefinition> factDefinitions) throws StorageException {
            List<RecordDefinition> definitions = new ArrayList<RecordDefinition>();
            try {
                for (File file : findRecordDefinitionFiles(here)) {
                    definitions.add(RecordDefinition.read(file, factDefinitions));
                }
            }
            catch (Exception e) {
                throw new StorageException("Unable to load metadata model");
            }
            return definitions;
        }

        @Override
        public DataSetState getState() {
            File imported = importedFile(here);
            File source = sourceFile(here);
            if (imported.exists()) {
                if (source.exists() && source.lastModified() >= imported.lastModified()) {
                    return postSourceState(source);
                }
                else {
                    return importedState(imported);
                }
            }
            else if (source.exists()) {
                return postSourceState(source);
            }
            else {
                return EMPTY;
            }
        }

        private DataSetState importedState(File imported) {
            File statistics = statisticsFile(here, false);
            if (statistics.exists() && statistics.lastModified() >= imported.lastModified()) {
                return allHintsSet(getHints()) ? DELIMITED : ANALYZED_IMPORT;
            }
            else {
                return IMPORTED;
            }
        }

        private DataSetState postSourceState(File source) {
            File statistics = statisticsFile(here, true);
            if (statistics.exists() && statistics.lastModified() >= source.lastModified()) {
                File mapping = latestMappingFileOrNull(here);
                if (mapping != null) {
                    return validationFile(here, mapping).exists() ? VALIDATED : MAPPING;
                }
                else {
                    return ANALYZED_SOURCE;
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
            if (!isRecentlyImported()) delete(sourceFile(here));
        }

        @Override
        public void deleteValidation(String metadataPrefix) throws StorageException {
            for (File file : validationFiles(here, metadataPrefix)) delete(file);
        }

        @Override
        public void deleteValidations() throws StorageException {
            for (File file : validationFiles(here)) delete(file);
        }

        @Override
        public File importedOutput() throws StorageException {
            return importedFile(here);
        }

        @Override
        public InputStream openImportedInputStream() throws StorageException {
            return zipIn(importedFile(here));
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
                ObjectInputStream objectInputStream = null;
                try {
                    objectInputStream = new ObjectInputStream(new BufferedInputStream(new FileInputStream(statisticsFile)));
                    return (Statistics) objectInputStream.readObject();
                }
                catch (Exception e) {
                    FileUtils.deleteQuietly(statisticsFile);
                }
                finally {
                    IOUtils.closeQuietly(objectInputStream);
                }
            }
            return null;
        }

        @Override
        public void setStatistics(Statistics statistics) throws StorageException {
            File statisticsFile = statisticsFile(here, statistics.isSourceFormat());
            ObjectOutputStream out = null;
            try {
                out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(statisticsFile)));
                out.writeObject(statistics);
            }
            catch (IOException e) {
                throw new StorageException(String.format("Unable to save statistics file to %s", statisticsFile.getAbsolutePath()), e);
            }
            finally {
                IOUtils.closeQuietly(out);
            }
        }

        @Override
        public RecordMapping getRecordMapping(String prefix, MetadataModel metadataModel) throws StorageException {
            File file = findLatestMappingFile(here, prefix);
            if (file.exists()) {
                try {
                    return RecordMapping.read(file, metadataModel);
                }
                catch (Exception e) {
                    throw new StorageException(String.format("Unable to read mapping from %s", file.getAbsolutePath()), e);
                }
            }
            else {
                return new RecordMapping(prefix, metadataModel.getRecordDefinition(prefix));
            }
        }

        @Override
        public void setRecordMapping(RecordMapping recordMapping) throws StorageException {
            File file = new File(here, String.format(FileType.MAPPING.getPattern(), recordMapping.getPrefix()));
            try {
                RecordMapping.write(file, recordMapping);
            }
            catch (FileNotFoundException e) {
                throw new StorageException(String.format("Unable to save mapping to %s", file.getAbsolutePath()), e);
            }
        }

        @Override
        public void setValidation(String metadataPrefix, BitSet validation, int recordCount) throws StorageException {
            if (validation == null) {
                deleteValidation(metadataPrefix);
            }
            else {
                File file = new File(here, String.format(FileType.VALIDATION.getPattern(), metadataPrefix));
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
        public PrintWriter openReportWriter(RecordMapping recordMapping) throws StorageException {
            File file = new File(here, String.format(FileType.REPORT.getPattern(), recordMapping.getPrefix()));
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
            File source = new File(here, FileType.IMPORTED.getName());
            Hasher hasher = new Hasher();
            boolean cancelled = false;
            InputStream inputStream = null;
            OutputStream outputStream = null;
            try {
                CountingInputStream countingInput;
                if (inputFile.getName().endsWith(".xml")) {
                    inputStream = new FileInputStream(inputFile);
                    countingInput = new CountingInputStream(inputStream);
                    inputStream = countingInput;
                }
                else if (inputFile.getName().endsWith(".xml.gz")) {
                    inputStream = new FileInputStream(inputFile);
                    countingInput = new CountingInputStream(inputStream);
                    inputStream = new GZIPInputStream(countingInput);
                }
                else {
                    throw new IllegalArgumentException("Input file should be .xml or .xml.gz, but it is " + inputFile.getName());
                }
                outputStream = new GZIPOutputStream(new FileOutputStream(source));
                byte[] buffer = new byte[BLOCK_SIZE];
                int bytesRead;
                while (-1 != (bytesRead = inputStream.read(buffer))) {
                    outputStream.write(buffer, 0, bytesRead);
                    if (progressListener != null) {
                        if (!progressListener.setProgress((int) (countingInput.getByteCount() / BLOCK_SIZE))) {
                            cancelled = true;
                            break;
                        }
                    }
                    hasher.update(buffer, bytesRead);
                }
                if (progressListener != null) progressListener.finished(!cancelled);
                delete(statisticsFile(here, false));
            }
            catch (Exception e) {
                if (progressListener != null) progressListener.finished(false);
                throw new StorageException("Unable to capture XML input into " + source.getAbsolutePath(), e);
            }
            finally {
                IOUtils.closeQuietly(inputStream);
                IOUtils.closeQuietly(outputStream);
            }
            if (cancelled) {
                delete(source);
            }
            else {
                File hashedSource = new File(here, hasher.prefixFileName(FileType.IMPORTED.getName()));
                if (hashedSource.exists()) {
                    delete(source);
                    throw new StorageException("This import was identical to the previous one. Discarded.");
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
                Statistics statistics = getStatistics(false);
                SourceConverter converter = new SourceConverter(recordRoot, recordCount, uniqueElement, statistics.getNamespaces());
                converter.setProgressListener(progressListener);
                Hasher hasher = new Hasher();
                DigestOutputStream digestOut = hasher.createDigestOutputStream(zipOut(new File(here, FileType.SOURCE.getName())));
                converter.parse(openImportedInputStream(), digestOut); // streams closed within parse()
                File source = new File(here, FileType.SOURCE.getName());
                File hashedSource = new File(here, hasher.prefixFileName(FileType.SOURCE.getName()));
                if (hashedSource.exists()) FileUtils.deleteQuietly(hashedSource);
                FileUtils.moveFile(source, hashedSource);
                FileUtils.deleteQuietly(statisticsFile(here, true));
            }
            catch (StorageException e) {
                throw e;
            }
            catch (Exception e) {
                File source = new File(here, FileType.SOURCE.getName());
                delete(source);
                throw new StorageException("Unable to convert source: " + e.getMessage(), e);
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
            String now = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
            File saveDirectory = new File(here, now);
            try {
                for (File file : here.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File file) {
                        return file.isFile();
                    }
                })) {
                    FileUtils.moveFileToDirectory(file, saveDirectory, true);
                }
            }
            catch (IOException e) {
                throw new StorageException(String.format("Unable to save files in %s to directory %s", here.getName(), now), e);
            }
        }

        private InputStream zipIn(File file) throws StorageException {
            try {
                return new GZIPInputStream(new FileInputStream(file));
            }
            catch (IOException e) {
                throw new StorageException(String.format("Unable to create input stream from %s", file.getAbsolutePath()), e);
            }
        }

        private OutputStream zipOut(File file) throws StorageException {
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
