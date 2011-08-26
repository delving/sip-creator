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

import static eu.delving.sip.files.FileStore.FACTS_FILE_NAME;
import static eu.delving.sip.files.FileStore.HINTS_FILE_NAME;
import static eu.delving.sip.files.FileStore.IMPORTED_FILE_NAME;
import static eu.delving.sip.files.FileStore.MAPPING_FILE_PREFIX;
import static eu.delving.sip.files.FileStore.MAPPING_FILE_SUFFIX;
import static eu.delving.sip.files.FileStore.SOURCE_FILE_NAME;

/**
 * This class contains helpers for the FileStoreImpl to lean on
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class FileStoreBase {
    public static final int BLOCK_SIZE = 4096;
    public static final int MAX_HASH_HISTORY = 5;

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
        return findOrCreate(dir, FACTS_FILE_NAME, new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isFile() && FileStore.FACTS_FILE_NAME.equals(Hasher.extractFileName(file));
            }

        });
    }

    File hintsFile(File dir) {
        return findOrCreate(dir, HINTS_FILE_NAME, new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isFile() && FileStore.HINTS_FILE_NAME.equals(Hasher.extractFileName(file));
            }
        });
    }

    File importedFile(File dir) {
        return findOrCreate(dir, IMPORTED_FILE_NAME, new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isFile() && IMPORTED_FILE_NAME.equals(Hasher.extractFileName(file));
            }
        });
    }

    File sourceFile(File dir) {
        return findOrCreate(dir, SOURCE_FILE_NAME, new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isFile() && SOURCE_FILE_NAME.equals(Hasher.extractFileName(file));
            }
        });
    }

    private File findOrCreate(File directory, String name, FileFilter fileFilter) {
        File[] files = directory.listFiles(fileFilter);
        switch (files.length) {
            case 0:
                return new File(directory, name);
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

    Collection<File> findMappingFiles(File dir) {
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

    File findMappingFile(File dir, String metadataPrefix) {
        File mappingFile = null;
        for (File file : findMappingFiles(dir)) {
            String prefix = getMetadataPrefix(file);
            if (prefix.equals(metadataPrefix)) {
                mappingFile = file;
            }
        }
        if (mappingFile == null) {
            mappingFile = new File(dir, String.format(FileStore.MAPPING_FILE_PATTERN, metadataPrefix));
        }
        return mappingFile;
    }

    class MappingFileFilter implements FileFilter {
        @Override
        public boolean accept(File file) {
            String name = Hasher.extractFileName(file);
            return file.isFile() && name.startsWith(MAPPING_FILE_PREFIX) && name.endsWith(MAPPING_FILE_SUFFIX);
        }
    }

    String getMetadataPrefix(File file) {
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

    File getMostRecent(File[] files) {
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
