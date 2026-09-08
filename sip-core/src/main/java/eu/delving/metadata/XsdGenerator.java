/*
 * Copyright 2026 Delving BV
 *
 * Licensed under the EUPL, Version 1.0 or as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the
 * Licence.
 */

package eu.delving.metadata;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Generates a LAX validation XSD from a record definition, so a schema version
 * uploaded without a hand-made XSD still gets output validation in SIP-Creator.
 *
 * Lax means: anything not declared or not annotated is allowed (xs:any /
 * xs:anyAttribute, minOccurs=0, maxOccurs=unbounded). Strictness comes from
 * the modeller's annotations only:
 *
 *   required="true"        -> minOccurs="1"
 *   singular="true"        -> maxOccurs="1"
 *   xsdMinOccurs/xsdMaxOccurs -> explicit cardinality (override the booleans)
 *   xsdDataType="xs:date"  -> typed simple content
 *   xsdPattern="regex"     -> xs:pattern restriction
 *   uriCheck="true"        -> xs:anyURI content
 *
 * Entities are emitted as named complex types keyed by tag (cycle-safe: types
 * reference each other by name), assembled from the UNRESOLVED recdef: root
 * children plus templates. Every namespace declared in the recdef is imported
 * laxly via xs:any namespace="##any".
 */
public class XsdGenerator {

