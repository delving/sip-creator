package eu.delving.metadata;

import eu.delving.groovy.*;
import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Node;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.Map;
import java.util.TreeMap;

public class RecDefTest {

    String localID = "enb-112.beeldmateriaal-957193e3-f3a9-6e4d-97ff-9f7390c41e86-005ff52b-4c5e-9a79-5ee9-50bcc65c8a77";

    String testRecord = " <pocket id=\"enb-112.beeldmateriaal-957193e3-f3a9-6e4d-97ff-9f7390c41e86-005ff52b-4c5e-9a79-5ee9-50bcc65c8a77\"> <record xmlns:oai_enb=\"http://www.openarchives.org/OAI/2.0/oai_enb/\" xmlns:dc=\"http://dublincore.org/documents/dcmi-namespace/\" xmlns:mmm=\"http://api.memorix-maior.nl/REST/3.0/\" xmlns:enb_dc=\"http://purl.org/enb_dc/elements/1.1/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                        "    <header>\n" +
                        "      <identifier>enb_112.beeldmateriaal:957193e3-f3a9-6e4d-97ff-9f7390c41e86:005ff52b-4c5e-9a79-5ee9-50bcc65c8a77</identifier>\n" +
                        "      <datestamp>2019-05-29T23:38:02Z</datestamp>\n" +
                        "    </header>\n" +
                        "    <metadata>\n" +
                        "      <oai_enb:dc xsi:schemaLocation=\"http://www.openarchives.org/OAI/2.0/oai_dc/          http://www.openarchives.org/OAI/2.0/oai_dc.xsd\">\n" +
                        "        <enb_dc:set_title>Beeldmateriaal</enb_dc:set_title>\n" +
                        "        <enb_dc:tenant>enb_112</enb_dc:tenant>\n" +
                        "        <enb_dc:tenantname>Stichting Heemkundekring 'Willem van Strijen'</enb_dc:tenantname>\n" +
                        "        <enb_dc:col_entiteit>957193e3-f3a9-6e4d-97ff-9f7390c41e86</enb_dc:col_entiteit>\n" +
                        "        <enb_dc:entity_uuid>005ff52b-4c5e-9a79-5ee9-50bcc65c8a77</enb_dc:entity_uuid>\n" +
                        "        <enb_dc:dcterms_identifier>BM300081</enb_dc:dcterms_identifier>\n" +
                        "        <enb_dc:spectrum_collection_name></enb_dc:spectrum_collection_name>\n" +
                        "        <enb_dc:dcterms_type>Foto</enb_dc:dcterms_type>\n" +
                        "        <enb_dc:dcterms_title>Johan en Diny Goverde</enb_dc:dcterms_title>\n" +
                        "        <enb_dc:dcterms_description>&lt;p&gt;Johan en Diny Goverde in de kapsalon van hun vader/moeder Driek Goverde en Cornelia Jacobs. Johan heeft de kapsalon in 1938 overgenonem van zijn vader. Diny beheerde de toiletartikelen en parfumeriëen in de winkel naast de kapsalon.&lt;/p&gt;</enb_dc:dcterms_description>\n" +
                        "        <enb_dc:dcterms_subject></enb_dc:dcterms_subject>\n" +
                        "        <enb_dc:dcterms_medium></enb_dc:dcterms_medium>\n" +
                        "        <enb_dc:internal_notes1>A1.003.Gov.007</enb_dc:internal_notes1>\n" +
                        "        <enb_dc:subcollectienaam></enb_dc:subcollectienaam>\n" +
                        "        <enb_dc:geos>\n" +
                        "          <enb_dc:geo>\n" +
                        "            <enb_dc:straatnaam>Langenoordstraat</enb_dc:straatnaam>\n" +
                        "            <enb_dc:nummer>66</enb_dc:nummer>\n" +
                        "            <enb_dc:geonameid>2743947</enb_dc:geonameid>\n" +
                        "            <enb_dc:name>Zevenbergen</enb_dc:name>\n" +
                        "            <enb_dc:country_name>Nederland</enb_dc:country_name>\n" +
                        "            <enb_dc:country_code>NL</enb_dc:country_code>\n" +
                        "            <enb_dc:admin1_name>Noord-Brabant</enb_dc:admin1_name>\n" +
                        "            <enb_dc:admin2_name>Gemeente Moerdijk</enb_dc:admin2_name>\n" +
                        "          </enb_dc:geo>\n" +
                        "        </enb_dc:geos>\n" +
                        "      </oai_enb:dc>\n" +
                        "    </metadata>\n" +
                        "    <about>\n" +
                        "      <mmm:memorix xsi:schemaLocation=\"http://api.memorix-maior.nl/REST/3.0/ http://api.memorix-maior.nl/REST/3.0/MRX-API-ANY.xsd\">\n" +
                        "        <image>\n" +
                        "          <thumbnail_small>http://images.memorix.nl/enb_112/thumb/220x220/d582440d-731a-5578-0517-e1a70faa88c1.jpg</thumbnail_small>\n" +
                        "          <thumbnail_large>http://images.memorix.nl/enb_112/thumb/500x500/d582440d-731a-5578-0517-e1a70faa88c1.jpg</thumbnail_large>\n" +
                        "          <mimetype>image/jpeg</mimetype>\n" +
                        "          <filename>BM300081.jpg</filename>\n" +
                        "          <dzi>http://images.memorix.nl/enb_112/deepzoom/d582440d-731a-5578-0517-e1a70faa88c1.dzi</dzi>\n" +
                        "        </image>\n" +
                        "        <url>http://api.memorix-maior.nl/collectiebeheer/oai-pmh?verb=GetRecord&amp;metadataPrefix=oai_enb&amp;tenant=enb&amp;key=30afd54c-8a05-11e5-a67e-00155d012a81&amp;identifier=enb_112.beeldmateriaal:957193e3-f3a9-6e4d-97ff-9f7390c41e86:005ff52b-4c5e-9a79-5ee9-50bcc65c8a77</url>\n" +
                        "      </mmm:memorix>\n" +
                        "    </about>\n" +
                        "  </record>\n</pocket>";


