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
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

import java.io.StringWriter;
import java.math.BigInteger;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Generates SHACL shapes (Turtle) from the semantic annotations
 * ({@link RecDefSemantics}) carried by a record definition, so a schema
 * version can validate the records it maps against the constraints its
 * modeller actually annotated -- a mirror of {@link XsdGenerator}'s lax
 * XSD, but expressed as SHACL node/property shapes.
 *
 * Only annotated constraints are emitted: sh:minCount/sh:maxCount from
 * cardinality, sh:datatype from an explicit XSD type, sh:nodeKind sh:IRI
 * from uriCheck ONLY, sh:class per resolvable target, sh:pattern from an
 * explicit regex. A target property gets sh:class but deliberately NOT
 * sh:nodeKind sh:IRI, because a target's value may legitimately be an
 * inline typed blank node (rdf:parseType="Resource") rather than an IRI
 * reference -- sh:nodeKind sh:IRI would wrongly reject those. Nothing else
 * is constrained -- in particular there is NO sh:closed, because the same
 * tag can be dually declared (once via target, once via datatype in a
 * different entity) and processed records commonly carry extra rdf:type
 * triples; a closed shape would false-positive on both.
 *
 * jena-arq 3.17 ships no SHACL vocabulary class, so the sh: terms used here
 * are declared privately below, exactly as far as this generator needs them.
 *
 * Unresolvable CURIEs at REFERENCE positions (property target) are skipped
 * one triple at a time, mirroring {@link RdfsGenerator}'s resolve-or-skip
 * approach -- an external vocabulary the recdef author forgot to declare a
 * namespace for should not kill generation of everything else. An
 * unresolvable entity or property SUBJECT tag is a broken recdef and throws.
 */
public class ShaclGenerator {

    private static final String SH = "http://www.w3.org/ns/shacl#";

    public static String generate(RecDef recDef) {
        RecDefSemantics semantics = RecDefSemantics.from(recDef);
        Model model = ModelFactory.createDefaultModel();

        Property shNodeShape = model.createProperty(SH, "NodeShape");
        Property shTargetClass = model.createProperty(SH, "targetClass");
        Property shProperty = model.createProperty(SH, "property");
        Property shPath = model.createProperty(SH, "path");
        Property shMinCount = model.createProperty(SH, "minCount");
        Property shMaxCount = model.createProperty(SH, "maxCount");
        Property shDatatype = model.createProperty(SH, "datatype");
        Property shNodeKind = model.createProperty(SH, "nodeKind");
        Property shIRI = model.createProperty(SH, "IRI");
        Property shClass = model.createProperty(SH, "class");
        Property shPattern = model.createProperty(SH, "pattern");

        for (Map.Entry<String, String> ns : semantics.namespaces.entrySet()) {
            model.setNsPrefix(ns.getKey(), ns.getValue());
        }
        model.setNsPrefix("sh", SH);

        for (RecDefSemantics.Entity entity : semantics.entities.values()) {
            String entityUri = semantics.uriFor(entity.tag);
            Resource shape = model.createResource(semantics.ontologyUri + "shapes/" + localName(entityUri) + "Shape");
            shape.addProperty(RDF.type, shNodeShape);
            shape.addProperty(shTargetClass, model.createResource(entityUri));

            for (RecDefSemantics.PropertyUse use : entity.properties) {
                Resource propertyShape = model.createResource();
                shape.addProperty(shProperty, propertyShape);
                propertyShape.addProperty(shPath, model.createResource(semantics.uriFor(use.tag)));

                if (use.minOccurs != null && !"0".equals(use.minOccurs)) {
                    addCountOrSkip(shMinCount, use.minOccurs, propertyShape, model);
                }
                if (use.maxOccurs != null) {
                    // xsdMaxOccurs is also an XSD-facing free-text field (see XsdGenerator),
                    // so a recdef can legitimately set it to "unbounded" -- SHACL has no such
                    // value, and simply omitting sh:maxCount already means unbounded.
                    addCountOrSkip(shMaxCount, use.maxOccurs, propertyShape, model);
                }
                boolean isUriDataType = use.uriCheck && "xsd:anyURI".equals(use.dataType);
                if (use.dataType != null && !isUriDataType) {
                    resolveOrSkip(semantics, use.dataType, uri ->
                        propertyShape.addProperty(shDatatype, model.createResource(uri)));
                }
                if (use.uriCheck) {
                    propertyShape.addProperty(shNodeKind, shIRI);
                }
                if (use.target != null && !use.target.isEmpty()) {
                    for (String targetCurie : use.target.split(",")) {
                        String trimmed = targetCurie.trim();
                        if (trimmed.isEmpty()) continue;
                        resolveOrSkip(semantics, trimmed, uri ->
                            propertyShape.addProperty(shClass, model.createResource(uri)));
                    }
                }
                if (use.pattern != null) {
                    propertyShape.addProperty(shPattern, use.pattern);
                }
            }
        }

        StringWriter writer = new StringWriter();
        model.write(writer, "TURTLE");
        return writer.toString();
    }

    private static String localName(String uri) {
        int hash = uri.lastIndexOf('#');
        int slash = uri.lastIndexOf('/');
        int cut = Math.max(hash, slash);
        return cut < 0 ? uri : uri.substring(cut + 1);
    }

    private static void addCountOrSkip(Property shCountProperty, String occurs, Resource propertyShape, Model model) {
        try {
            propertyShape.addProperty(shCountProperty, model.createTypedLiteral(new BigInteger(occurs)));
        } catch (NumberFormatException e) {
            // e.g. xsdMaxOccurs="unbounded" -- not a SHACL count, skip.
        }
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
}
