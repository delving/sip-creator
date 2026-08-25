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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared semantic model parsed from an unresolved record definition, giving
 * the RDFS/SHACL/JSON-LD-context generators a common view of the ontology
 * annotations (subclassof, equivalentClass, subPropertyOf, doc labels) that
 * the record definition carries alongside its mapping structure.
 *
 * Entities are collected the same way as {@link XsdGenerator}: templates
 * first, then root children overwriting on the same tag (root declares the
 * full mappable shape; a template is just a reusable fragment).
 */
public class RecDefSemantics {

    public final String ontologyUri;
    public final List<String> imports;
    public final String version;
    public final Map<String, String> namespaces;
    public final Map<String, Entity> entities;

    private RecDefSemantics(String ontologyUri, List<String> imports, String version,
                             Map<String, String> namespaces, Map<String, Entity> entities) {
        this.ontologyUri = ontologyUri;
        this.imports = Collections.unmodifiableList(imports);
        this.version = version;
        this.namespaces = Collections.unmodifiableMap(namespaces);
        this.entities = Collections.unmodifiableMap(entities);
    }

    public static RecDefSemantics from(RecDef recDef) {
        Map<String, String> namespaces = new LinkedHashMap<>();
        namespaces.put("xsd", "http://www.w3.org/2001/XMLSchema#");
        namespaces.put("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        if (recDef.namespaces != null) {
            for (RecDef.Namespace ns : recDef.namespaces) {
                namespaces.put(ns.prefix, ns.uri);
            }
        }

        String ontologyUri = namespaces.getOrDefault(recDef.prefix, "");

        List<String> imports = new ArrayList<>();
        if (recDef.namespaces != null) {
            for (RecDef.Namespace ns : recDef.namespaces) {
                if ("rdf".equals(ns.prefix) || "xsd".equals(ns.prefix) || "xml".equals(ns.prefix)) continue;
                if (ns.prefix != null && ns.prefix.equals(recDef.prefix)) continue;
                imports.add(ns.uri);
            }
        }

        // Collect entity definitions: templates + root children, keyed by tag.
        // Reference: XsdGenerator.generate, lines 50-61.
        Map<String, RecDef.Elem> entityElems = new LinkedHashMap<>();
        if (recDef.templates != null) {
            for (RecDef.Elem template : recDef.templates) {
                if (!hasUsableTag(template)) continue;
                entityElems.put(template.tag.toString(), template);
            }
        }
        if (recDef.root != null && recDef.root.subelements != null) {
            for (RecDef.Elem child : recDef.root.subelements) {
                if (!hasUsableTag(child)) continue;
                // Root declaration wins: it is the full mappable shape.
                entityElems.put(child.tag.toString(), child);
            }
        }

        Map<String, Entity> entities = new LinkedHashMap<>();
        for (Map.Entry<String, RecDef.Elem> entry : entityElems.entrySet()) {
            entities.put(entry.getKey(), toEntity(entry.getKey(), entry.getValue()));
        }

        return new RecDefSemantics(ontologyUri, imports, recDef.version, namespaces, entities);
    }

    public String uriFor(String curie) {
        if (curie.startsWith("http://") || curie.startsWith("https://")) return curie;
        int colon = curie.indexOf(':');
        if (colon < 0) throw new IllegalArgumentException("Not a curie: " + curie);
        String prefix = curie.substring(0, colon);
        String localName = curie.substring(colon + 1);
        String uri = namespaces.get(prefix);
        if (uri == null) throw new IllegalArgumentException("Unknown prefix: " + prefix);
        return uri + localName;
    }

    private static boolean hasUsableTag(RecDef.Elem elem) {
        return elem.tag != null && elem.tag.getLocalName() != null && !elem.tag.getLocalName().isEmpty();
    }

