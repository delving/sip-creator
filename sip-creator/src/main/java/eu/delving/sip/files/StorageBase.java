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

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import eu.delving.metadata.ElementDefinition;
import eu.delving.metadata.FactDefinition;
import eu.delving.metadata.FieldDefinition;
import eu.delving.metadata.Hasher;
import eu.delving.metadata.MetadataException;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecordDefinition;
import eu.delving.metadata.RecordMapping;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static eu.delving.sip.files.Storage.ANALYSIS_STATS_FILE_NAME;
import static eu.delving.sip.files.Storage.FACTS_FILE_NAME;
import static eu.delving.sip.files.Storage.FACT_DEFINITION_FILE_NAME;
import static eu.delving.sip.files.Storage.HINTS_FILE_NAME;
import static eu.delving.sip.files.Storage.IMPORTED_FILE_NAME;
import static eu.delving.sip.files.Storage.MAPPING_FILE_PREFIX;
import static eu.delving.sip.files.Storage.MAPPING_FILE_SUFFIX;
import static eu.delving.sip.files.Storage.PHANTOM_FILE_NAME;
import static eu.delving.sip.files.Storage.RECORD_COUNT;
import static eu.delving.sip.files.Storage.RECORD_DEFINITION_FILE_SUFFIX;
import static eu.delving.sip.files.Storage.RECORD_ROOT_PATH;
import static eu.delving.sip.files.Storage.REPORT_FILE_PATTERN;
import static eu.delving.sip.files.Storage.SOURCE_FILE_NAME;
import static eu.delving.sip.files.Storage.SOURCE_STATS_FILE_NAME;
import static eu.delving.sip.files.Storage.UNIQUE_ELEMENT_PATH;
import static eu.delving.sip.files.Storage.VALIDATION_FILE_PATTERN;

