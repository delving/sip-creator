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

package eu.delving.sip;

import junit.framework.Assert;
import org.junit.Test;

import static eu.delving.sip.files.LinkChecker.Entry;
import static eu.delving.sip.files.LinkChecker.LinkCheck;

/**
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestLinkChecker {
    @Test
    public void formatTest()  {
        LinkCheck c = new LinkCheck();
        c.httpStatus = 1;
        c.time = 200000000000L;
        c.fileSize = -1;
        c.mimeType = "file/text";
        String url = "http://whatever";
        Entry before = new Entry(url, c);
        String line = before.toLine();
        Entry after = new Entry(line);
        Assert.assertEquals("CSV Format", line, after.toLine());
    }
}
