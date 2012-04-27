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

import eu.delving.metadata.Hasher;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecMapping;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;

import static eu.delving.sip.files.Storage.FileType.*;
import static eu.delving.sip.files.Storage.*;

/**
 * This class contains helpers for the StorageImpl to lean on
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class StorageBase {
    public static final int BLOCK_SIZE = 4096;

    public static File createDataSetDirectory(File home, String spec, String organization) {
        return new File(home, String.format("%s_%s", spec, organization));
    }

    public static String getSpecFromDirectory(File directory) {
        int underscore = directory.getName().indexOf("_");
        if (underscore < 0) throw new IllegalStateException("Directory must be in the form spec_organization");
        return directory.getName().substring(0, underscore);
    }

    public static String getOrganizationFromDirectory(File directory) {
        int underscore = directory.getName().indexOf("_");
        if (underscore < 0) throw new IllegalStateException("Directory must be in the form spec_organization");
        return directory.getName().substring(underscore + 1);
    }

    public static Path getRecordRoot(Map<String, String> hints) throws StorageException {
        String recordRoot = hints.get(RECORD_ROOT_PATH);
        if (recordRoot == null) throw new StorageException("Must have record root path");
        return Path.create(recordRoot);
    }

    public static int getRecordCount(Map<String, String> hints) throws StorageException {
        String recordCount = hints.get(RECORD_COUNT);
        int count = 0;
        try {
            count = Integer.parseInt(recordCount);
        }
        catch (Exception e) { /* nothing */ }
        if (count == 0) throw new StorageException("Must have nonzero record count");
        return count;
    }

    public static Path getUniqueElement(Map<String, String> hints) throws StorageException {
        String uniqueElement = hints.get(UNIQUE_ELEMENT_PATH);
        if (uniqueElement == null) throw new StorageException("Must have unique element path");
        return Path.create(uniqueElement);
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

    File helpFile(File dir) {
        return new File(dir, "help.html");
    }

    File factsFile(File dir) {
        return findOrCreate(dir, FACTS);
    }

    File hintsFile(File dir) {
        return findOrCreate(dir, HINTS);
    }

    File importedFile(File dir) {
        return findOrCreate(dir, IMPORTED);
    }

    File sourceFile(File dir) {
        return findOrCreate(dir, SOURCE);
    }

    File latestMappingFileOrNull(File dir) {
        return findOrNull(dir, 0, new PrefixFileFilter(MAPPING), MAPPING);
    }

    File validationFile(File dir, File mappingFile) {
        String prefix = extractName(mappingFile, MAPPING);
        String name = VALIDATION.getName(prefix);
        return findOrCreate(dir, name, new NameFileFilter(name), VALIDATION);
    }

    File[] validationFiles(File dir, String prefix) {
        return dir.listFiles(new NameFileFilter(VALIDATION.getName(prefix)));
    }

    File[] validationFiles(File dir) {
        return dir.listFiles(new PrefixFileFilter(VALIDATION));
    }

    File reportFile(File dir, RecMapping recMapping) {
        return new File(dir, REPORT.getName(recMapping.getPrefix()));
    }

    File statsFile(File dir, boolean sourceFormat, String prefix) {
        if (prefix == null) {
            return new File(dir, sourceFormat ? SOURCE_STATS.getName() : IMPORT_STATS.getName());
        }
        else {
            return findOrCreate(dir, RESULT_STATS.getName(prefix), new PrefixFileFilter(RESULT_STATS), RESULT_STATS);
        }
    }

    File statsFile(File dir, File mappingFile) {
        String prefix = extractName(mappingFile, MAPPING);
        String name = RESULT_STATS.getName(prefix);
        return findOrCreate(dir, name, new NameFileFilter(name), RESULT_STATS);
    }

    private File findOrCreate(File directory, Storage.FileType fileType) {
        if (fileType.getName() == null) throw new RuntimeException("Expected name");
        File file = findOrNull(directory, 0, new NameFileFilter(fileType.getName()), fileType);
        if (file == null) file = new File(directory, fileType.getName());
        return file;
    }

    private File findOrCreate(File directory, String name, FileFilter fileFilter, Storage.FileType fileType) {
        File file = findOrNull(directory, 0, fileFilter, fileType);
        if (file == null) file = new File(directory, name);
        return file;
    }

    private File findOrNull(File directory, int which, FileFilter fileFilter, Storage.FileType fileType) {
        File[] files = directory.listFiles(fileFilter);
        return getRecent(files, which, fileType);
    }

    File recordDefinitionFile(File dir, String prefix) {
        return new File(dir, RECORD_DEFINITION.getName(prefix));
    }

    File schemaFile(File dir, String prefix) {
        return new File(dir, SCHEMA.getName(prefix));
    }

    List<File> findRecordDefinitionFiles(File dir) {
        File[] files = dir.listFiles(new SuffixFileFilter(Storage.FileType.RECORD_DEFINITION));
        return Arrays.asList(files);
    }

    File findLatestMappingFile(File dir, String metadataPrefix) {
        File mappingFile = null;
        for (File file : findLatestFiles(dir, MAPPING)) {
            String prefix = extractName(file, MAPPING);
            if (prefix.equals(metadataPrefix)) mappingFile = file;
        }
        if (mappingFile == null) mappingFile = new File(dir, MAPPING.getName(metadataPrefix));
        return mappingFile;
    }

    Collection<File> findLatestMappingFiles(File dir) {
        return findLatestFiles(dir, MAPPING);
    }

    List<File> findHashedMappingFiles(File dir, String prefix) {
        File[] files = dir.listFiles(new HashedMappingFileFilter(prefix));
        List<File> sorted = new ArrayList<File>(Arrays.asList(files));
        Collections.sort(sorted, new LastModifiedComparator());
        return sorted;
    }

    Collection<File> findLatestFiles(File dir, Storage.FileType fileType) {
        File[] files = dir.listFiles(new PrefixFileFilter(fileType));
        Map<String, List<File>> map = new TreeMap<String, List<File>>();
        for (File file : files) {
            String prefix = extractName(file, fileType);
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
                latestFiles.add(getRecent(entry.getValue().toArray(new File[entry.getValue().size()]), 0, fileType));
            }
        }
        return latestFiles;
    }

    class PrefixFileFilter implements FileFilter {
        private Storage.FileType fileType;

        PrefixFileFilter(Storage.FileType fileType) {
            this.fileType = fileType;
        }

        @Override
        public boolean accept(File file) {
            String name = Hasher.extractFileName(file);
            return file.isFile() && name.startsWith(fileType.getPrefix());
        }
    }

    class SuffixFileFilter implements FileFilter {
        private Storage.FileType fileType;

        SuffixFileFilter(Storage.FileType fileType) {
            this.fileType = fileType;
        }

        @Override
        public boolean accept(File file) {
            String name = file.getName();
            return file.isFile() && name.endsWith(fileType.getSuffix());
        }
    }

    class HashedMappingFileFilter implements FileFilter {
        private String ending;

        HashedMappingFileFilter(String prefix) {
            this.ending = MAPPING.getName(prefix);
        }

        @Override
        public boolean accept(File file) {
            String name = file.getName();
            return file.isFile() && name.endsWith(ending);
        }
    }

    String extractName(File file, Storage.FileType fileType) {
        String name = Hasher.extractFileName(file);
        if (name.startsWith(fileType.getPrefix()) && name.endsWith(fileType.getSuffix())) {
            name = name.substring(fileType.getPrefix().length());
            name = name.substring(0, name.length() - fileType.getSuffix().length());
            return name;
        }
        else {
            return null;
        }

    }

    static void delete(File file) throws StorageException {
        if (file.exists()) {
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                if (files != null) for (File sub : files) delete(sub);
            }
            if (!file.delete()) {
                throw new StorageException(String.format("Unable to delete %s", file.getAbsolutePath()));
            }
        }
    }

    File getRecent(File[] files, int which, Storage.FileType fileType) {
        int maxHistory = fileType.getHistorySize();
        if (files == null || files.length <= which || which > maxHistory) {
            return null;
        }
        Arrays.sort(files, new LastModifiedComparator());
        if (files.length > maxHistory) {
            for (int walk = maxHistory; walk < files.length; walk++) {
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

    public static class LastModifiedComparator implements Comparator<File> {

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
            else { // lastModified is only accurate to seconds (ends in 000) and we want the unhashed one at the top
                int nameA = a.getName().length();
                int nameB = b.getName().length();
                if (nameA > nameB) {
                    return 1;
                }
                else if (nameB < nameA) {
                    return -1;
                }
                else {
                    return 0;
                }
            }
        }
    }
}
