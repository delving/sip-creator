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

import eu.delving.metadata.Facts;
import eu.delving.metadata.FieldStatistics;
import eu.delving.metadata.Hasher;
import eu.delving.metadata.MetadataModel;
import eu.delving.metadata.RecordDefinition;
import eu.delving.metadata.RecordMapping;
import eu.delving.sip.ProgressListener;
import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * This interface describes how files are stored by the sip-creator
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class FileStoreImpl implements FileStore {

    private File home;
    private MetadataModel metadataModel;
    public static final int BLOCK_SIZE = 4096;
    public static final int MAX_HASH_HISTORY = 3;

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

        private File directory;

        public DataSetStoreImpl(File directory) {
            this.directory = directory;
        }

        @Override
        public String getSpec() {
            return directory.getName();
        }

        @Override
        public Facts getFacts() {
            File factsFile = getFactsFile();
            Facts facts = null;
            if (factsFile.exists()) {
                try {
                    facts = Facts.read(new FileInputStream(factsFile));
                }
                catch (Exception e) {
                    // eat this exception
                }
            }
            if (facts == null) {
                facts = new Facts();
            }
            return facts;
        }

        @Override
        public InputStream getImportedInputStream() throws FileStoreException {
            File imported = getImportedFile();
            try {
                return new GZIPInputStream(new FileInputStream(imported));
            }
            catch (IOException e) {
                throw new FileStoreException(String.format("Unable to create input stream from %s", imported.getAbsolutePath()), e);
            }
        }


        @Override
        public InputStream getSourceInputStream() throws FileStoreException {
            File source = getSourceFile();
            try {
                return new GZIPInputStream(new FileInputStream(source));
            }
            catch (IOException e) {
                throw new FileStoreException(String.format("Unable to create input stream from %s", source.getAbsolutePath()), e);
            }
        }

        @Override
        public List<FieldStatistics> getStatistics() {
            File statisticsFile = new File(directory, STATISTICS_FILE_NAME);
            if (statisticsFile.exists()) {
                try {
                    ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(statisticsFile)));
                    @SuppressWarnings("unchecked")
                    List<FieldStatistics> fieldStatisticsList = (List<FieldStatistics>) in.readObject();
                    in.close();
                    return fieldStatisticsList;
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
        public void setStatistics(List<FieldStatistics> fieldStatisticsList) throws FileStoreException {
            File statisticsFile = new File(directory, STATISTICS_FILE_NAME);
            try {
                ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(statisticsFile)));
                out.writeObject(fieldStatisticsList);
                out.close();
            }
            catch (IOException e) {
                throw new FileStoreException(String.format("Unable to save statistics file to %s", statisticsFile.getAbsolutePath()), e);
            }
        }

        @Override
        public RecordMapping getRecordMapping(String metadataPrefix) throws FileStoreException {
            RecordDefinition recordDefinition = metadataModel.getRecordDefinition(metadataPrefix);
            File mappingFile = findMappingFile(directory, metadataPrefix);
            if (mappingFile.exists()) {
                try {
                    FileInputStream is = new FileInputStream(mappingFile);
                    return RecordMapping.read(is, metadataModel);
                }
                catch (Exception e) {
                    throw new FileStoreException(String.format("Unable to read mapping from %s", mappingFile.getAbsolutePath()), e);
                }
            }
            else {
                return new RecordMapping(recordDefinition.prefix);
            }
        }

        @Override
        public void setRecordMapping(RecordMapping recordMapping) throws FileStoreException {
            File mappingFile = new File(directory, String.format(MAPPING_FILE_PATTERN, recordMapping.getPrefix()));
            try {
                FileOutputStream out = new FileOutputStream(mappingFile);
                RecordMapping.write(recordMapping, out);
                out.close();
            }
            catch (IOException e) {
                throw new FileStoreException(String.format("Unable to save mapping to %s", mappingFile.getAbsolutePath()), e);
            }
        }

        @Override
        public void remove() throws FileStoreException {
            delete(directory);
        }

        @Override
        public File getFactsFile() {
            return findFactsFile(directory);
        }

        @Override
        public File getImportedFile() {
            return findImportedFile(directory);
        }

        @Override
        public File getSourceFile() {
            return findSourceFile(directory);
        }

        @Override
        public File getMappingFile(String metadataPrefix) {
            return findMappingFile(directory, metadataPrefix);
        }

        @Override
        public List<String> getMappingPrefixes() {
            List<String> prefixes = new ArrayList<String>();
            for (File mappingFile : findMappingFiles(directory)) {
                String name = Hasher.extractFileName(mappingFile);
                name = name.substring(MAPPING_FILE_PREFIX.length());
                name = name.substring(0, name.length() - MAPPING_FILE_SUFFIX.length());
                prefixes.add(name);
            }
            return prefixes;
        }

        @Override
        public File getDiscardedFile(RecordMapping recordMapping) {
            return new File(directory, String.format(DISCARDED_FILE_PATTERN, recordMapping.getPrefix()));
        }

        @Override
        public void importSource(File inputFile, ProgressListener progressListener) throws FileStoreException {
            int fileBlocks = (int) (inputFile.length() / BLOCK_SIZE);
            if (progressListener != null) progressListener.setTotal(fileBlocks);
            File source = new File(directory, IMPORTED_FILE_NAME);
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
            }
            catch (Exception e) {
                if (progressListener != null) progressListener.finished(false);
                throw new FileStoreException("Unable to capture XML input into " + source.getAbsolutePath(), e);
            }
            if (cancelled) {
                delete(source);
            }
            else {
                File hashedSource = new File(directory, hasher.prefixFileName(IMPORTED_FILE_NAME));
                if (hashedSource.exists()) {
                    delete(source);
                    throw new FileStoreException("This import was identical to the previous one. Discarded.");
                }
                if (!source.renameTo(hashedSource)) {
                    throw new FileStoreException(String.format("Unable to rename %s to %s", source.getAbsolutePath(), hashedSource.getAbsolutePath()));
                }
                File statisticsFile = new File(directory, STATISTICS_FILE_NAME);
                delete(statisticsFile);
            }
        }

        @Override
        public void convertSource(ProgressListener progressListener) throws FileStoreException {
            // todo: get the record root (assuming it has been set)
            // todo: get the unique id path (minus the root is the unique id)
            // todo: take IMPORTED_FILE_NAME, and create standard sip-creator source format file
            // todo: include in the source: record root path and unique id path
        }

        @Override
        public void acceptSipZip(ZipInputStream zipInputStream, ProgressListener progressListener) throws FileStoreException {
            ZipEntry zipEntry;
            byte[] buffer = new byte[BLOCK_SIZE];
            long totalBytesRead = 0;
            int bytesRead;
            boolean cancelled = false;
            try {
                while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                    String fileName = zipEntry.getName();
                    File file = new File(directory, fileName);
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
                        File hashedSource = new File(directory, hasher.prefixFileName(SOURCE_FILE_NAME));
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
        public String toString() {
            return getSpec();
        }
    }

    private static class MappingOutputImpl {
        private RecordMapping recordMapping;
        private File discardedFile, normalizedFile;
        private Writer outputWriter, discardedWriter;
        private int recordsNormalized, recordsDiscarded;

        private MappingOutputImpl(String spec, RecordMapping recordMapping, File normalizedDirectory) throws FileStoreException {
            this.recordMapping = recordMapping;
            try {
                if (normalizedDirectory != null) {
                    this.normalizedFile = new File(normalizedDirectory, String.format("%s_%s_normalized.xml", spec, recordMapping.getPrefix()));
                    this.discardedFile = new File(normalizedDirectory, String.format("%s_%s_discarded.txt", spec, recordMapping.getPrefix()));
                    this.outputWriter = new OutputStreamWriter(new FileOutputStream(normalizedFile), "UTF-8");
                    this.discardedWriter = new OutputStreamWriter(new FileOutputStream(discardedFile), "UTF-8");
                }
            }
            catch (FileNotFoundException e) {
                throw new FileStoreException("Unable to create output files", e);
            }
            catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        public Writer getDiscardedWriter() {
            if (discardedWriter == null) {
                throw new RuntimeException("Discarded file was not to be stored");
            }
            return discardedWriter;
        }

        public void recordNormalized() {
            recordsNormalized++;
        }

        public void recordDiscarded() {
            recordsDiscarded++;
        }

        public void close(boolean abort) throws FileStoreException {
            try {
                if (abort) {
                    recordMapping.setRecordsNormalized(0);
                    recordMapping.setRecordsDiscarded(0);
                    recordMapping.setNormalizeTime(0);
                    if (outputWriter != null) {
                        outputWriter.close();
                        discardedWriter.close();
                    }
                    if (normalizedFile != null) {
                        delete(normalizedFile);
                    }
                }
                else {
                    if (outputWriter != null) {
                        outputWriter.close();
                        discardedWriter.close();
                    }
                    recordMapping.setRecordsNormalized(recordsNormalized);
                    recordMapping.setRecordsDiscarded(recordsDiscarded);
                    recordMapping.setNormalizeTime(System.currentTimeMillis());
                }
            }
            catch (IOException e) {
                throw new FileStoreException("Unable to close output", e);
            }
        }
    }

    private File findFactsFile(File dir) {
        File[] files = dir.listFiles(new FactsFileFilter());
        switch (files.length) {
            case 0:
                return new File(dir, FACTS_FILE_NAME);
            case 1:
                return files[0];
            default:
                for (File file : files) {
                    if (Hasher.extractHash(file) == null) {
                        return file;
                    }
                }
                return getMostRecent(files);
        }
    }

    private File findImportedFile(File dir) {
        File[] files = dir.listFiles(new ImportedFileFilter());
        switch (files.length) {
            case 0:
                return new File(dir, IMPORTED_FILE_NAME);
            case 1:
                return files[0];
            default:
                for (File file : files) {
                    if (Hasher.extractHash(file) == null) {
                        return file;
                    }
                }
                return getMostRecent(files);
        }
    }

    private File findSourceFile(File dir) {
        File[] files = dir.listFiles(new SourceFileFilter());
        switch (files.length) {
            case 0:
                return new File(dir, SOURCE_FILE_NAME);
            case 1:
                return files[0];
            default:
                for (File file : files) {
                    if (Hasher.extractHash(file) == null) {
                        return file;
                    }
                }
                return getMostRecent(files);
        }
    }

    private Collection<File> findMappingFiles(File dir) {
        File[] files = dir.listFiles(new MappingFileFilter());
        Map<String, List<File>> map = new TreeMap<String, List<File>>();
        for (File file : files) {
            String prefix = getMetadataPrefix(file);
            if (prefix == null) continue;
            List<File> list = map.get(prefix);
            if (list == null) {
                map.put(prefix, list = new ArrayList<File>());
            }
            list.add(file);
        }
        List<File> mappingFiles = new ArrayList<File>();
        for (Map.Entry<String, List<File>> entry : map.entrySet()) {
            if (entry.getValue().size() == 1) {
                mappingFiles.add(entry.getValue().get(0));
            }
            else {
                mappingFiles.add(getMostRecent(entry.getValue().toArray(new File[entry.getValue().size()])));
            }
        }
        return mappingFiles;
    }

    private File findMappingFile(File dir, String metadataPrefix) {
        File mappingFile = null;
        for (File file : findMappingFiles(dir)) {
            String prefix = getMetadataPrefix(file);
            if (prefix.equals(metadataPrefix)) {
                mappingFile = file;
            }
        }
        if (mappingFile == null) {
            mappingFile = new File(dir, String.format(MAPPING_FILE_PATTERN, metadataPrefix));
        }
        return mappingFile;
    }

    private class FactsFileFilter implements FileFilter {
        @Override
        public boolean accept(File file) {
            return file.isFile() && FACTS_FILE_NAME.equals(Hasher.extractFileName(file));
        }
    }

    private class ImportedFileFilter implements FileFilter {
        @Override
        public boolean accept(File file) {
            return file.isFile() && IMPORTED_FILE_NAME.equals(Hasher.extractFileName(file));
        }
    }

    private class SourceFileFilter implements FileFilter {
        @Override
        public boolean accept(File file) {
            return file.isFile() && SOURCE_FILE_NAME.equals(Hasher.extractFileName(file));
        }
    }

    private class MappingFileFilter implements FileFilter {
        @Override
        public boolean accept(File file) {
            String name = Hasher.extractFileName(file);
            return file.isFile() && name.startsWith(MAPPING_FILE_PREFIX) && name.endsWith(MAPPING_FILE_SUFFIX);
        }
    }

    private String getMetadataPrefix(File file) {
        String name = Hasher.extractFileName(file);
        if (name.startsWith(MAPPING_FILE_PREFIX) && name.endsWith(MAPPING_FILE_SUFFIX)) {
            name = name.substring(MAPPING_FILE_PREFIX.length());
            name = name.substring(0, name.length() - MAPPING_FILE_SUFFIX.length());
            return name;
        }
        else {
            return null;
        }
    }

    static void delete(File file) throws FileStoreException {
        if (file.exists()) {
            if (file.isDirectory()) {
                for (File sub : file.listFiles()) {
                    delete(sub);
                }
            }
            if (!file.delete()) {
                throw new FileStoreException(String.format("Unable to delete %s", file.getAbsolutePath()));
            }
        }
    }

    private File getMostRecent(File[] files) {
        if (files.length > 0) {
            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    long lastA = a.lastModified();
                    long lastB = b.lastModified();
                    if (lastA > lastB) {
                        return -1;
                    }
                    else if (lastA < lastB) {
                        return 1;
                    }
                    else {
                        return 0;
                    }
                }
            });
            if (files.length > MAX_HASH_HISTORY) {
                for (int walk = MAX_HASH_HISTORY; walk < files.length; walk++) {
                    //noinspection ResultOfMethodCallIgnored
                    files[walk].delete();
                }
            }
            return files[0];
        }
        else {
            return null;
        }
    }
}
