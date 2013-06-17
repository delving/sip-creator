/*
 * Copyright 2013x Delving BV
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

package eu.delving.crm;

import eu.delving.XMLToolFactory;
import eu.delving.groovy.XmlSerializer;
import junit.framework.Assert;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.URL;
import java.util.List;

/**
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestLIDOToCRM {
    private Logger log = Logger.getLogger(getClass());

    @Test
    public void lidoToCRM() throws IOException, ParserConfigurationException, SAXException {
        URL mappingFile = getClass().getResource("/crm/lido-to-crm.xml");
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
        URL lidoFile = getClass().getResource("/crm/LIDOExample.xml");
        Document lidoDoc = XMLToolFactory.documentBuilderFactory().newDocumentBuilder().parse(lidoFile.openStream());
        String lidoXML = new XmlSerializer().toXml(lidoDoc.getDocumentElement(), false);
//        log.info(lidoXML);
        Graph graph = mappings.toGraph(lidoDoc.getDocumentElement(), new FunctionExecutor());
        log.info(graph);
    }

}
