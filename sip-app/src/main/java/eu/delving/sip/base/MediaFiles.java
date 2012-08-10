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

package eu.delving.sip.base;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamImplicit;

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

    public static void write(MediaFiles mediaFiles, File indexFile) throws IOException {
        XStream stream = new XStream();
        stream.processAnnotations(MediaFiles.class);
        FileOutputStream out = new FileOutputStream(indexFile);
        stream.toXML(mediaFiles, out);
        out.close();
    }

    public static MediaFiles read(InputStream inputStream) {
        XStream stream = new XStream();
        stream.processAnnotations(MediaFiles.class);
        return (MediaFiles) stream.fromXML(inputStream);
    }

    @XStreamAsAttribute
    public Date date;
    @XStreamImplicit
    public List<MediaFile> mediaFiles;

    public MediaFiles() {
        this.date = new Date();
    }

    public void add(File originalFile, File hashedFile) throws IOException {
        if (mediaFiles == null) mediaFiles = new ArrayList<MediaFile>();
        mediaFiles.add(new MediaFile(originalFile.getAbsolutePath(), hashedFile.getName()));
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
        public String name;

        public String path;

        public List<String> keywords;

        public MediaFile() {
        }

        public MediaFile(String path, String name) {
            this.path = path;
            this.name = name;
            this.keywords = new ArrayList<String>();
            for (String part : path.split("[\\/:;]")) {
                part = part.trim().toLowerCase();
                if (part.isEmpty()) continue;
                keywords.add(part);
            }
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
