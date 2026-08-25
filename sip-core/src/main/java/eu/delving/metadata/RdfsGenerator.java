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

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Generates an OWL/RDFS ontology (classes + properties) from the semantic
 * annotations ({@link RecDefSemantics}) carried by a record definition, so a
 * schema version can publish its own vocabulary description alongside the
 * mapping it defines.
 *
 * Properties are declared once, merged ACROSS entities by tag: a property
 * used under two different entities gets one rdf:Property/owl:*Property
 * resource with an rdfs:domain triple per declaring entity. The type is a
 * strict binary — owl:ObjectProperty if any merged use has a target, else
 * owl:DatatypeProperty — and range emission follows the same gate: an
 * object property gets one rdfs:range triple per distinct target (union
 * across uses), a datatype property gets a single rdfs:range triple for
 * the XSD datatype. The two are mutually exclusive per property so a merged
 * property never ends up with both a class range and a datatype range,
 * which OWL-DL forbids. This mirrors how the same recdef tag can appear as
 * a subelement of several entities without meaning several distinct
 * properties.
 *
 * Unresolvable CURIEs at REFERENCE positions (subclassof, equivalentClass,
 * property target, datatype) are skipped one triple at a time — an external
 * vocabulary the recdef author forgot to declare a namespace for should not
 * kill generation of everything else. An unresolvable entity or property
 * SUBJECT tag is a broken recdef and throws.
 */
public class RdfsGenerator {

    public static String generate(RecDef recDef, String jenaLang) {
        RecDefSemantics semantics = RecDefSemantics.from(recDef);
        Model model = ModelFactory.createDefaultModel();

        for (Map.Entry<String, String> ns : semantics.namespaces.entrySet()) {
            model.setNsPrefix(ns.getKey(), ns.getValue());
        }

        Resource ontology = model.createResource(semantics.ontologyUri);
        ontology.addProperty(RDF.type, OWL.Ontology);
        for (String importUri : semantics.imports) {
            ontology.addProperty(OWL.imports, model.createResource(importUri));
        }
        if (semantics.version != null) {
            ontology.addProperty(OWL.versionInfo, semantics.version);
        }

        // Properties are merged across entities by tag, so collect their
        // uses first and emit each property resource once.
        Map<String, PropertyAccumulator> properties = new LinkedHashMap<>();

        for (RecDefSemantics.Entity entity : semantics.entities.values()) {
            Resource classResource = model.createResource(semantics.uriFor(entity.tag));
            classResource.addProperty(RDF.type, OWL.Class);
            classResource.addProperty(RDFS.isDefinedBy, ontology);

            for (Map.Entry<String, String> label : entity.labels.entrySet()) {
                classResource.addProperty(RDFS.label, model.createLiteral(label.getValue(), label.getKey()));
            }
            for (Map.Entry<String, String> def : entity.definitions.entrySet()) {
                classResource.addProperty(RDFS.comment, model.createLiteral(def.getValue(), def.getKey()));
            }
            for (String subClassOf : entity.subClassOf) {
                resolveSubClassOfOrSkip(semantics, subClassOf, uri ->
                    classResource.addProperty(RDFS.subClassOf, model.createResource(uri)));
            }
            if (entity.equivalentClass != null) {
                resolveOrSkip(semantics, entity.equivalentClass, uri ->
                    classResource.addProperty(OWL.equivalentClass, model.createResource(uri)));
            }

            for (RecDefSemantics.PropertyUse use : entity.properties) {
                properties.computeIfAbsent(use.tag, tag -> new PropertyAccumulator())
                    .addUse(entity.tag, use);
            }
        }

        for (Map.Entry<String, PropertyAccumulator> entry : properties.entrySet()) {
            String tag = entry.getKey();
            PropertyAccumulator acc = entry.getValue();
            Resource propertyResource = model.createResource(semantics.uriFor(tag));
            propertyResource.addProperty(RDF.type, acc.hasTarget ? OWL.ObjectProperty : OWL.DatatypeProperty);

            for (String domainTag : acc.domainTags) {
                resolveOrSkip(semantics, domainTag, uri ->
                    propertyResource.addProperty(RDFS.domain, model.createResource(uri)));
            }
            // Property type is a strict binary (ObjectProperty XOR DatatypeProperty),
            // so range must follow the same gate: a target-derived range for object
            // properties, a datatype-derived range otherwise. Emitting both would
            // produce an owl:ObjectProperty with an XSD-datatype range, which is
            // invalid OWL-DL.
            if (acc.hasTarget) {
                for (String targetCurie : acc.targets) {
                    resolveOrSkip(semantics, targetCurie, uri ->
                        propertyResource.addProperty(RDFS.range, model.createResource(uri)));
                }
            } else if (acc.dataType != null) {
                resolveOrSkip(semantics, acc.dataType, uri ->
                    propertyResource.addProperty(RDFS.range, model.createResource(uri)));
            }
            if (acc.subPropertyOf != null) {
                resolveOrSkip(semantics, acc.subPropertyOf, uri ->
                    propertyResource.addProperty(RDFS.subPropertyOf, model.createResource(uri)));
            }
            for (Map.Entry<String, String> label : acc.labels.entrySet()) {
                propertyResource.addProperty(RDFS.label, model.createLiteral(label.getValue(), label.getKey()));
            }
            for (Map.Entry<String, String> def : acc.definitions.entrySet()) {
                propertyResource.addProperty(RDFS.comment, model.createLiteral(def.getValue(), def.getKey()));
            }
        }

        StringWriter writer = new StringWriter();
        model.write(writer, jenaLang);
        return writer.toString();
    }

    private static void resolveOrSkip(RecDefSemantics semantics, String curie, Consumer<String> emit) {
        String uri;
        try {
            uri = semantics.uriFor(curie);
        } catch (IllegalArgumentException e) {
            return;
        }
        emit.accept(uri);
    }

    // subClassOf follows the recdef's own label-based parent-reference convention
    // (see RecDefSemantics#uriForSubClassOf), not a plain curie -- resolve it that way.
    private static void resolveSubClassOfOrSkip(RecDefSemantics semantics, String ref, Consumer<String> emit) {
        String uri;
        try {
            uri = semantics.uriForSubClassOf(ref);
        } catch (IllegalArgumentException e) {
            return;
        }
        emit.accept(uri);
    }

    /** Accumulates all uses of one property tag across entities. */
    private static class PropertyAccumulator {
        final Set<String> domainTags = new LinkedHashSet<>();
        final Set<String> targets = new LinkedHashSet<>();
        boolean hasTarget = false;
        String dataType;
        String subPropertyOf;
        final Map<String, String> labels = new LinkedHashMap<>();
        final Map<String, String> definitions = new LinkedHashMap<>();

        void addUse(String entityTag, RecDefSemantics.PropertyUse use) {
            domainTags.add(entityTag);
            if (use.target != null && !use.target.isEmpty()) {
                hasTarget = true;
                for (String t : use.target.split(",")) {
                    String trimmed = t.trim();
                    if (!trimmed.isEmpty()) targets.add(trimmed);
                }
            }
            if (dataType == null && use.dataType != null) {
                dataType = use.dataType;
            }
            if (subPropertyOf == null && use.subPropertyOf != null) {
                subPropertyOf = use.subPropertyOf;
            }
            if (labels.isEmpty() && !use.labels.isEmpty()) {
                labels.putAll(use.labels);
            }
            if (definitions.isEmpty() && !use.definitions.isEmpty()) {
                definitions.putAll(use.definitions);
            }
        }
    }
}
