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

import eu.delving.metadata.MediaIndex;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;

import static junit.framework.Assert.assertEquals;

/**
 * check the reading and matching of media files
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestMediaIndex {

    @Test
    public void read() throws IOException {
        URL resource = MediaIndex.class.getResource("/aff/media-index.xml");
        MediaIndex mediaIndex = MediaIndex.read(resource.openStream());
//        mediaFiles.printMatchTree();
        String fileName = mediaIndex.match("D:\\whatever\\we\\encounter\\16513-01.tif");
        assertEquals("mismatch", "3FDD9FF7D732B16C9F2DE2DBB19D5975.tif", fileName);
        fileName = mediaIndex.match("16513-01");
        assertEquals("mismatch", "3FDD9FF7D732B16C9F2DE2DBB19D5975.tif", fileName);
    }
}
