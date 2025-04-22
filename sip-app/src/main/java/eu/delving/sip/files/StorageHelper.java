/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.sip.files;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static eu.delving.sip.files.Storage.FileType;
import static eu.delving.sip.files.Storage.FileType.*;
import static eu.delving.sip.files.Storage.MAX_UNIQUE_VALUE_LENGTH;
import static eu.delving.sip.files.Storage.UNIQUE_VALUE_CONVERTER;

/**
 * This class contains helpers for the StorageImpl to lean on. It does all of
 * the searching for file name
 * patterns that the storage system needs, as well as maintaining the previous
 * versions of various
 * types of files.
 *
 *
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
                if (line.startsWith("#"))
                    continue;
                int equals = line.indexOf("=");
                if (equals < 0) {
                    continue;
                }
                String name = line.substring(0, equals).trim().replace("\uFEFF", "");
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

    static Map<String, String> readFactsJson(File file) throws IOException {
        if (file.exists()) {
            ObjectMapper jsonMapper = new ObjectMapper();
            JsonNode baseNode = jsonMapper.readTree(file);
            if (baseNode != null && baseNode.isObject()) {
                JsonNode factsNode = baseNode.get("facts");
                if (factsNode != null && factsNode.isObject()) {
                    Map<String, String> facts = new TreeMap<>();
                    for (Iterator<Map.Entry<String, JsonNode>> it = factsNode.fields(); it.hasNext(); ) {
                        Map.Entry<String, JsonNode> entry = it.next();
                        facts.put(entry.getKey(), entry.getValue().textValue());
                    }
                    return facts;
                }
            }
        }
        return null;
    }

    static void writeFactsJson(File file, Map<String, String> facts) throws IOException {
        ObjectMapper jsonMapper = new ObjectMapper();
        ObjectNode baseNode = jsonMapper.createObjectNode();
        ObjectNode factsNode = baseNode.putObject("facts");
        for (String key : facts.keySet()) {
            factsNode.put(key, facts.get(key));
        }
        jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);
        jsonMapper.writeValue(file, baseNode);
    }

    static File factsFile(File dir) {
        return findOrCreate(dir, FACTS);
    }

    static File factsJsonFile(File dir) {
        return findOrCreate(dir, FileType.FACTS_JSON);
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

    static File mappingFile(File dir, String prefix) {
        //return findOrNull(dir, 0, new PrefixFileFilter(MAPPING), MAPPING);
        return new File(dir, MAPPING.getName(prefix));
    }

    static File targetFile(File dir, Map<String, String> facts, String prefix) {
        String fileName = String.format("%s__%s.xml", facts.get("spec"), prefix);
        return new File(dir, fileName);
    }

    static InputStream zipIn(File file) throws StorageException {
        try {
            if (file.getName().endsWith((".zst"))) {
                return new ZstdInputStream(new FileInputStream(file));
            } else {
                return new GZIPInputStream(new FileInputStream(file));
            }
        } catch (IOException e) {
            throw new StorageException(String.format("Unable to create input stream from %s", file.getAbsolutePath()),
                    e);
        }
    }

    public static OutputStream zipOut(File file) throws StorageException {
        try {
            if (file.getName().endsWith((".zst"))) {
                return new ZstdOutputStream(new FileOutputStream(file));
            } else {
                return new GZIPOutputStream(new FileOutputStream(file));
            }
        } catch (IOException e) {
            throw new StorageException(String.format("Unable to create output stream from %s", file.getAbsolutePath()),
                    e);
        }
    }

    static File reportJsonFile(File dir, String prefix) {
        return findOrNull(dir, 0, new NameFileFilter(FileType.REPORT_JSON.getName(prefix)), FileType.REPORT_JSON);
    }

    static File reportFile(File dir, String prefix) {
        return new File(dir, REPORT.getName(prefix));
    }

    static File reportIndexFile(File dir, String prefix) {
        return new File(dir, REPORT_INDEX.getName(prefix));
    }

    static File reportConclusionFile(File dir, String prefix) {
        return new File(dir, REPORT_CONCLUSION.getName(prefix));
    }

    public static File statsFile(File dir) {
        File sourceFile = sourceFile(dir);
        if (sourceFile != null) {
            String hash = Hasher.extractHash(sourceFile);
            if (hash != null) {
                return new File(dir, hash + Hasher.SEPARATOR + SOURCE_STATS_ZSTD.getName());
            }
        }
        return new File(dir, SOURCE_STATS.getName());
    }

    static File sipZip(File dir, String spec, String prefix) {
        String name = String.format("%s__%s__%s.sip.zip", spec, DATE_FORMAT.format(new Date()), prefix);
        return new File(dir, name);
    }

    public static String datasetNameFromSipZip(File file) {
        String n = file.getName();
        int uu = n.indexOf("__");
        if (uu < 0) {
            return n;
        }
        return n.substring(0, uu);
    }

    public static DateTime dateTimeFromSipZip(File file) {
        DateTime dateTime = dateTimeFromSipZip(file.getName());
        if (dateTime == null)
            dateTime = new DateTime(file.lastModified());
        return dateTime;
    }

    public static DateTime dateTimeFromSipZip(String n) {
        Matcher matcher = EXTRACT_DATE.matcher(n);
        if (matcher.matches()) {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));
            int hour = Integer.parseInt(matcher.group(4));
            int minute = Integer.parseInt(matcher.group(5));
            return new DateTime(year, month, day, hour, minute);
        } else {
            return null;
        }
    }

    static Pattern EXTRACT_DATE = Pattern.compile(".*(\\d{4})_(\\d{2})_(\\d{2})_(\\d{2})_(\\d{2}).*");

    static File findOrCreate(File directory, Storage.FileType fileType) {
        if (fileType.getName() == null)
            throw new RuntimeException("Expected name");
        File file = findOrNull(directory, 0, new NameFileFilter(fileType.getName()), fileType);
        if (file == null) {
            switch (fileType) {
                case SOURCE:
                    file = findOrNull(directory, 0, new NameFileFilter(SOURCE_ZSTD.getName()), SOURCE_ZSTD);
                    break;
            }
        }
        if (file == null)
            file = new File(directory, fileType.getName());
        return file;
    }

    static File findOrNull(File directory, int which, FileFilter fileFilter, Storage.FileType fileType) {
        File[] files = directory.listFiles(fileFilter);
        return getRecent(files, which, fileType);
    }

    static File findNonHashedPrefixFile(File dir, FileType fileType, String prefix) {
        // Return the file for the type and prefix which is not timestamped ("hashed")
        return new File(dir, fileType.getName(prefix));
    }

    static List<File> findHashedPrefixFiles(File dir, FileType fileType, String prefix) {
        File[] files = dir.listFiles(new HashedPrefixFileFilter(fileType, prefix));
        return Arrays.asList(getAllRecent(files, fileType));
    }

    static Collection<File> findSourceFiles(File dir) {
        File[] files = dir.listFiles(new NameFileFilter(SOURCE.getName()));
        return Arrays.asList(files);
    }

    static void delete(File file) {
        if (file.exists()) {
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                if (files != null)
                    for (File sub : files)
                        delete(sub);
            }
            FileUtils.deleteQuietly(file);
        }
    }

    static File getRecent(File[] files, int which, Storage.FileType fileType) {
        return getRecent(files, which, fileType.getHistorySize());
    }

    static File getRecent(File[] files, int which, int maxHistory) {
        if (files == null || files.length <= which || which > maxHistory) {
            return null;
        }
        File[] recent = getAllRecent(files, maxHistory);
        if (recent == null) {
            return null;
        }
        return which < files.length ? files[which] : null;
    }

    static File[] getAllRecent(File[] files, Storage.FileType fileType) {
        return getAllRecent(files, fileType.getHistorySize());
    }

    static File[] getAllRecent(File[] files, int maxHistory) {
        Arrays.sort(files, new LastModifiedComparator());
        if (files.length > maxHistory) {
            for (int walk = maxHistory; walk < files.length; walk++) {
                files[walk].delete();
            }
        }
        return files;
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

    static class HashedNameFileFilter implements FileFilter {
        private String ending;

        HashedNameFileFilter(String name) {
            this.ending = Hasher.SEPARATOR + name;
        }

        @Override
        public boolean accept(File file) {
            String name = file.getName();
            return file.isFile() && name.endsWith(ending);
        }
    }

    static class LastModifiedComparator implements Comparator<File> {

        @Override
        public int compare(File a, File b) {
            long lastA = a.lastModified();
            long lastB = b.lastModified();
            if (lastA > lastB) {
                return -1;
            } else if (lastA < lastB) {
                return 1;
            } else { // lastModified is only accurate to seconds (ends in 000) and we want the
                     // unhashed one at the top
                int nameA = a.getName().length();
                int nameB = b.getName().length();
                if (nameA > nameB) {
                    return 1;
                } else if (nameB < nameA) {
                    return -1;
                } else {
                    return 0;
                }
            }
        }
    }

}
