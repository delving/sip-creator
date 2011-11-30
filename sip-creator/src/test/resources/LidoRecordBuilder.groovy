/**
 * Experimentation
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

import groovy.xml.MarkupBuilder
import groovy.xml.NamespaceBuilder

def writer = new StringWriter()
def xml = new MarkupBuilder(writer)
def xmlns = new NamespaceBuilder(xml);
def lido = xmlns.namespace('http://www.lido-schema.org', 'lido')

lido.lido {
    lido.lidoRecID('lido:type': 'local', 'lido:source':'source', 'DE-Mb112/lido/obj/00000001')
    lido.category {
        lido.conceptID('lido.type': 'URI') {
            'http://www.cidoc-crm.org/crm-concepts/E22'
        }
    }
}

println writer
