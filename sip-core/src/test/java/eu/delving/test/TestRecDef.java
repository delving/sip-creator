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

import eu.delving.metadata.Path;
import eu.delving.metadata.RecDef;
import junit.framework.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.net.URL;

/**
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestRecDef {

    @Test
    public void fetchFieldType() {
        Assert.assertEquals("special value gumby not found", "gumby", recDef().getFieldType(Path.create("/test/sub-one/sub-two/abc-link")));
        Assert.assertEquals("default not returned", "text", recDef().getFieldType(Path.create("/test/sub-one/sub-two")));
        Assert.assertNull("null not returned for nonexistent path", recDef().getFieldType(Path.create("/testes/someCrazyValue")));
    }

    private static RecDef recDef() {
        try {
            URL url = TestCodeGeneration.class.getResource("/codegen/TestCodeGeneration-recdef.xml");
            InputStream inputStream = url.openStream();
            return RecDef.read(inputStream);
        }
        catch (Exception e) {
            throw new RuntimeException("Unable to load recdef", e);
        }
    }
}
