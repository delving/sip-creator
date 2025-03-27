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

package eu.delving.sip.base;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static eu.delving.XStreamFactory.getStreamFor;

/**
 * XStream stuff for media-files.xml in the Media subdir of a dataset
 *
 *
 */

@XStreamAlias("media-files")
public class MediaFiles {

    public static void write(MediaFiles mediaFiles, File indexFile) throws IOException {
        FileOutputStream out = new FileOutputStream(indexFile);
        getStreamFor(MediaFiles.class).toXML(mediaFiles, out);
        out.close();
    }

    public static MediaFiles read(InputStream inputStream) {
        MediaFiles mediaFiles = (MediaFiles) getStreamFor(MediaFiles.class).fromXML(inputStream);
        mediaFiles.resolve();
        return mediaFiles;
    }

    @XStreamAsAttribute
    public Date date;
    @XStreamImplicit
    public List<MediaFile> mediaFiles;

    @XStreamOmitField
    private Map<String, MediaFile> quickMap;

    @XStreamOmitField
    private Set<String> fileNames;

    public MediaFile getQuick(String quickHash) {
        return quickMap.get(quickHash);
    }

    public boolean contains(File file) {
        return fileNames.contains(file.getName());
    }

    public void removeExcess(Set<String> otherFileNames) {
        Set<String> excess = new HashSet<String>();
        excess.addAll(fileNames);
        excess.removeAll(otherFileNames);
        Iterator<MediaFile> walk = mediaFiles.iterator();
        while (walk.hasNext()) {
            if (excess.contains(walk.next().name)) {
                walk.remove();
            }
        }
    }

    public void add(File sourceFile, File hashedFile, String quickHash) throws IOException {
        if (mediaFiles == null) mediaFiles = new ArrayList<MediaFile>();
        mediaFiles.add(new MediaFile(sourceFile, hashedFile, quickHash));
    }

    private void resolve() {
        date = new Date();
        quickMap = new HashMap<String, MediaFile>();
        fileNames = new HashSet<String>();
        for (MediaFile file : mediaFiles) {
            quickMap.put(file.quickHash, file);
            fileNames.add(file.name);
        }
    }

    public void purge() {
        Map<String, Counter> counts = new HashMap<String, Counter>();
        for (MediaFile mediaFile : mediaFiles) {
            for (String keyword : mediaFile.keywords) {
                Counter counter = counts.get(keyword);
                if (counter == null) counts.put(keyword, counter = new Counter(keyword));
                counter.count++;
            }
        }
        for (Counter counter : counts.values()) {
            if (counter.count == mediaFiles.size()) {
                for (MediaFile mediaFile : mediaFiles) {
                    mediaFile.keywords.remove(counter.keyword);
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

        public List<String> keywords;

        public MediaFile() {
        }

        public MediaFile(File sourceFile, File hashedFile, String quickHash) {
            this.path = sourceFile.getAbsolutePath();
            this.name = hashedFile.getName();
            this.quickHash = quickHash;
            this.keywords = new ArrayList<String>();
            for (String part : path.split("[\\/:;]")) {
                part = part.trim().toLowerCase();
                if (part.isEmpty()) continue;
                keywords.add(part);
            }
        }

        public boolean matchesSourceFile(File file) {
            return path.equals(file.getAbsolutePath());
        }
    }

    public static class Counter {
        String keyword;
        int count;

        private Counter(String keyword) {
            this.keyword = keyword;
        }
    }
}
