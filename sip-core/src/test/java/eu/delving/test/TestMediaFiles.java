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

package eu.delving.test;

import eu.delving.plugin.MediaFiles;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;

import static junit.framework.Assert.assertEquals;

/**
 * check the reading and matching of media files
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestMediaFiles {

    @Test
    public void read() throws IOException {
        URL resource = MediaFiles.class.getResource("/aff/media-files.xml");
        MediaFiles mediaFiles = MediaFiles.read(resource.openStream());
//        mediaFiles.printMatchTree();
        String fileName = mediaFiles.match("D:\\whatever\\we\\encounter\\16513-01.tif");
        assertEquals("mismatch", "3FDD9FF7D732B16C9F2DE2DBB19D5975.tif", fileName);
    }
}