    private static Entity toEntity(String tag, RecDef.Elem elem) {
        Map<String, String> labels = labelsOf(elem);
        Map<String, String> definitions = definitionsOf(elem);

        List<String> subClassOf = new ArrayList<>();
        if (elem.subclassof != null) {
            for (String s : elem.subclassof.split(",")) subClassOf.add(s.trim());
        }

        List<PropertyUse> properties = new ArrayList<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        if (elem.subelements != null) {
            for (RecDef.Elem child : elem.subelements) {
                if (!hasUsableTag(child)) continue;
                String childTag = child.tag.toString();
                if (seen.containsKey(childTag)) continue;
                seen.put(childTag, Boolean.TRUE);
                properties.add(toPropertyUse(childTag, child));
            }
        }

        return new Entity(tag, labels, definitions, subClassOf, elem.equivalentClass, properties);
    }

    private static PropertyUse toPropertyUse(String tag, RecDef.Elem elem) {
        String dataType = elem.xsdDataType != null ? elem.xsdDataType : (elem.uriCheck ? "xsd:anyURI" : null);
        String minOccurs = elem.xsdMinOccurs != null ? elem.xsdMinOccurs : (elem.required ? "1" : "0");
        String maxOccurs = elem.xsdMaxOccurs != null ? elem.xsdMaxOccurs : (elem.singular ? "1" : null);
        Map<String, String> labels = labelsOf(elem);
        Map<String, String> definitions = definitionsOf(elem);
        return new PropertyUse(tag, elem.target, dataType, elem.xsdPattern, minOccurs, maxOccurs,
            elem.uriCheck, elem.subPropertyOf, labels, definitions);
    }

    private static Map<String, String> labelsOf(RecDef.Elem elem) {
        return docParas(elem, "Label");
    }

    private static Map<String, String> definitionsOf(RecDef.Elem elem) {
        return docParas(elem, "Definition");
    }

    private static Map<String, String> docParas(RecDef.Elem elem, String name) {
        Map<String, String> result = new LinkedHashMap<>();
        if (elem.doc == null) return Collections.unmodifiableMap(result);
        List<RecDef.DocParagraph> paras = elem.doc.paraList != null ? elem.doc.paraList : elem.doc.paras;
        if (paras == null) return Collections.unmodifiableMap(result);
        for (RecDef.DocParagraph para : paras) {
            if (!name.equals(para.name)) continue;
            if (para.lang == null) continue;
            result.put(para.lang, para.content);
        }
        return Collections.unmodifiableMap(result);
    }

    public static class Entity {
        public final String tag;
        public final Map<String, String> labels;
        public final Map<String, String> definitions;
        public final List<String> subClassOf;
        public final String equivalentClass;
        public final List<PropertyUse> properties;

        Entity(String tag, Map<String, String> labels, Map<String, String> definitions,
               List<String> subClassOf, String equivalentClass, List<PropertyUse> properties) {
            this.tag = tag;
            this.labels = labels;
            this.definitions = definitions;
            this.subClassOf = Collections.unmodifiableList(subClassOf);
            this.equivalentClass = equivalentClass;
            this.properties = Collections.unmodifiableList(properties);
        }
    }

    public static class PropertyUse {
        public final String tag;
        public final String target;
        public final String dataType;
        public final String pattern;
        public final String minOccurs;
        public final String maxOccurs;
        public final boolean uriCheck;
        public final String subPropertyOf;
        public final Map<String, String> labels;
        public final Map<String, String> definitions;

        PropertyUse(String tag, String target, String dataType, String pattern, String minOccurs,
                    String maxOccurs, boolean uriCheck, String subPropertyOf,
                    Map<String, String> labels, Map<String, String> definitions) {
            this.tag = tag;
            this.target = target;
            this.dataType = dataType;
            this.pattern = pattern;
            this.minOccurs = minOccurs;
            this.maxOccurs = maxOccurs;
            this.uriCheck = uriCheck;
            this.subPropertyOf = subPropertyOf;
            this.labels = labels;
            this.definitions = definitions;
        }
    }
}
