/*
 * Copyright 2011, 2012 Delving BV
 *
 * Licensed under the EUPL, Version 1.0 or as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the
 * Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.delving.plugin;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * XStream stuff for media-files.xml in the Media subdir of a dataset
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

@XStreamAlias("media-files")
public class MediaFiles {

    public static final String SPLIT_REGEX = "[\\/:;\\\\]";

    public static void write(MediaFiles mediaFiles, File indexFile) throws IOException {
        XStream stream = new XStream(new PureJavaReflectionProvider());
        stream.processAnnotations(MediaFiles.class);
        FileOutputStream out = new FileOutputStream(indexFile);
        stream.toXML(mediaFiles, out);
        out.close();
    }

    public static MediaFiles read(InputStream inputStream) {
        XStream stream = new XStream(new PureJavaReflectionProvider());
        stream.processAnnotations(MediaFiles.class);
        MediaFiles mediaFiles = (MediaFiles) stream.fromXML(inputStream);
        mediaFiles.resolve();
        return mediaFiles;
    }

    public static MediaFiles create() {
        MediaFiles mediaFiles = new MediaFiles();
        mediaFiles.resolve();
        return mediaFiles;
    }

    @XStreamAsAttribute
    public Date date;
    @XStreamImplicit
    public List<MediaFile> mediaFiles;

    @XStreamOmitField
    private Map<String, MediaFile> quickHash;

    @XStreamOmitField
    private Set<String> fileNameSet;

    @XStreamOmitField
    private Map<String, MediaFile> uniqueParts;

    public String match(String fieldContent) {
        for (String part : split(fieldContent)) {
            MediaFile mediaFile = uniqueParts.get(part);
            if (mediaFile == null) continue;
            return mediaFile.name;
        }
        return "";
    }

    public MediaFile getQuick(String quickHash) {
        return this.quickHash.get(quickHash);
    }

    public boolean contains(File file) {
        return fileNameSet.contains(file.getName());
    }

    public void removeExcess(Set<String> otherFileNames) {
        Set<String> excess = new HashSet<String>();
        excess.addAll(fileNameSet);
        excess.removeAll(otherFileNames);
        Iterator<MediaFile> walk = mediaFiles.iterator();
        while (walk.hasNext()) if (excess.contains(walk.next().name)) walk.remove();
    }

    public void add(File sourceFile, File hashedFile, String quickHash) throws IOException {
        if (mediaFiles == null) mediaFiles = new ArrayList<MediaFile>();
        mediaFiles.add(new MediaFile(sourceFile, hashedFile, quickHash));
    }

    private void resolve() {
        date = new Date();
        quickHash = new HashMap<String, MediaFile>();
        uniqueParts = new HashMap<String, MediaFile>();
        fileNameSet = new HashSet<String>();
        Set<String> repeatedParts = new HashSet<String>();
        if (mediaFiles == null) mediaFiles = new ArrayList<MediaFile>();
        for (MediaFile file : mediaFiles) {
            quickHash.put(file.quickHash, file);
            fileNameSet.add(file.name);
            for (String keyword : file.getKeywords()) {
                if (repeatedParts.contains(keyword)) continue;
                if (uniqueParts.containsKey(keyword)) {
                    uniqueParts.remove(keyword);
                    repeatedParts.add(keyword);
                }
                else {
                    uniqueParts.put(keyword, file);
                }
            }
        }
    }

    @XStreamAlias("file")
    public static class MediaFile {
        @XStreamAsAttribute
        public String quickHash;

        @XStreamAsAttribute
        public String name;

        public String path;

        public MediaFile() {
        }

        public MediaFile(File sourceFile, File hashedFile, String quickHash) {
            this.path = sourceFile.getAbsolutePath();
            this.name = hashedFile.getName();
            this.quickHash = quickHash;
        }

        public boolean matchesSourceFile(File file) {
            return path.equals(file.getAbsolutePath());
        }

        public List<String> getKeywords() {
            return split(path);
        }
    }

    public static class Counter {
        String keyword;
        int count;

        private Counter(String keyword) {
            this.keyword = keyword;
        }
    }

    private static List<String> split(String value) {
        List<String> parts = new ArrayList<String>();
        for (String part : value.split(SPLIT_REGEX)) {
            part = part.trim().toLowerCase();
            if (part.isEmpty()) continue;
            parts.add(part);
        }
        return parts;
    }
}
