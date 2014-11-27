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

import eu.delving.metadata.RecDef;
import eu.delving.sip.files.LinkCheck;
import eu.delving.sip.files.LinkFile;
import junit.framework.Assert;
import org.junit.Test;

/**
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestLinkFile {
    @Test
    public void formatTest()  {
        LinkCheck c = new LinkCheck();
        c.check = RecDef.Check.LANDING_PAGE;
        c.spec = "spek";
        c.localId = "here";
        c.httpStatus = 1;
        c.time = 200000000000L;
        c.fileSize = -1;
        c.mimeType = "file/text";
        c.ok = true;
        String url = "http://whatever";
        LinkFile.Entry before = new LinkFile.Entry(url, c);
        String line = before.toLine();
        LinkFile.Entry after = new LinkFile.Entry(line);
        Assert.assertEquals("CSV Format", line, after.toLine());
    }
}