    public static String generate(RecDef recDef) {
        StringBuilder out = new StringBuilder();
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        out.append("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n");
        for (RecDef.Namespace ns : recDef.namespaces) {
            out.append("           xmlns:").append(ns.prefix).append("=\"").append(escape(ns.uri)).append("\"\n");
        }
        out.append("           elementFormDefault=\"qualified\" attributeFormDefault=\"qualified\">\n\n");

        // Collect entity definitions: templates + root children, keyed by tag.
        Map<String, RecDef.Elem> entities = new LinkedHashMap<>();
        if (recDef.templates != null) {
            for (RecDef.Elem template : recDef.templates) {
                entities.put(template.tag.toString(), template);
            }
        }
        if (recDef.root != null && recDef.root.subelements != null) {
            for (RecDef.Elem child : recDef.root.subelements) {
                // Root declaration wins: it is the full mappable shape.
                entities.put(child.tag.toString(), child);
            }
        }
        // Elems declared inline via subelements are validated laxly (xs:any).

        // Root element: rdf:RDF containing any of the entities.
        String rootTag = recDef.root != null ? recDef.root.tag.toString() : "rdf:RDF";
        out.append("  <xs:element name=\"").append(localName(rootTag)).append("\">\n");
        out.append("    <xs:complexType>\n");
        out.append("      <xs:sequence>\n");
        out.append("        <xs:any namespace=\"##any\" processContents=\"lax\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
        out.append("      </xs:sequence>\n");
        out.append("      <xs:anyAttribute namespace=\"##any\" processContents=\"lax\"/>\n");
        out.append("    </xs:complexType>\n");
        out.append("  </xs:element>\n\n");

        Set<String> emitted = new LinkedHashSet<>();
        for (Map.Entry<String, RecDef.Elem> entry : entities.entrySet()) {
            emitComplexType(out, recDef, entry.getKey(), entry.getValue(), emitted);
        }

        out.append("</xs:schema>\n");
        return out.toString();
    }

    private static void emitComplexType(StringBuilder out, RecDef recDef, String tag, RecDef.Elem elem, Set<String> emitted) {
        String typeName = typeName(tag);
        if (!emitted.add(typeName)) return;
        out.append("  <xs:complexType name=\"").append(typeName).append("\">\n");
        out.append("    <xs:sequence>\n");
        boolean declared = false;
        // XSD 1.0 element-consistency: one declaration per element name in a
        // model group. Recdefs may declare the same property twice (different
        // targets); merge to the first occurrence, cardinality stays lax.
        Set<String> seenNames = new LinkedHashSet<>();
        if (elem.subelements != null) {
            for (RecDef.Elem child : elem.subelements) {
                if (child.tag == null || !seenNames.add(localName(child.tag.toString()))) continue;
                int before = out.length();
                emitPropertyElement(out, recDef, child);
                declared |= out.length() > before;
            }
        }
        // XSD 1.0 UPA: declared elements and a wildcard cannot share a
        // sequence. Output is generated from this same recdef, so the
        // declared property set IS complete — wildcard only when nothing
        // is declared at all.
        if (!declared) {
            out.append("      <xs:any namespace=\"##any\" processContents=\"lax\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
        }
        out.append("    </xs:sequence>\n");
        out.append("    <xs:anyAttribute namespace=\"##any\" processContents=\"lax\"/>\n");
        out.append("  </xs:complexType>\n\n");
    }

    private static void emitPropertyElement(StringBuilder out, RecDef recDef, RecDef.Elem elem) {
        if (elem.tag == null) return;
        // A tagless or prefix-only elem cannot be a valid xs:element name —
        // it is a recdef defect the resolver reports elsewhere; skip here.
        if (localName(elem.tag.toString()).isEmpty()) return;
        String min = elem.xsdMinOccurs != null ? elem.xsdMinOccurs : (elem.required ? "1" : "0");
        String max = elem.xsdMaxOccurs != null ? elem.xsdMaxOccurs : (elem.singular ? "1" : "unbounded");
        out.append("      <xs:element name=\"").append(localName(elem.tag.toString()))
           .append("\" minOccurs=\"").append(min)
           .append("\" maxOccurs=\"").append(max).append("\"");
        String dataType = elem.xsdDataType != null ? schemaPrefixed(elem.xsdDataType, recDef) : (elem.uriCheck ? "xs:anyURI" : null);
        if (elem.xsdPattern != null) {
            out.append(">\n");
            out.append("        <xs:simpleType>\n");
            out.append("          <xs:restriction base=\"").append(dataType != null ? dataType : "xs:string").append("\">\n");
            out.append("            <xs:pattern value=\"").append(escape(elem.xsdPattern)).append("\"/>\n");
            out.append("          </xs:restriction>\n");
            out.append("        </xs:simpleType>\n");
            out.append("      </xs:element>\n");
        } else if (dataType != null) {
            out.append(" type=\"").append(dataType).append("\"/>\n");
        } else {
            // Lax content: attributes and nested entities allowed.
            out.append(">\n");
            out.append("        <xs:complexType mixed=\"true\">\n");
            out.append("          <xs:sequence>\n");
            out.append("            <xs:any namespace=\"##any\" processContents=\"lax\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>\n");
            out.append("          </xs:sequence>\n");
            out.append("          <xs:anyAttribute namespace=\"##any\" processContents=\"lax\"/>\n");
            out.append("        </xs:complexType>\n");
            out.append("      </xs:element>\n");
        }
    }

    /**
     * Recdefs spell datatypes the RDF way, {@code xsd:string} with {@code xsd} bound to
     * {@code http://www.w3.org/2001/XMLSchema#}. Inside an XSD that prefix must resolve to the
     * XML Schema namespace itself, so rewrite it to this document's {@code xs} prefix.
     */
    private static String schemaPrefixed(String dataType, RecDef recDef) {
        int colon = dataType.indexOf(':');
        if (colon < 0) return dataType;
        String prefix = dataType.substring(0, colon);
        boolean schemaNamespace = prefix.equals("xsd");
        if (recDef.namespaces != null) {
            for (RecDef.Namespace ns : recDef.namespaces) {
                if (prefix.equals(ns.prefix)) {
                    schemaNamespace = ns.uri != null && ns.uri.replaceAll("#$", "").equals("http://www.w3.org/2001/XMLSchema");
                }
            }
        }
        return schemaNamespace ? "xs:" + dataType.substring(colon + 1) : dataType;
    }

    private static String localName(String tag) {
        int colon = tag.indexOf(':');
        return colon < 0 ? tag : tag.substring(colon + 1);
    }

    private static String typeName(String tag) {
        return tag.replace(':', '_') + "_Type";
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
