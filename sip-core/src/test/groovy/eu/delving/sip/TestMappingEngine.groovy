package eu.delving.sip

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
        mappingEngine = new MappingEngine(mapping, namespaces, getClass().getClassLoader())
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
            IndexDocument doc;
            for (int x: 1..10) {
              Long now = System.currentTimeMillis();
//              record.replace("6389", new Random().toString())
              IndexDocument oldDoc = doc;
              doc = mappingEngine.executeMapping(record)
              if(doc != null && oldDoc != null && !doc.toString().equals(oldDoc.toString())) {
                throw new Exception("WRONG");
              }
              Long total = System.currentTimeMillis() - now;
//              println "mapping time: " + total
//              println mappingEngine
            }
            println "VALID!\n ${doc}"
            println 'After 10 runs:\n' + mappingEngine
        }
        catch (ValidationException e) {
            println "The failed assertion:\n${e.message}\n${doc}"
        }

    }


}
