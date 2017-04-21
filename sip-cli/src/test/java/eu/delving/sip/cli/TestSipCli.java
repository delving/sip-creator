package eu.delving.sip.cli;


import eu.delving.groovy.MappingException;
import eu.delving.metadata.RecDefTree;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamException;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;


public class TestSipCli {


        @Test
        public void getRecDefTree() throws FileNotFoundException {
            String recDefPath = getClass().getResource("/edm_5.2.6_record-definition.xml").getPath();
            RecDefTree recDefTree = Main.getRecDefTree(recDefPath);
            assertNotNull(recDefTree);
        }

        @Test
        public void runMapping() throws FileNotFoundException, SAXException, UnsupportedEncodingException, MappingException, XMLStreamException {
            String sourceRecord = " <record id=\"87\">\n" +
                "      <achternaam>Schraven</achternaam>\n" +
                "      <voornaam1>Hans</voornaam1>\n" +
                "      <geb>1956</geb>\n" +
                "      <gest>1999</gest>\n" +
                "      <titel>Face Eighty Seven</titel>\n" +
                "      <jaarvanvervaardiging>2009</jaarvanvervaardiging>\n" +
                "      <techniek>potlood, kleurpotlood</techniek>\n" +
                "      <inventarisnummer>87</inventarisnummer>\n" +
                "      <in>87</in>\n" +
                "      <pictureurl>http://www.ietsheelanders.nl/wp-content/uploads/2010/09/faceeightyseven.jpg</pictureurl>\n" +
                "    </record>\n";
            String outputRecord = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                "<edm:RDF xmlns:edm=\"http://www.europeana.eu/schemas/edm/\" xmlns:nave=\"http://schemas.delving.eu/nave/terms/\" xmlns:ore=\"http://www.openarchives.org/ore/terms/\" xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n" +
                "    <ore:Aggregation rdf:about=\"http://data.collectienederland.nl/resource/aggregation/coll-schraven/87\">\n" +
                "        <edm:aggregatedCHO rdf:resource=\"http://data.collectienederland.nl/resource/document/coll-schraven/87\"/>\n" +
                "        <edm:dataProvider>Collectie Schraven</edm:dataProvider>\n" +
                "        <edm:provider>Rijksdienst voor het Cultureel Erfgoed</edm:provider>\n" +
                "        <edm:rights>http://creativecommons.org/licenses/by-nc/3.0/nl/</edm:rights>\n" +
                "    </ore:Aggregation>\n" +
                "    <edm:ProvidedCHO rdf:about=\"http://data.collectienederland.nl/resource/document/coll-schraven/87\">\n" +
                "        <edm:type>IMAGE</edm:type>\n" +
                "    </edm:ProvidedCHO>\n" +
                "    <nave:DcnResource>\n" +
                "        <nave:province>Noord-Holland</nave:province>\n" +
                "    </nave:DcnResource>\n" +
                "</edm:RDF>\n";
            String recDefPath = getClass().getResource("/coll-schraven/edm_5.2.6_record-definition.xml").getPath();
            String xsdPath = getClass().getResource("/coll-schraven/edm_5.2.6_validation.xsd").getPath();
            String mappingPath = getClass().getResource("/coll-schraven/mapping_edm.xml").getPath();
            String result = Main.processRecord(recDefPath, xsdPath, mappingPath, sourceRecord, "87");
            assertEquals(outputRecord, result);
        }
}
