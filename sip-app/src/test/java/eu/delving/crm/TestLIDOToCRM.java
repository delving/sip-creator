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

package eu.delving.crm;

import eu.delving.XMLToolFactory;
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
import java.util.Map;

/**
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestLIDOToCRM {
    private Logger log = Logger.getLogger(getClass());

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
        URL lidoFile = getClass().getResource("/rdf/LIDOExample.xml");
        Document lidoDoc = XMLToolFactory.documentBuilderFactory().newDocumentBuilder().parse(lidoFile.openStream());
//        String lidoXML = new XmlSerializer().toXml(lidoDoc.getDocumentElement(), false);
//        System.out.println(lidoXML);
        Graph graph = mappings.toGraph(lidoDoc.getDocumentElement(), new FunctionExecutor());
        System.out.println(graph);
    }

    private class FunctionExecutor implements MapToCRM.Func {

        @Override
        public String execute(String name, Map<String, String> argMap) {
            StringBuilder out = new StringBuilder(name);
            out.append("(");
            int left = argMap.size();
            for (Map.Entry<String, String> entry : argMap.entrySet()) {
                out.append(entry.getKey()).append("=\"").append(entry.getValue()).append("\"");
                if (--left > 0) out.append(", ");
            }
            out.append(")");
            log.info(out.toString());
            return "URI";
        }
    }
}
