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

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Generates a JSON-LD {@code @context} (and a framing document built on top
 * of it) from the semantic annotations ({@link RecDefSemantics}) carried by
 * a record definition, so records mapped to this schema can be interpreted
 * as linked data without a separately hand-maintained context.
 *
 * The context always declares every namespace prefix as a plain prefix
 * mapping. On top of that it offers short terms -- the local name of a tag
 * -- for properties and entities, merged ACROSS entities the same way
 * {@link RdfsGenerator} merges properties: one term per distinct tag/URI.
 *
 * Terms are a single shared namespace, so a local name can only mean one
 * URI. Properties claim their terms first (a schema is read/written through
 * its properties far more often than through its class names), then
 * entities fill in whatever local names are still free. Within each group,
 * first occurrence wins; anything whose local name collides with an
 * already-claimed term gets no term at all and stays addressable only via
 * its full/prefixed URI in the mapped-out data.
 *
 * A property term is `{"@id": "<uri>"}` plus either `"@type":"@id"` (when
 * the property has a target entity or is uriCheck -- i.e. its value is
 * itself a resource) or `"@type":"<datatype URI>"` (when a datatype is
 * declared). A property with neither is emitted as the plain string URI
 * shorthand. Entity terms are always the plain string URI shorthand.
 *
 * A property tag's uses are accumulated ACROSS every entity it appears
 * under -- mirroring {@link RdfsGenerator}'s PropertyAccumulator -- rather
 * than deciding the term's shape from whichever use happens to come first
 * in document order. Any use with a target or uriCheck wins "@id"; failing
 * that, any use with a dataType wins the datatype (first non-null dataType
 * across uses); only a tag whose every use has neither becomes a plain
 * string term.
 *
 * Properties in the `rdf:` namespace (e.g. `rdf:type`) are never claimed as
 * context terms: they are RDF/XML-syntax properties, not schema vocabulary,
 * and claiming their local name would collide with genuine schema terms.
 */
public class JsonLdContextGenerator {

    public static String generateContext(RecDef recDef) {
        JsonObject root = new JsonObject();
        root.add("@context", buildContext(RecDefSemantics.from(recDef)));
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    public static String generateFrame(RecDef recDef) {
        RecDefSemantics semantics = RecDefSemantics.from(recDef);
        JsonObject root = new JsonObject();
        root.add("@context", buildContext(semantics));

        JsonArray types = new JsonArray();
        for (RecDefSemantics.Entity entity : semantics.entities.values()) {
            if (!entity.fromRoot) continue;
            types.add(semantics.uriFor(entity.tag));
        }
        root.add("@type", types);

        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    private static JsonObject buildContext(RecDefSemantics semantics) {
        JsonObject ctx = new JsonObject();
        for (Map.Entry<String, String> ns : semantics.namespaces.entrySet()) {
            ctx.addProperty(ns.getKey(), ns.getValue());
        }

        // Terms are a single shared namespace: properties claim first, then
        // entities fill in whatever local names remain free.
        Set<String> claimedTerms = new LinkedHashSet<>();

        Map<String, PropertyAccumulator> properties = new LinkedHashMap<>();
        for (RecDefSemantics.Entity entity : semantics.entities.values()) {
            for (RecDefSemantics.PropertyUse use : entity.properties) {
                if (isRdfNamespace(use.tag)) continue; // RDF/XML syntax property, not schema vocabulary
                properties.computeIfAbsent(use.tag, tag -> new PropertyAccumulator()).addUse(use);
            }
        }
        for (Map.Entry<String, PropertyAccumulator> entry : properties.entrySet()) {
            String tag = entry.getKey();
            String term = localName(tag);
            if (!claimedTerms.add(term)) continue; // term already taken -- stays prefixed
            ctx.add(term, propertyTermValue(semantics, tag, entry.getValue()));
        }

        for (RecDefSemantics.Entity entity : semantics.entities.values()) {
            String term = localName(entity.tag);
            if (!claimedTerms.add(term)) continue; // term already taken -- stays prefixed
            ctx.addProperty(term, semantics.uriFor(entity.tag));
        }

        return ctx;
    }

    private static JsonElement propertyTermValue(RecDefSemantics semantics, String tag, PropertyAccumulator acc) {
        String uri = semantics.uriFor(tag);
        if (acc.isResourceValued) {
            JsonObject value = new JsonObject();
            value.addProperty("@id", uri);
            value.addProperty("@type", "@id");
            return value;
        }
        if (acc.dataType != null) {
            JsonObject value = new JsonObject();
            value.addProperty("@id", uri);
            value.addProperty("@type", semantics.uriFor(acc.dataType));
            return value;
        }
        return new JsonPrimitive(uri);
    }

    private static String localName(String tag) {
        int colon = tag.indexOf(':');
        return colon < 0 ? tag : tag.substring(colon + 1);
    }

    private static boolean isRdfNamespace(String tag) {
        int colon = tag.indexOf(':');
        return colon >= 0 && "rdf".equals(tag.substring(0, colon));
    }

    /**
     * Accumulates every use of one merged property tag across entities, the
     * same way {@link RdfsGenerator.PropertyAccumulator} does for the RDFS
     * output: a term's shape is decided by the union of its uses, not by
     * whichever use happens to come first in document order.
     */
    private static class PropertyAccumulator {
        boolean isResourceValued = false;
        String dataType;

        void addUse(RecDefSemantics.PropertyUse use) {
            boolean hasTarget = use.target != null && !use.target.trim().isEmpty();
            if (hasTarget || use.uriCheck) {
                isResourceValued = true;
            }
            if (dataType == null && use.dataType != null) {
                dataType = use.dataType;
            }
        }
    }
}
