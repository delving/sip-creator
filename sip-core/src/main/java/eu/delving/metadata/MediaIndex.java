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

package eu.delving.metadata;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import org.apache.commons.lang.StringUtils;

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

@XStreamAlias("media-index")
public class MediaIndex {

    public static final String IGNORE_CHARACTERS = "/:;\\";

    public static void write(MediaIndex mediaIndex, File indexFile) throws IOException {
        mediaIndex.resolve();
        XStream stream = new XStream(new PureJavaReflectionProvider());
        stream.processAnnotations(MediaIndex.class);
        FileOutputStream out = new FileOutputStream(indexFile);
        stream.toXML(mediaIndex, out);
        out.close();
    }

    public static MediaIndex read(InputStream inputStream) {
        XStream stream = new XStream(new PureJavaReflectionProvider());
        stream.processAnnotations(MediaIndex.class);
        MediaIndex mediaIndex = (MediaIndex) stream.fromXML(inputStream);
        mediaIndex.resolve();
        return mediaIndex;
    }

    public static MediaIndex create() {
        MediaIndex mediaIndex = new MediaIndex();
        mediaIndex.resolve();
        return mediaIndex;
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
    private Map<MatchTreeKey, MatchTreeValue> matchTree;

    public String match(String fieldContent) {
        MatchTreeKey key = new MatchTreeKey(createKeyFrom(fieldContent));
        MatchTreeValue value = matchTree.get(key);
        if (value != null) {
            MediaFile file = value.get(key.getChild());
            if (file != null) return file.name;
        }
        if (matchTree.size() == 1) {
            MediaFile file = matchTree.values().iterator().next().get(key.getBastard());
            if (file != null) return file.name;
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
        fileNameSet = new HashSet<String>();
        matchTree = new TreeMap<MatchTreeKey, MatchTreeValue>();
        if (mediaFiles == null) mediaFiles = new ArrayList<MediaFile>();
        for (MediaFile file : mediaFiles) {
            quickHash.put(file.quickHash, file);
            fileNameSet.add(file.name);
            put(file);
        }
        for (MatchTreeValue value : matchTree.values()) value.purgeKey();
    }

    @XStreamAlias("file")
    public static class MediaFile {
        @XStreamAsAttribute
        public String quickHash;

        @XStreamAsAttribute
        public String name;

        public String path;

        public String key;

        public MediaFile() {
        }

        public MediaFile(File sourceFile, File hashedFile, String quickHash) {
            this.path = sourceFile.getAbsolutePath();
            this.key = createKeyFrom(this.path);
            this.name = hashedFile.getName();
            this.quickHash = quickHash;
        }

        public boolean matchesSourceFile(File file) {
            return path.equals(file.getAbsolutePath());
        }
    }

    private static class MatchTreeKey implements Comparable<MatchTreeKey> {
        private String key;
        private int index;

        private MatchTreeKey(String key) {
            this.key = key;
        }

        private MatchTreeKey getChild() {
            MatchTreeKey childKey = new MatchTreeKey(key);
            childKey.index = index + 1;
            return childKey;
        }

        private MatchTreeKey getBastard() {
            MatchTreeKey childKey = new MatchTreeKey("?" + key);
            childKey.index = index + 1;
            return childKey;
        }

        public String toString() {
            return key.substring(0, index + 1);
        }

        @Override
        public int compareTo(MatchTreeKey other) {
            if (index != other.index) throw new RuntimeException("MatchTreeKey indexes do not match");
            return key.charAt(index) - other.key.charAt(index);
        }
    }

    private static class MatchTreeValue {
        private Map<MatchTreeKey, MatchTreeValue> matchTree;
        private MatchTreeKey matchTreeKey;
        private MediaFile mediaFile;

        private MatchTreeValue(MatchTreeKey matchTreeKey, MediaFile value) {
            this.matchTreeKey = matchTreeKey;
            this.mediaFile = value;
        }

        public void put(MatchTreeKey key, MediaFile value) {
            if (mediaFile != null) {
                matchTree = new TreeMap<MatchTreeKey, MatchTreeValue>();
                MatchTreeKey childKey = matchTreeKey.getChild();
                matchTree.put(childKey, new MatchTreeValue(childKey, mediaFile));
                mediaFile = null;
            }
            MatchTreeKey childKey = key.getChild();
            MatchTreeValue childValue = matchTree.get(childKey);
            if (childValue == null) {
                matchTree.put(childKey, new MatchTreeValue(childKey, value));
            }
            else {
                childValue.put(childKey, value);
            }
        }

        public void purgeKey() {
            if (mediaFile != null) {
                mediaFile.key = mediaFile.key.substring(0, matchTreeKey.index + 2);
            }
            else for (MatchTreeValue value : matchTree.values()) {
                value.purgeKey();
            }
        }

        public String toString() {
            if (mediaFile != null) {
                return matchTreeKey + ": " + mediaFile.key;
            }
            else {
                return matchTreeKey.toString();
            }
        }

        public MediaFile get(MatchTreeKey key) {
            if (mediaFile != null) return mediaFile;
            MatchTreeValue value = matchTree.get(key);
            if (value == null) {
                if (matchTree.size() == 1) {
                    MediaFile file = matchTree.values().iterator().next().get(key.getBastard());
                    if (file != null) return file;
                }
                return null;
            }
            return value.get(key.getChild());
        }
    }

    public void printMatchTree() {
        printMatchTree(matchTree);
    }

    private void printMatchTree(Map<MatchTreeKey, MatchTreeValue> map) {
        for (MatchTreeValue value : map.values()) {
            int indent = value.matchTreeKey.index;
            while (indent-- > 0) System.out.print("   ");
            System.out.println(value);
            if (value.matchTree != null) printMatchTree(value.matchTree);
        }
    }

    private void put(MediaFile mediaFile) {
        MatchTreeKey key = new MatchTreeKey(mediaFile.key);
        MatchTreeValue value = matchTree.get(key);
        if (value == null) {
            matchTree.put(key, new MatchTreeValue(key, mediaFile));
        }
        else {
            value.put(key, mediaFile);
        }
    }

    private static String createKeyFrom(String path) {
        StringBuilder keyBuilder = new StringBuilder(path.length());
        for (char c : StringUtils.reverse(path).toCharArray()) {
            if (IGNORE_CHARACTERS.indexOf(c) >= 0) continue;
            keyBuilder.append(Character.toUpperCase(c));
        }
        return keyBuilder.toString();
    }
}
