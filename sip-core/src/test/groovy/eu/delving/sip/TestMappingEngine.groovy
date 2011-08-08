package eu.delving.sip

import eu.delving.groovy.XmlNodePrinter
import eu.delving.metadata.ValidationException
import org.junit.Test

/**
 * A Groovy unit test of the mapping engine
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

class TestMappingEngine {

    Map<String, String> namespaces = new HashMap<String, String>()
    MappingEngine mappingEngine = new MappingEngine(getClass().getResourceAsStream("/sample_mapping_icn.xml"), namespaces)

    @Test
    void initialMapping() {
        try {
            Node node = mappingEngine.executeMapping(
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
                <acquisition.method>aankoop</acquisition.method>
                <acquisition.date>1947</acquisition.date>
                <association.subject/>
                <association.subject>bestuurders (Utrecht)</association.subject>
                <priref>6389</priref>
                """
            )
            println XmlNodePrinter.serialize(node)
        }
        catch (ValidationException e) {
            println "The failed assertion:\n${e.message}"
            println XmlNodePrinter.serialize(e.getRecord())
        }

    }


}
