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

        Map<String, PropertyUseAccumulator> properties = new LinkedHashMap<>();
        for (RecDefSemantics.Entity entity : semantics.entities.values()) {
            for (RecDefSemantics.PropertyUse use : entity.properties) {
                properties.computeIfAbsent(use.tag, tag -> new PropertyUseAccumulator(use));
            }
        }
        for (Map.Entry<String, PropertyUseAccumulator> entry : properties.entrySet()) {
            String tag = entry.getKey();
            String term = localName(tag);
            if (!claimedTerms.add(term)) continue; // term already taken -- stays prefixed
            ctx.add(term, propertyTermValue(semantics, tag, entry.getValue().use));
        }

        for (RecDefSemantics.Entity entity : semantics.entities.values()) {
            String term = localName(entity.tag);
            if (!claimedTerms.add(term)) continue; // term already taken -- stays prefixed
            ctx.addProperty(term, semantics.uriFor(entity.tag));
        }

        return ctx;
    }

    private static JsonElement propertyTermValue(RecDefSemantics semantics, String tag, RecDefSemantics.PropertyUse use) {
        String uri = semantics.uriFor(tag);
        boolean hasTarget = use.target != null && !use.target.trim().isEmpty();
        if (hasTarget || use.uriCheck) {
            JsonObject value = new JsonObject();
            value.addProperty("@id", uri);
            value.addProperty("@type", "@id");
            return value;
        }
        if (use.dataType != null) {
            JsonObject value = new JsonObject();
            value.addProperty("@id", uri);
            value.addProperty("@type", semantics.uriFor(use.dataType));
            return value;
        }
        return new JsonPrimitive(uri);
    }

    private static String localName(String tag) {
        int colon = tag.indexOf(':');
        return colon < 0 ? tag : tag.substring(colon + 1);
    }

    /** Holds the first use of a merged property tag -- first occurrence wins the term's shape. */
    private static class PropertyUseAccumulator {
        final RecDefSemantics.PropertyUse use;

        PropertyUseAccumulator(RecDefSemantics.PropertyUse use) {
            this.use = use;
        }
    }
}
