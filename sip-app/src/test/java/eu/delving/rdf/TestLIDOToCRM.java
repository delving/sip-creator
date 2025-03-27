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

package eu.delving.rdf;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.URL;
import java.util.List;

/**
 *
 */

public class TestLIDOToCRM {

    @Test
    public void lidoToCRM() throws IOException, ParserConfigurationException, SAXException {
        URL mappingFile = getClass().getResource("/rdf/lido-to-crm.xml");
        MapToCRM.Mappings mappings = MapToCRM.readMappings(mappingFile.openStream());
        String xml = MapToCRM.toString(mappings);
        String[] fresh = xml.split("\n");
        List<String> original = IOUtils.readLines(mappingFile.openStream());
        int index = 0;
        for (String originalLine : original) {
            originalLine = originalLine.trim();
            String freshLine = fresh[index].trim();
            Assert.assertEquals("Line " + index, originalLine, freshLine);
            index++;
        }
    }

}