    String expected = "<?xml version='1.0' encoding='UTF-8'?>\n" +
        "<edm:RDF xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" xmlns:ebucore=\"http://www.ebu.ch/metadata/ontologies/ebucore/ebucore#\" xmlns:edm=\"http://www.europeana.eu/schemas/edm/\" xmlns:nave=\"http://schemas.delving.eu/nave/terms/\" xmlns:ore=\"http://www.openarchives.org/ore/terms/\" xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" xmlns:skos=\"http://www.w3.org/2004/02/skos/core#\">\n" +
        "    <ore:Aggregation rdf:about=\"http://data.brabantcloud.nl/resource/aggregation/enb-112-beeldmateriaal/enb-112.beeldmateriaal-957193e3-f3a9-6e4d-97ff-9f7390c41e86-005ff52b-4c5e-9a79-5ee9-50bcc65c8a77\">\n" +
        "        <edm:aggregatedCHO rdf:resource=\"http://data.brabantcloud.nl/resource/document/enb-112-beeldmateriaal/enb-112.beeldmateriaal-957193e3-f3a9-6e4d-97ff-9f7390c41e86-005ff52b-4c5e-9a79-5ee9-50bcc65c8a77\"/>\n" +
        "        <edm:dataProvider>Stichting Heemkundekring 'Willem van Strijen'</edm:dataProvider>\n" +
        "        <edm:hasView rdf:resource=\"https://images.memorix.nl/enb_112/thumb/500x500/d582440d-731a-5578-0517-e1a70faa88c1.jpg\"/>\n" +
        "        <edm:isShownAt rdf:resource=\"http://heemkundezevenbergen.nl/databanken/index.html?diw-id=brabantcloud_enb-112-beeldmateriaal_enb-112.beeldmateriaal-957193e3-f3a9-6e4d-97ff-9f7390c41e86-005ff52b-4c5e-9a79-5ee9-50bcc65c8a77\"/>\n" +
        "        <edm:isShownBy rdf:resource=\"https://images.memorix.nl/enb_112/thumb/500x500/d582440d-731a-5578-0517-e1a70faa88c1.jpg\"/>\n" +
        "        <edm:object rdf:resource=\"https://images.memorix.nl/enb_112/thumb/220x220/d582440d-731a-5578-0517-e1a70faa88c1.jpg\"/>\n" +
        "        <edm:provider>Brabant Cloud</edm:provider>\n" +
        "        <edm:rights rdf:resource=\"https://rightsstatements.org/vocab/InC/1.0/\"/>\n" +
        "    </ore:Aggregation>\n" +
        "    <edm:ProvidedCHO rdf:about=\"http://data.brabantcloud.nl/resource/document/enb-112-beeldmateriaal/enb-112.beeldmateriaal-957193e3-f3a9-6e4d-97ff-9f7390c41e86-005ff52b-4c5e-9a79-5ee9-50bcc65c8a77\">\n" +
        "        <dc:description>&lt;p>Johan en Diny Goverde in de kapsalon van hun vader/moeder Driek Goverde en Cornelia Jacobs. Johan heeft de kapsalon in 1938 overgenonem van zijn vader. Diny beheerde de toiletartikelen en parfumeriëen in de winkel naast de kapsalon.&lt;/p></dc:description>\n" +
        "        <dc:identifier>BM300081</dc:identifier>\n" +
        "        <dc:title>Johan en Diny Goverde</dc:title>\n" +
        "        <dc:type>Foto</dc:type>\n" +
        "        <dcterms:spatial rdf:resource=\"https://sws.geonames.org/2743947\"/>\n" +
        "        <edm:type>IMAGE</edm:type>\n" +
        "    </edm:ProvidedCHO>\n" +
        "    <edm:WebResource rdf:about=\"https://images.memorix.nl/enb_112/thumb/500x500/d582440d-731a-5578-0517-e1a70faa88c1.jpg\">\n" +
        "        <ebucore:hasMimeType>image/jpeg</ebucore:hasMimeType>\n" +
        "        <nave:deepZoomUrl>https://images.memorix.nl/enb_112/deepzoom/d582440d-731a-5578-0517-e1a70faa88c1.dzi</nave:deepZoomUrl>\n" +
        "        <nave:resourceSortOrder>1</nave:resourceSortOrder>\n" +
        "        <nave:thumbSmall>https://images.memorix.nl/enb_112/thumb/220x220/d582440d-731a-5578-0517-e1a70faa88c1.jpg</nave:thumbSmall>\n" +
        "        <nave:thumbLarge>https://images.memorix.nl/enb_112/thumb/500x500/d582440d-731a-5578-0517-e1a70faa88c1.jpg</nave:thumbLarge>\n" +
        "        <nave:allowDeepZoom>true</nave:allowDeepZoom>\n" +
        "        <nave:allowSourceDownload>false</nave:allowSourceDownload>\n" +
        "        <nave:allowPublicWebView>true</nave:allowPublicWebView>\n" +
        "    </edm:WebResource>\n" +
        "    <edm:Place rdf:about=\"https://sws.geonames.org/2743947\">\n" +
        "        <skos:prefLabel>Zevenbergen</skos:prefLabel>\n" +
        "        <nave:address>Langenoordstraat 66</nave:address>\n" +
        "        <nave:city>Zevenbergen</nave:city>\n" +
        "        <nave:municipality>Gemeente Moerdijk</nave:municipality>\n" +
        "        <nave:street>Langenoordstraat</nave:street>\n" +
        "        <nave:streetNr>66</nave:streetNr>\n" +
        "    </edm:Place>\n" +
        "    <nave:BrabantCloudResource>\n" +
        "        <nave:collection>Stichting Heemkundekring 'Willem van Strijen'</nave:collection>\n" +
        "        <nave:collectionType>Beeldmateriaal</nave:collectionType>\n" +
        "        <nave:place>Zevenbergen</nave:place>\n" +
        "    </nave:BrabantCloudResource>\n" +
        "    <nave:Museometrie>\n" +
        "        <nave:acquisitionYear>enb_112.beeldmateriaal:957193e3-f3a9-6e4d-97ff-9f7390c41e86:005ff52b-4c5e-9a79-5ee9-50bcc65c8a77</nave:acquisitionYear>\n" +
        "    </nave:Museometrie>\n" +
        "    <nave:DelvingResource>\n" +
        "        <nave:featured>false</nave:featured>\n" +
        "        <nave:allowDeepZoom>true</nave:allowDeepZoom>\n" +
        "        <nave:allowLinkedOpenData>true</nave:allowLinkedOpenData>\n" +
        "        <nave:allowSourceDownload>false</nave:allowSourceDownload>\n" +
        "        <nave:public>true</nave:public>\n" +
        "    </nave:DelvingResource>\n" +
        "</edm:RDF>\n";