/**
 * This class contains helpers for the StorageImpl to lean on
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class StorageBase {
    public static final int BLOCK_SIZE = 4096;
    public static final int MAX_HASH_HISTORY = 5;

    public static Path getRecordRoot(Map<String,String> hints) throws StorageException {
        String recordRoot = hints.get(RECORD_ROOT_PATH);
        if (recordRoot == null) {
            throw new StorageException("Must have record root path");
        }
        return new Path(recordRoot);
    }

    public static int getRecordCount(Map<String,String> hints) throws StorageException {
        String recordCount = hints.get(RECORD_COUNT);
        int count = 0;
        try {
            count = Integer.parseInt(recordCount);
        }
        catch (Exception e) { /* nothing */ }
        if (count == 0) {
            throw new StorageException("Must have nonzero record count");
        }
        return count;
    }

    public static Path getUniqueElement(Map<String,String> hints) throws StorageException {
        String uniqueElement = hints.get(UNIQUE_ELEMENT_PATH);
        if (uniqueElement == null) {
            throw new StorageException("Must have unique element path");
        }
        return new Path(uniqueElement);
    }

    Map<String, String> readFacts(File file) throws IOException {
        Map<String, String> facts = new TreeMap<String, String>();
        if (file.exists()) {
            List<String> lines = FileUtils.readLines(file, "UTF-8");
            for (String line : lines) {
                if (line.startsWith("#")) continue;
                int equals = line.indexOf("=");
                if (equals < 0) {
                    continue;
                }
                String name = line.substring(0, equals).trim();
                String value = line.substring(equals + 1).trim();
                facts.put(name, value);
            }
        }
        return facts;
    }

    boolean allHintsSet(Map<String, String> hints) {
        String recordRoot = hints.get(RECORD_ROOT_PATH);
        String recordCount = hints.get(RECORD_COUNT);
        String uniqueElement = hints.get(UNIQUE_ELEMENT_PATH);
        if (recordRoot == null || recordCount == null || uniqueElement == null) return false;
        if (!uniqueElement.startsWith(recordRoot)) return false;
        try {
            if (Integer.parseInt(recordCount) <= 0) return false;
        }
        catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    void writeFacts(File file, Map<String, String> facts) throws IOException {
        List<String> lines = new ArrayList<String>();
        lines.add("#SIPCreator - facts");
        for (Map.Entry<String, String> entry : facts.entrySet()) {
            lines.add(String.format("%s=%s", entry.getKey(), entry.getValue()));
        }
        FileUtils.writeLines(file, lines);
    }

    File factsFile(File dir) {
        return findOrCreate(dir, FACTS_FILE_NAME, new NameFileFilter(FACTS_FILE_NAME));
    }

    File hintsFile(File dir) {
        return findOrCreate(dir, HINTS_FILE_NAME, new NameFileFilter(HINTS_FILE_NAME));
    }

    File importedFile(File dir) {
        return findOrCreate(dir, IMPORTED_FILE_NAME, new NameFileFilter(IMPORTED_FILE_NAME));
    }

    File sourceFile(File dir) {
        return findOrCreate(dir, SOURCE_FILE_NAME, new NameFileFilter(SOURCE_FILE_NAME));
    }

    File previousSourceOrNull(File dir) {
        return findOrNull(dir, 1, new NameFileFilter(SOURCE_FILE_NAME));
    }

    File latestMappingFileOrNull(File dir) {
        return findOrNull(dir, 0, new MappingFileFilter());
    }

    File validationFile(File dir, File mappingFile) {
        String prefix = mappingPrefix(mappingFile);
        String name = String.format(VALIDATION_FILE_PATTERN, prefix);
        return findOrCreate(dir, name, new NameFileFilter(name));
    }

    File[] validationFiles(File dir, String prefix) {
        String name = String.format(VALIDATION_FILE_PATTERN, prefix);
        return dir.listFiles(new NameFileFilter(name));
    }

    File reportFile(File dir, RecordMapping recordMapping) {
        return new File(dir, String.format(REPORT_FILE_PATTERN, recordMapping.getPrefix()));
    }

    File statisticsFile(File dir, boolean sourceFormat) {
        return new File(dir, sourceFormat ? SOURCE_STATS_FILE_NAME : ANALYSIS_STATS_FILE_NAME);
    }

    File phantomFile(File dir) {
        return new File(dir, PHANTOM_FILE_NAME);
    }

    private File findOrCreate(File directory, String name, FileFilter fileFilter) {
        File file = findOrNull(directory, 0, fileFilter);
        if (file == null) {
            file = new File(directory, name);
        }
        return file;
    }

    private File findOrNull(File directory, int which, FileFilter fileFilter) {
        File[] files = directory.listFiles(fileFilter);
        return getRecent(files, which);
    }

    File factDefinitionFile(File dir) {
        return new File(dir, FACT_DEFINITION_FILE_NAME);
    }

    List<File> findRecordDefinitionFiles(File dir) {
        File [] files = dir.listFiles(new RecordDefinitionFileFilter());
        return Arrays.asList(files);
    }

    File findLatestMappingFile(File dir, String metadataPrefix) {
        return findLatestFile(dir, metadataPrefix, new MappingFileFilter());
    }

    File findLatestFile(File dir, String metadataPrefix, FileFilter fileFilter) {
        File mappingFile = null;
        for (File file : findLatestFiles(dir, fileFilter)) {
            String prefix = mappingPrefix(file);
            if (prefix.equals(metadataPrefix)) {
                mappingFile = file;
            }
        }
        if (mappingFile == null) {
            mappingFile = new File(dir, String.format(Storage.MAPPING_FILE_PATTERN, metadataPrefix));
        }
        return mappingFile;
    }

    Collection<File> findLatestMappingFiles(File dir) {
        return findLatestFiles(dir, new MappingFileFilter());
    }

    Collection<File> findLatestFiles(File dir, FileFilter fileFilter) {
        File[] files = dir.listFiles(fileFilter);
        Map<String, List<File>> map = new TreeMap<String, List<File>>();
        for (File file : files) {
            String prefix = mappingPrefix(file);
            if (prefix == null) continue;
            List<File> list = map.get(prefix);
            if (list == null) {
                map.put(prefix, list = new ArrayList<File>());
            }
            list.add(file);
        }
        List<File> latestFiles = new ArrayList<File>();
        for (Map.Entry<String, List<File>> entry : map.entrySet()) {
            if (entry.getValue().size() == 1) {
                latestFiles.add(entry.getValue().get(0));
            }
            else {
                latestFiles.add(getRecent(entry.getValue().toArray(new File[entry.getValue().size()]), 0));
            }
        }
        return latestFiles;
    }

    class MappingFileFilter implements FileFilter {
        @Override
        public boolean accept(File file) {
            String name = Hasher.extractFileName(file);
            return file.isFile() && name.startsWith(MAPPING_FILE_PREFIX) && name.endsWith(MAPPING_FILE_SUFFIX);
        }
    }

    class RecordDefinitionFileFilter implements FileFilter {
        @Override
        public boolean accept(File file) {
            String name = file.getName();
            return file.isFile() && name.endsWith(RECORD_DEFINITION_FILE_SUFFIX);
        }
    }

    String mappingPrefix(File file) {
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

    static void delete(File file) throws StorageException {
        if (file.exists()) {
            if (file.isDirectory()) {
                for (File sub : file.listFiles()) {
                    delete(sub);
                }
            }
            if (!file.delete()) {
                throw new StorageException(String.format("Unable to delete %s", file.getAbsolutePath()));
            }
        }
    }

    File getRecent(File[] files, int which) {
        if (files == null || files.length <= which || which > MAX_HASH_HISTORY) {
            return null;
        }
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
        if (files.length > MAX_HASH_HISTORY) { // todo: vary this per file type
            for (int walk = MAX_HASH_HISTORY; walk < files.length; walk++) {
                //noinspection ResultOfMethodCallIgnored
                files[walk].delete();
            }
        }
        return files[which];
    }

    private class NameFileFilter implements FileFilter {
        private String name;

        private NameFileFilter(String name) {
            this.name = name;
        }

        @Override
        public boolean accept(File file) {
            return file.isFile() && Hasher.extractFileName(file).equals(name);
        }
    }

    public static RecordDefinition readRecordDefinition(InputStream in, List<FactDefinition> factDefinitions) throws MetadataException {
        try {
            Reader inReader = new InputStreamReader(in, "UTF-8");
            RecordDefinition recordDefinition = (RecordDefinition) recordStream().fromXML(inReader);
            recordDefinition.initialize(factDefinitions);
            return recordDefinition;
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }



    static XStream recordStream() {
        XStream stream = new XStream(new PureJavaReflectionProvider());
        stream.processAnnotations(new Class[]{
                FactDefinition.List.class,
                RecordDefinition.class,
                ElementDefinition.class,
                FieldDefinition.class
        });
        return stream;
    }


}
