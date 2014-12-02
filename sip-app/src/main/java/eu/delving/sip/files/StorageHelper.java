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
import eu.delving.stats.Stats;
import org.apache.commons.io.FileUtils;
import org.joda.time.DateTime;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static eu.delving.sip.files.Storage.FileType;
import static eu.delving.sip.files.Storage.FileType.*;
import static eu.delving.sip.files.Storage.MAX_UNIQUE_VALUE_LENGTH;
import static eu.delving.sip.files.Storage.UNIQUE_VALUE_CONVERTER;

/**
 * This class contains helpers for the StorageImpl to lean on.  It does all of the searching for file name
 * patterns that the storage system needs, as well as maintaining the previous versions of various
 * types of files.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class StorageHelper {
    static final int BLOCK_SIZE = 4096;
    static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy_MM_dd_HH_mm");

    static File createDataSetDirectory(File home, String spec) {
        return new File(home, spec);
    }

    public static int getMaxUniqueValueLength(Map<String, String> hints) {
        String max = hints.get(MAX_UNIQUE_VALUE_LENGTH);
        return max == null ? Stats.DEFAULT_MAX_UNIQUE_VALUE_LENGTH : Integer.parseInt(max);
    }

    public static String getUniqueValueConverter(Map<String, String> hints) {
        return hints.get(UNIQUE_VALUE_CONVERTER);
    }

    static Map<String, String> readFacts(File file) throws IOException {
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

    static void writeFacts(File file, Map<String, String> facts) throws IOException {
        List<String> lines = new ArrayList<String>();
        lines.add("#SIPCreator - facts");
        for (Map.Entry<String, String> entry : facts.entrySet()) {
            lines.add(String.format("%s=%s", entry.getKey(), entry.getValue()));
        }
        FileUtils.writeLines(file, lines);
    }

    static File factsFile(File dir) {
        return findOrCreate(dir, FACTS);
    }

    static File narthexFactsFile(File dir) {
        return new File(dir, "narthex_facts.txt");
    }

    static File hintsFile(File dir) {
        return findOrCreate(dir, HINTS);
    }

    static File sourceFile(File dir) {
        return findOrCreate(dir, SOURCE);
    }

    static File latestMappingFileOrNull(File dir) {
        return findOrNull(dir, 0, new PrefixFileFilter(MAPPING), MAPPING);
    }

    static File targetFile(File dir, Map<String, String> facts, String prefix) {
        String fileName = String.format("%s__%s.xml.gz", facts.get("spec"), prefix);
        return new File(dir, fileName);
    }

    static InputStream zipIn(File file) throws StorageException {
        try {
            return new GZIPInputStream(new FileInputStream(file));
        }
        catch (IOException e) {
            throw new StorageException(String.format("Unable to create input stream from %s", file.getAbsolutePath()), e);
        }
    }

    public static OutputStream zipOut(File file) throws StorageException {
        try {
            return new GZIPOutputStream(new FileOutputStream(file));
        }
        catch (IOException e) {
            throw new StorageException(String.format("Unable to create output stream from %s", file.getAbsolutePath()), e);
        }
    }

    static File reportFile(File dir, String prefix) {
        return new File(dir, REPORT.getName(prefix));
    }

    static File reportIndexFile(File dir, String prefix) {
        return new File(dir, REPORT_INDEX.getName(prefix));
    }

    static File linkFile(File dir, String prefix) {
        return new File(dir, LINKS.getName(prefix));
    }

    public static File statsFile(File dir) {
        return new File(dir, SOURCE_STATS.getName());
    }

    static File sipZip(File dir, String spec, String prefix) {
        String name = String.format("%s__%s__%s.sip.zip", spec, DATE_FORMAT.format(new Date()), prefix);
        return new File(dir, name);
    }

    public static String datasetNameFromSipZip(File file) {
        String n = file.getName();
        int uu = n.indexOf("__");
        if (uu < 0) throw new RuntimeException("No dataset name contained in " + n);
        return n.substring(0, uu);
    }

    public static DateTime dateTimeFromSipZip(File file) {
        return dateTimeFromSipZipName(file.getName());
    }

    public static DateTime dateTimeFromSipZipName(String n) {
        Matcher matcher = EXTRACT_DATE.matcher(n);
        if (matcher.matches()) {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));
            int hour = Integer.parseInt(matcher.group(4));
            int minute = Integer.parseInt(matcher.group(5));
            return new DateTime(year, month, day, hour, minute);
        }
        else {
            return new DateTime();
        }
    }

    static Pattern EXTRACT_DATE = Pattern.compile(".*(\\d{4})_(\\d{2})_(\\d{2})_(\\d{2})_(\\d{2}).*");

    static File findOrCreate(File directory, Storage.FileType fileType) {
        if (fileType.getName() == null) throw new RuntimeException("Expected name");
        File file = findOrNull(directory, 0, new NameFileFilter(fileType.getName()), fileType);
        if (file == null) file = new File(directory, fileType.getName());
        return file;
    }

    static File findOrCreate(File directory, Storage.FileType fileType, String prefix) {
        if (fileType.getName(prefix) == null) throw new RuntimeException("Expected name");
        File file = findOrNull(directory, 0, new NameFileFilter(fileType.getName(prefix)), fileType);
        if (file == null) file = new File(directory, fileType.getName(prefix));
        return file;
    }

    static File findOrCreate(File directory, String name, FileFilter fileFilter, Storage.FileType fileType) {
        File file = findOrNull(directory, 0, fileFilter, fileType);
        if (file == null) file = new File(directory, name);
        return file;
    }

    static File findOrNull(File directory, int which, FileFilter fileFilter, Storage.FileType fileType) {
        File[] files = directory.listFiles(fileFilter);
        return getRecent(files, which, fileType);
    }

    static void addLatestNoHash(File dir, FileType fileType, String prefix, List<File> list) throws StorageException {
        File latestFile = findLatestFile(dir, fileType, prefix);
        if (!latestFile.exists()) return;
        try {
            list.add(Hasher.ensureFileNotHashed(latestFile));
        }
        catch (IOException e) {
            throw new StorageException("Unable to hash file " + latestFile);
        }
    }

    static File findLatestFile(File dir, FileType fileType, String prefix) {
        File latestFile = null;
        for (File file : findLatestPrefixFiles(dir, fileType)) {
            String filePrefix = extractName(file, fileType);
            if (filePrefix.equals(prefix)) latestFile = file;
        }
        if (latestFile == null) latestFile = new File(dir, fileType.getName(prefix));
        return latestFile;
    }

    static List<File> findHashedPrefixFiles(File dir, FileType fileType, String prefix) {
        File[] files = dir.listFiles(new HashedPrefixFileFilter(fileType, prefix));
        List<File> sorted = new ArrayList<File>(Arrays.asList(files));
        Collections.sort(sorted, new LastModifiedComparator());
        return sorted;
    }

    static Collection<File> findSourceFiles(File dir) {
        File[] files = dir.listFiles(new NameFileFilter(SOURCE.getName()));
        return Arrays.asList(files);
    }

    static Collection<File> findLatestPrefixFiles(File dir, Storage.FileType fileType) {
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

    static class PrefixFileFilter implements FileFilter {
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

    static String extractName(File file, Storage.FileType fileType) {
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

    static void delete(File file) {
        if (file.exists()) {
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                if (files != null) for (File sub : files) delete(sub);
            }
            FileUtils.deleteQuietly(file);
        }
    }

    static File getRecent(File[] files, int which, Storage.FileType fileType) {
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

    static class HashedPrefixFileFilter implements FileFilter {
        private String ending;

        HashedPrefixFileFilter(FileType fileType, String prefix) {
            this.ending = Hasher.SEPARATOR + fileType.getName(prefix);
        }

        @Override
        public boolean accept(File file) {
            String name = file.getName();
            return file.isFile() && name.endsWith(ending);
        }
    }

    static class NameFileFilter implements FileFilter {
        private String name;

        NameFileFilter(String name) {
            this.name = name;
        }

        @Override
        public boolean accept(File file) {
            return file.isFile() && Hasher.extractFileName(file).equals(name);
        }
    }

    static class LastModifiedComparator implements Comparator<File> {

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

    static final FileFilter FILE_FILTER = new FileFilter() {
        @Override
        public boolean accept(File file) {
            return file.isFile();
        }
    };

    static final FileFilter ATTIC_FILTER = new FileFilter() {
        @Override
        public boolean accept(File file) {
            return file.isDirectory() && file.getName().matches("\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}");
        }
    };

}
