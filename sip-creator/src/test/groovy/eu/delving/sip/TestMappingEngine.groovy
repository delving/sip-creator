package eu.delving.sip

import eu.delving.metadata.MetadataModel
import eu.delving.metadata.MetadataModelImpl
import eu.delving.metadata.ValidationException
import org.apache.commons.io.IOUtils
import org.junit.Before
import org.junit.Test

/**
 * A Groovy unit test of the mapping engine
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

class TestMappingEngine {

    MappingEngine mappingEngine;

    @Before
    void createMappingEngine() {
        Map<String, String> namespaces = new HashMap<String, String>()
        String mapping = IOUtils.toString(getClass().getResourceAsStream("/sample_mapping_icn.xml"), "UTF-8")
        mappingEngine = new MappingEngine(mapping, namespaces, getClass().getClassLoader(), loadMetadataModel())
    }

    @Test
    void initialMapping() {
        try {
            String record =
            """
                <priref>6389</priref>
                <dimension.unit>cm</dimension.unit>
                <dimension.unit>cm</dimension.unit>
                <dimension.unit>cm</dimension.unit>
                <dimension.unit>cm</dimension.unit>
                <dimension.precision>2x</dimension.precision>
                <dimension.precision>2x</dimension.precision>
                <dimension.precision>2x</dimension.precision>
                <dimension.precision>2x</dimension.precision>
                <dimension.type>hoogte</dimension.type>
                <dimension.type>lengte</dimension.type>
                <dimension.type>hoogte</dimension.type>
                <dimension.type>lengte</dimension.type>
                <dimension.value>77</dimension.value>
                <dimension.value>54</dimension.value>
                <dimension.value>57</dimension.value>
                <dimension.value>65</dimension.value>
                <collection>toegepaste kunst</collection>
                <collection>stadsgeschiedenis</collection>
                <collection>onedele metalen</collection>
                <object_name>wandluster</object_name>
                <object_number>10000</object_number>
                <reproduction.reference>o108.jpg</reproduction.reference>
                <reproduction.identifier_URL>\\onedelemetalen\\o108.jpg</reproduction.identifier_URL>
                <techniek.vrije.tekst>ijzer, gesmeed, gegoten, verguld</techniek.vrije.tekst>
                <title>Vier wandlusters</title>
                <creator>Anoniem</creator>
                <creator.date_of_birth.start>?</creator.date_of_birth.start>
                <production.date.start>1780</production.date.start>
                <production.date.end>1799</production.date.end>
                <acquisition.method>schilder</acquisition.method>
                <acquisition.date>1947</acquisition.date>
                <association.subject/>
                <association.subject>bestuurders (Utrecht)</association.subject>
                <priref>6389</priref>
                """
            IndexDocument doc = mappingEngine.executeMapping(record);
            println "VALID!\n ${doc}"
            compareLines(doc.toString(),
               """IndexDocument {
                dc_creator_text -> Anoniem
                dc_date_text -> 1780 - 1799
                dc_format_text ->
                    2x
                    54
                    57
                    65
                    77
                    cm
                    hoogte
                    lengte
                dc_identifier_gumby -> 10000
                dc_title_text -> Vier wandlusters
                dc_type_text -> wandluster
                europeana_collectionTitle_text -> Princessehof
                europeana_country_text -> netherlands
                europeana_dataProvider_text -> Princessehof
                europeana_isShownAt_string -> http://fries-museum.delving.org/portal/object/Princessehof/8A12A315082A345F1A9D3AD14B214CD36D310CF8.html
                europeana_isShownBy_string -> http://fries-museum.delving.org/images/PH/o108.jpg.jpg
                europeana_language_string -> nl
                europeana_object_text -> http://fries-museum.delving.org/images/PH/o108.jpg.jpg
                europeana_provider_text -> Princessehof
                europeana_rights_text -> http://creativecommons.org/licenses/by-nc/3.0/de/
                europeana_type_string -> IMAGE
                europeana_uri_text -> Princessehof/8A12A315082A345F1A9D3AD14B214CD36D310CF8
                icn_acquisitionMeans_text -> onbekend: schilder
                icn_acquisitionYear_text -> 1947
                icn_collectionType_text -> all
               }
            """
            );
        }
        catch (ValidationException e) {
            println "The failed assertion:\n${e.message}\n${doc}"
        }
    }

    static boolean compareLines(String received, String expected) {
        def r = received.split(" *\n[\t ]*")
        def e = expected.split(" *\n[\t ]*")
        assert r == e
    }

    public MetadataModel loadMetadataModel() {
        try {
            MetadataModelImpl metadataModel = new MetadataModelImpl();
            metadataModel.setFactDefinitionsFile(new File(getClass().getResource("/fact-definition-list.xml").getFile()));
            metadataModel.setRecordDefinitionFiles(new File(getClass().getResource("/icn-record-definition.xml").getFile()));
            return metadataModel;
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
            return null;
        }
    }

}
