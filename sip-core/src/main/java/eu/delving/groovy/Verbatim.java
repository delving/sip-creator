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

package eu.delving.groovy;

/**
 * Wrap a GroovyNode in this to mark it so that the DOM Builder makes nodes from its contents
 * in a verbatim fashion.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class Verbatim {
    public GroovyNode root;

    public static Verbatim capture(GroovyNode node) {
        return new Verbatim(node);
    }

    private Verbatim(GroovyNode root) {
        this.root = root;
    }

    public String toString() {
        return XmlNodePrinter.toXml(root);
    }
}
