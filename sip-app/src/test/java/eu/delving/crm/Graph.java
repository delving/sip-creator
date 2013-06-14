package eu.delving.crm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RDF Graph
 *
 * @author Gerald de Jong <gerald@delving.eu>
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
