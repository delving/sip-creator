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

import eu.delving.metadata.Hasher;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecordMapping;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static eu.delving.sip.files.FileStore.ANALYSIS_STATS_FILE_NAME;
import static eu.delving.sip.files.FileStore.FACTS_FILE_NAME;
import static eu.delving.sip.files.FileStore.HINTS_FILE_NAME;
import static eu.delving.sip.files.FileStore.IMPORTED_FILE_NAME;
import static eu.delving.sip.files.FileStore.MAPPING_FILE_PREFIX;
import static eu.delving.sip.files.FileStore.MAPPING_FILE_SUFFIX;
import static eu.delving.sip.files.FileStore.PHANTOM_FILE_NAME;
import static eu.delving.sip.files.FileStore.RECORD_COUNT;
import static eu.delving.sip.files.FileStore.RECORD_ROOT_PATH;
import static eu.delving.sip.files.FileStore.REPORT_FILE_PATTERN;
import static eu.delving.sip.files.FileStore.SOURCE_FILE_NAME;
import static eu.delving.sip.files.FileStore.SOURCE_STATS_FILE_NAME;
import static eu.delving.sip.files.FileStore.UNIQUE_ELEMENT_PATH;
import static eu.delving.sip.files.FileStore.VALIDATION_FILE_PATTERN;

/**
 * This class contains helpers for the FileStoreImpl to lean on
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class FileStoreBase {
    public static final int BLOCK_SIZE = 4096;
    public static final int MAX_HASH_HISTORY = 5;

    public static Path getRecordRoot(Map<String,String> hints) throws FileStoreException {
        String recordRoot = hints.get(RECORD_ROOT_PATH);
        if (recordRoot == null) {
            throw new FileStoreException("Must have record root path");
        }
        return new Path(recordRoot);
    }

    public static int getRecordCount(Map<String,String> hints) throws FileStoreException {
        String recordCount = hints.get(RECORD_COUNT);
        int count = 0;
        try {
            count = Integer.parseInt(recordCount);
        }
        catch (Exception e) { /* nothing */ }
        if (count == 0) {
            throw new FileStoreException("Must have nonzero record count");
        }
        return count;
    }

    public static Path getUniqueElement(Map<String,String> hints) throws FileStoreException {
        String uniqueElement = hints.get(UNIQUE_ELEMENT_PATH);
        if (uniqueElement == null) {
            throw new FileStoreException("Must have unique element path");
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
        return new File(dir, String.format(VALIDATION_FILE_PATTERN, prefix));
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
            mappingFile = new File(dir, String.format(FileStore.MAPPING_FILE_PATTERN, metadataPrefix));
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

    File getRecent(File[] files, int which) {
        if (files.length <= which || which > MAX_HASH_HISTORY) {
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
}
