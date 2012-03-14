/*
 * Copyright 2011 DELVING BV
 *
 * Licensed under the EUPL, Version 1.0 or? as soon they
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

package eu.delving.test;

import eu.delving.groovy.MappingException;
import eu.delving.sip.MappingEngine;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A unit test of the mapping engine
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestMappingEngine {

    MappingEngine mappingEngine;

    @Before
    public void createMappingEngine() throws IOException {
        Map<String, String> namespaces = new HashMap<String, String>();
        String mapping = IOUtils.toString(getClass().getResourceAsStream("/engine/test-mapping.xml"), "UTF-8");
//        mappingEngine = new MappingEngine(mapping, namespaces, getClass().getClassLoader(), recDefModel, validator);
    }

    @Test
    public void flatMapping() throws IOException, SAXException, MappingException {
        String inputRecord = IOUtils.toString(getClass().getResourceAsStream("/engine/test-input.xml" ), "UTF-8");
        String outputRecord = IOUtils.toString(getClass().getResourceAsStream("/engine/test-output.txt" ), "UTF-8");
        System.out.println(inputRecord);
        System.out.println(outputRecord);
//        IndexDocument doc = mappingEngine.executeMapping(inputRecord);
//        Assert.assertEquals(outputRecord, doc.toString());
    }
}
