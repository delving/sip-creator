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

package eu.delving.metadata;

import javax.xml.namespace.NamespaceContext;
import java.util.*;

/**
 * Build an XPath namespace context using the namespaces from a recdef
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class XPathContext implements NamespaceContext {
    private Map<String, String> prefixUri = new TreeMap<String, String>();
    private Map<String, String> uriPrefix = new TreeMap<String, String>();

    public XPathContext(List<RecDef.Namespace> namespaces) {
        for (RecDef.Namespace namespace : namespaces) {
            prefixUri.put(namespace.prefix, namespace.uri);
            uriPrefix.put(namespace.uri, namespace.prefix);
        }
    }

    @Override
    public String getNamespaceURI(String prefix) {
        return prefixUri.get(prefix);
    }

    @Override
    public String getPrefix(String namespaceURI) {
        return uriPrefix.get(namespaceURI);
    }

    @Override
    public Iterator getPrefixes(String namespaceURI) {
        String prefix = getPrefix(namespaceURI);
        if (prefix == null) return null;
        List<String> list = new ArrayList<String>();
        list.add(prefix);
        return list.iterator();
    }
}
