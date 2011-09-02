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

import eu.delving.metadata.Hasher;
import eu.delving.metadata.MetadataModel;
import eu.delving.metadata.MetadataModelImpl;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecordDefinition;
import eu.delving.metadata.RecordMapping;
import eu.delving.sip.ProgressListener;
import eu.delving.sip.xml.SourceConverter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import javax.xml.stream.XMLStreamException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static eu.delving.sip.files.FileStore.StoreState.EMPTY;
import static eu.delving.sip.files.FileStore.StoreState.IMPORTED_PENDING_ANALYZE;
import static eu.delving.sip.files.FileStore.StoreState.IMPORTED_PENDING_CONVERT;
import static eu.delving.sip.files.FileStore.StoreState.MAPPED;
import static eu.delving.sip.files.FileStore.StoreState.PHANTOM;
import static eu.delving.sip.files.FileStore.StoreState.SOURCED;
import static eu.delving.sip.files.FileStore.StoreState.VALIDATED;

/**
 * This interface describes how files are stored by the sip-creator
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class FileStoreImpl extends FileStoreBase implements FileStore {

    private File home;
    private MetadataModel metadataModel;

    public FileStoreImpl(File home, MetadataModel metadataModel) throws FileStoreException {
        this.home = home;
        this.metadataModel = metadataModel;
        if (!home.exists()) {
            if (!home.mkdirs()) {
                throw new FileStoreException(String.format("Unable to create file store in %s", home.getAbsolutePath()));
            }
        }
    }

    @Override
    public void setTemplate(String name, RecordMapping recordMapping) throws FileStoreException {
        File templateFile = new File(home, String.format(MAPPING_FILE_PATTERN, name));
        try {
            FileOutputStream fos = new FileOutputStream(templateFile);
            RecordMapping.write(recordMapping, fos);
            fos.close();
        }
        catch (IOException e) {
            throw new FileStoreException(String.format("Unable to save template to %s", templateFile.getAbsolutePath()), e);
        }
    }

    @Override
    public Map<String, RecordMapping> getTemplates() throws FileStoreException {
        Map<String, RecordMapping> templates = new TreeMap<String, RecordMapping>();
        for (File templateFile : home.listFiles(new MappingFileFilter())) {
            try {
                FileInputStream fis = new FileInputStream(templateFile);
                RecordMapping recordMapping = RecordMapping.read(fis, metadataModel);
                fis.close();
                String name = templateFile.getName();
                name = name.substring(MAPPING_FILE_PREFIX.length());
                name = name.substring(0, name.length() - MAPPING_FILE_SUFFIX.length());
                templates.put(name, recordMapping);
            }
            catch (Exception e) {
                delete(templateFile);
            }
        }
        return templates;
    }

    @Override
    public void deleteTemplate(String name) throws FileStoreException {
        File templateFile = new File(home, String.format(MAPPING_FILE_PATTERN, name));
        delete(templateFile);
    }

    @Override
    public Map<String, DataSetStore> getDataSetStores() {
        Map<String, DataSetStore> map = new TreeMap<String, DataSetStore>();
        File[] list = home.listFiles();
        if (list != null) {
            for (File file : list) {
                if (file.isDirectory()) {
                    map.put(file.getName(), new DataSetStoreImpl(file));
                }
            }
        }
        return map;
    }

    @Override
    public DataSetStore createDataSetStore(String spec) throws FileStoreException {
        File directory = new File(home, spec);
        if (directory.exists()) {
            throw new FileStoreException(String.format("Data store directory %s already exists", directory.getAbsolutePath()));
        }
        if (!directory.mkdirs()) {
            throw new FileStoreException(String.format("Unable to create data store directory %s", directory.getAbsolutePath()));
        }
        return new DataSetStoreImpl(directory);
    }

    public class DataSetStoreImpl implements DataSetStore, Serializable {

        private File here;

        public DataSetStoreImpl(File here) {
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
        public RecordMapping setLatestPrefix(String prefix) throws FileStoreException {
            File latestForPrefix = findLatestMappingFile(here, prefix);
            RecordMapping recordMapping;
            if (latestForPrefix == null) {
                recordMapping = new RecordMapping(prefix);
                setRecordMapping(recordMapping);
            }
            else {
                if (!latestForPrefix.setLastModified(System.currentTimeMillis())) {
                    throw new FileStoreException("Couldn't touch the file to give it priority");
                }
                recordMapping = getRecordMapping(prefix);
            }
            return recordMapping;
        }

        @Override
        public MetadataModel getMetadataModel() throws FileStoreException {
            try { // todo: this must be built from the record definition files present
                MetadataModelImpl metadataModel = new MetadataModelImpl();
                metadataModel.setRecordDefinitionResources(Arrays.asList(
                        "/ese-record-definition.xml",
                        "/icn-record-definition.xml",
                        "/abm-record-definition.xml"
                ));
                metadataModel.setDefaultPrefix("ese");
                return metadataModel;
            }
            catch (Exception e) {
                throw new FileStoreException("Unable to load metadata model");
            }
        }

        @Override
        public StoreState getState() {
            if (phantomFile(here).exists()) {
                return PHANTOM;
            }
            File imported = importedFile(here);
            File source = sourceFile(here);
            if (imported.exists()) {
                if (source.exists()) {
                    if (imported.lastModified() > source.lastModified()) {
                        if (statisticsFile(here, false).exists()) {
                            return IMPORTED_PENDING_CONVERT;
                        }
                        else {
                            return IMPORTED_PENDING_ANALYZE;
                        }
                    }
                    else {
                        return statePostSource();
                    }
                }
                else if (statisticsFile(here, false).exists()) {
                    return IMPORTED_PENDING_CONVERT;
                }
                else {
                    return IMPORTED_PENDING_ANALYZE;
                }
            }
            else if (source.exists()) {
                return statePostSource();
            }
            else {
                return EMPTY;
            }
        }

        private StoreState statePostSource() {
            File mapping = latestMappingFileOrNull(here);
            if (mapping != null) {
                if (validationFile(here, mapping).exists()) {
                    return VALIDATED;
                }
                else {
                    return MAPPED;
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
        public void setHints(Map<String, String> hints) throws FileStoreException {
            try {
                writeFacts(hintsFile(here), hints);
            }
            catch (IOException e) {
                throw new FileStoreException("Unable to set hints", e);
            }
        }

        @Override
        public boolean isRecentlyImported() {
            File importedFile = importedFile(here);
            File sourceFile = sourceFile(here);
            return importedFile.exists() && (!sourceFile.exists() || importedFile.lastModified() > sourceFile.lastModified());
        }

        @Override
        public InputStream importedInput() throws FileStoreException {
            return zipIn(importedFile(here));
        }

        @Override
        public InputStream sourceInput() throws FileStoreException {
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
                    catch (FileStoreException e1) {
                        // give up
                    }
                }
            }
            return null;
        }

        @Override
        public void setStatistics(Statistics statistics) throws FileStoreException {
            File statisticsFile = statisticsFile(here, statistics.isSourceFormat());
            try {
                ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(statisticsFile)));
                out.writeObject(statistics);
                out.close();
            }
            catch (IOException e) {
                throw new FileStoreException(String.format("Unable to save statistics file to %s", statisticsFile.getAbsolutePath()), e);
            }
        }

        @Override
        public RecordMapping getRecordMapping(String metadataPrefix) throws FileStoreException {
            RecordDefinition recordDefinition = metadataModel.getRecordDefinition(metadataPrefix);
            File file = findLatestMappingFile(here, metadataPrefix);
            if (file.exists()) {
                try {
                    FileInputStream is = new FileInputStream(file);
                    return RecordMapping.read(is, metadataModel);
                }
                catch (Exception e) {
                    throw new FileStoreException(String.format("Unable to read mapping from %s", file.getAbsolutePath()), e);
                }
            }
            else {
                return new RecordMapping(recordDefinition.prefix);
            }
        }

        @Override
        public void setRecordMapping(RecordMapping recordMapping) throws FileStoreException {
            File file = new File(here, String.format(MAPPING_FILE_PATTERN, recordMapping.getPrefix()));
            try {
                FileOutputStream out = new FileOutputStream(file);
                RecordMapping.write(recordMapping, out);
                out.close();
            }
            catch (IOException e) {
                throw new FileStoreException(String.format("Unable to save mapping to %s", file.getAbsolutePath()), e);
            }
        }

        @Override
        public void setValidation(String metadataPrefix, BitSet validation, int recordCount) throws FileStoreException {
            File file = new File(here, String.format(VALIDATION_FILE_PATTERN, metadataPrefix));
            try {
                DataOutputStream out = new DataOutputStream(new FileOutputStream(file));
                int invalidCount = recordCount - validation.cardinality();
                out.writeInt(invalidCount);
                for (int index = validation.nextClearBit(0); index >= 0 && index < recordCount; index = validation.nextClearBit(index+1)) {
                    out.writeInt(index);
                }
                out.close();
            }
            catch (IOException e) {
                throw new FileStoreException(String.format("Unable to save mapping to %s", file.getAbsolutePath()), e);
            }
        }

        @Override
        public PrintWriter reportWriter(RecordMapping recordMapping) throws FileStoreException {
            File file = new File(here, String.format(REPORT_FILE_PATTERN, recordMapping.getPrefix()));
            try {
                return new PrintWriter(file);
            }
            catch (IOException e) {
                throw new FileStoreException("Cannot read validation report", e);
            }
        }

        @Override
        public List<String> getReport(RecordMapping recordMapping) throws FileStoreException {
            try {
                File file = reportFile(here, recordMapping);
                return file.exists() ? FileUtils.readLines(file, "UTF-8") : null;
            }
            catch (IOException e) {
                throw new FileStoreException("Cannot read validation report", e);
            }
        }

        @Override
        public void externalToImported(File inputFile, ProgressListener progressListener) throws FileStoreException {
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
                throw new FileStoreException("Unable to capture XML input into " + source.getAbsolutePath(), e);
            }
            if (cancelled) {
                delete(source);
            }
            else {
                File hashedSource = new File(here, hasher.prefixFileName(IMPORTED_FILE_NAME));
                if (hashedSource.exists()) {
                    delete(source);
                    throw new FileStoreException("This import was identical to the previous one. Discarded.");
                }
                if (!source.renameTo(hashedSource)) {
                    throw new FileStoreException(String.format("Unable to rename %s to %s", source.getAbsolutePath(), hashedSource.getAbsolutePath()));
                }
            }
        }

        @Override
        public void importedToSource(ProgressListener progressListener) throws FileStoreException {
            if (!isRecentlyImported()) {
                throw new FileStoreException("Import to source would be redundant, since source is newer");
            }
            if (!statisticsFile(here, false).exists()) {
                throw new FileStoreException("No analysis stats so conversion doesn't trust the record count");
            }
            try {
                Map<String, String> hints = getHints();
                Path recordRoot = getRecordRoot(hints);
                int recordCount = getRecordCount(hints);
                SourceConverter converter = new SourceConverter(recordRoot, recordCount);
                converter.setProgressListener(progressListener);
                converter.parse(importedInput(), sourceOutput());
            }
            catch (XMLStreamException e) {
                throw new FileStoreException("Unable to convert source", e);
            }
            catch (IOException e) {
                throw new FileStoreException("Unable to convert source", e);
            }
        }

        @Override
        public List<File> getUploadFiles() throws FileStoreException {
            try {
                List<File> files = new ArrayList<File>();
                files.add(Hasher.ensureFileHashed(hintsFile(here)));
                files.add(Hasher.ensureFileHashed(sourceFile(here)));
                for (File file : findLatestMappingFiles(here)) {
                    files.add(Hasher.ensureFileHashed(file));
                }
                return files;
            }
            catch (IOException e) {
                throw new FileStoreException("Unable to collect upload files", e);
            }
        }

        @Override
        public void fromSipZip(ZipInputStream zipInputStream, ProgressListener progressListener) throws FileStoreException {
            ZipEntry zipEntry;
            byte[] buffer = new byte[BLOCK_SIZE];
            long totalBytesRead = 0;
            int bytesRead;
            boolean cancelled = false;
            try {
                while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                    String fileName = zipEntry.getName();
                    File file = new File(here, fileName);
                    if (fileName.equals(SOURCE_FILE_NAME)) {
                        Hasher hasher = new Hasher();
                        GZIPInputStream gzipInputStream = new GZIPInputStream(zipInputStream);
                        GZIPOutputStream outputStream = new GZIPOutputStream(new FileOutputStream(file));
                        while (!cancelled && -1 != (bytesRead = gzipInputStream.read(buffer))) {
                            outputStream.write(buffer, 0, bytesRead);
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
                        outputStream.close();
                        File hashedSource = new File(here, hasher.prefixFileName(SOURCE_FILE_NAME));
                        if (!file.renameTo(hashedSource)) {
                            throw new FileStoreException(String.format("Unable to rename %s to %s", file.getAbsolutePath(), hashedSource.getAbsolutePath()));
                        }
                    }
                    else {
                        IOUtils.copy(zipInputStream, new FileOutputStream(file));
                        Hasher.ensureFileHashed(file);
                    }
                }
            }
            catch (IOException e) {
                throw new FileStoreException("Unable to accept SipZip file", e);
            }
        }

        @Override
        public void remove() throws FileStoreException {
            delete(here);
        }

        public OutputStream sourceOutput() throws FileStoreException {
            return zipOut(SOURCE_FILE_NAME);
        }

        private InputStream zipIn(File file) throws FileStoreException {
            try {
                return new GZIPInputStream(new FileInputStream(file));
            }
            catch (IOException e) {
                throw new FileStoreException(String.format("Unable to create input stream from %s", file.getAbsolutePath()), e);
            }
        }

        private OutputStream zipOut(String fileName) throws FileStoreException {
            File file = new File(here, fileName);
            try {
                return new GZIPOutputStream(new FileOutputStream(file));
            }
            catch (IOException e) {
                throw new FileStoreException(String.format("Unable to create output stream from %s", file.getAbsolutePath()), e);
            }
        }

        @Override
        public String toString() {
            return getSpec();
        }
    }
}
