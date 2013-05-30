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

package eu.delving.rdf;

import junit.framework.Assert;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.URL;
import java.util.List;

/**
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestLIDOToCRM {

    @Test
    public void readForthMapping() throws IOException, ParserConfigurationException, SAXException {
        URL mappingFile = getClass().getResource("/rdf/lido-to-crm.xml");
        LIDO2CRM.Mappings mappings = LIDO2CRM.readForthMapping(mappingFile.openStream());
        String xml = LIDO2CRM.toString(mappings);
//        System.out.println(xml);

        String[] fresh = xml.split("\n");
        List<String> original = IOUtils.readLines(mappingFile.openStream());
        int index = 0;
        for (String originalLine : original) {
            originalLine = originalLine.trim();
            String freshLine = fresh[index].trim();
            Assert.assertEquals("Line " + index, originalLine, freshLine);
            index++;
        }

//        URL lidoFile = getClass().getResource("/rdf/LIDOExample.xml");
//        Document lidoDoc = XMLToolFactory.documentBuilderFactory().newDocumentBuilder().parse(lidoFile.openStream());
//        String lidoXML = new XmlSerializer().toXml(lidoDoc.getDocumentElement(), false);
//        System.out.println(lidoXML);
//        Graph graph = new Graph();
//        forthMapping.apply(graph, lidoDoc.getDocumentElement());
//        System.out.println(graph);
    }

}
