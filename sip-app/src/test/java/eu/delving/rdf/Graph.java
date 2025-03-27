/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.rdf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RDF Graph
 *
 *
 */

public class Graph {
    private Map<String, String> pathUri = new HashMap<String, String>();
    private List<Triple> triples = new ArrayList<Triple>();

    public Entity entity(String entityClass, String path, String generatedUri) {
        String uri = pathUri.get(path);
        if (uri == null) {
            pathUri.put(path, uri = generatedUri);
        }
        return new Entity(entityClass, uri);
    }

    public Triple triple(Entity subject, String predicate, Entity object) {
        Triple triple = new Triple(subject, predicate, object);
        triples.add(triple);
        return triple;
    }

    public class Triple {
        public final Entity subject;
        public final String predicate;
        public final Entity object;

        public Triple(Entity subject, String predicate, Entity object) {
            this.subject = subject;
            this.predicate = predicate;
            this.object = object;
        }

        public String toString() {
            return String.format("%s - %s - %s", subject, predicate, object);
        }
    }

    public class Entity {
        public final String entityClass;
        public final String uri;

        public Entity(String entityClass, String uri) {
            this.entityClass = entityClass;
            this.uri = uri;
        }

        public String toString() {
            return String.format("Entity(%s,%s)", entityClass, uri);
        }
    }

    public String toString() {
        StringBuilder out = new StringBuilder();
        out.append("graph:\n");
        for (Triple triple : triples) {
            out.append(triple).append("\n");
        }
        return out.toString();
    }
}