    public static MetadataRecordFactory getMetadataRecordFactory(RecDefTree recDefTree) {
        Map<String, String> namespaces = new TreeMap<String, String>();
        for(RecDef.Namespace ns : recDefTree.getRecDef().namespaces) {
            namespaces.put(ns.prefix, ns.uri);
        }
        return new MetadataRecordFactory(namespaces);
    }

    @Test
    public void read() throws FileNotFoundException, UnsupportedEncodingException, XMLStreamException, MappingException {
        URL url = getClass().getResource("/edm_5.2.6_record-definition.xml");
        RecDef recDef = RecDef.read(new FileInputStream(new File(url.getFile())));

        url = getClass().getResource("/mapping_edm.xml");

        RecDefTree recDefTree = RecDefTree.create(recDef);

        RecMapping recMapping = RecMapping.read(new FileInputStream((new File(url.getFile()))), recDefTree);

        MetadataRecordFactory metadataRecordFactory = getMetadataRecordFactory(recDefTree);

        BulkMappingRunner runner = new BulkMappingRunner(recMapping, new CodeGenerator(recMapping).toRecordMappingCode());

        MetadataRecord metadataRecord = metadataRecordFactory.metadataRecordFrom(testRecord);

        XmlSerializer serializer = new XmlSerializer();


        for (int i = 0; i < 1; i++) {
            Node node = runner.runMapping(metadataRecord);



            MappingResult result = new MappingResult(serializer, localID, node, recDefTree);

            String xml = result.toXml();

            Assert.assertEquals(expected, xml);
        }

    }
}