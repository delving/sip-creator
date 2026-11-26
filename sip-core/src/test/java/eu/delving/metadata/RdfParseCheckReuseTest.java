package eu.delving.metadata;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RIOT;
import org.apache.jena.riot.WebContent;
import org.apache.jena.riot.lang.ReaderRIOTRDFXML;
import org.apache.jena.riot.system.ErrorHandlerFactory;
import org.apache.jena.riot.system.StreamRDFLib;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MappingResult#hasRDFError} keeps one RDF/XML reader per thread. It
 * must accept and reject exactly what a fresh {@code RDFDataMgr.read} per
 * record did, with the same message, whatever was parsed before it.
 */
public class RdfParseCheckReuseTest {

    private static final String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    private static final String HEAD = "<rdf:RDF xmlns:rdf=\"" + RDF + "\" xmlns:ex=\"http://example.org/ns#\""
            + " xmlns:xsd=\"http://www.w3.org/2001/XMLSchema#\">\n";
    private static final String TAIL = "</rdf:RDF>\n";

    private static String doc(String body) {
        return HEAD + body + TAIL;
    }

    /** Name to record; the expected outcome is whatever the fresh parser says. */
    private static Map<String, String> corpus() {
        Map<String, String> c = new LinkedHashMap<>();
        // accepted
        c.put("plain", doc("<rdf:Description rdf:about=\"http://example.org/a\"><ex:p>v</ex:p></rdf:Description>\n"));
        c.put("xml decl utf-8", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + doc("<ex:T rdf:about=\"http://example.org/a\"><ex:p xml:lang=\"nl\">één</ex:p></ex:T>\n"));
        c.put("xml decl no encoding", "<?xml version=\"1.0\"?>\n"
                + doc("<ex:T rdf:about=\"http://example.org/a\"><ex:p>x</ex:p></ex:T>\n"));
        c.put("non-ascii", doc("<rdf:Description rdf:about=\"http://example.org/é\"><ex:p>中文 ü ß</ex:p></rdf:Description>\n"));
        c.put("nested blank", doc("<rdf:Description rdf:about=\"http://example.org/a\"><ex:p>"
                + "<rdf:Description><ex:q>x</ex:q></rdf:Description></ex:p></rdf:Description>\n"));
        c.put("nodeID", doc("<rdf:Description rdf:about=\"http://example.org/a\"><ex:p rdf:nodeID=\"n1\"/></rdf:Description>\n"
                + "<rdf:Description rdf:nodeID=\"n1\"><ex:q>x</ex:q></rdf:Description>\n"));
        c.put("parseType Resource", doc("<rdf:Description rdf:about=\"http://example.org/a\">"
                + "<ex:p rdf:parseType=\"Resource\"><ex:q>x</ex:q></ex:p></rdf:Description>\n"));
        c.put("parseType Literal", doc("<rdf:Description rdf:about=\"http://example.org/a\">"
                + "<ex:p rdf:parseType=\"Literal\"><b xmlns=\"http://www.w3.org/1999/xhtml\">x</b></ex:p></rdf:Description>\n"));
        c.put("parseType Collection", doc("<rdf:Description rdf:about=\"http://example.org/a\">"
                + "<ex:p rdf:parseType=\"Collection\"><rdf:Description rdf:about=\"http://example.org/b\"/></ex:p></rdf:Description>\n"));
        c.put("typed literal", doc("<rdf:Description rdf:about=\"http://example.org/a\">"
                + "<ex:p rdf:datatype=\"http://www.w3.org/2001/XMLSchema#integer\">42</ex:p></rdf:Description>\n"));
        c.put("rdf:ID with xml:base", doc("<rdf:Description xml:base=\"http://example.org/doc\" rdf:ID=\"x\">"
                + "<ex:p>v</ex:p></rdf:Description>\n"));
        c.put("relative about, no base", doc("<rdf:Description rdf:about=\"rel/a\"><ex:p>v</ex:p></rdf:Description>\n"));
        c.put("blank about", doc("<rdf:Description rdf:about=\"_:bad id\"><ex:p>v</ex:p></rdf:Description>\n"));
        c.put("container li", doc("<rdf:Seq rdf:about=\"http://example.org/s\"><rdf:li>a</rdf:li><rdf:li>b</rdf:li></rdf:Seq>\n"));
        c.put("prefix declared", "<rdf:RDF xmlns:rdf=\"" + RDF + "\" xmlns:foo=\"http://example.org/foo#\">"
                + "<foo:T rdf:about=\"http://example.org/a\"><foo:p>v</foo:p></foo:T></rdf:RDF>\n");
        c.put("bad typed literal", doc("<rdf:Description rdf:about=\"http://example.org/a\">"
                + "<ex:p rdf:datatype=\"http://www.w3.org/2001/XMLSchema#integer\">abc</ex:p></rdf:Description>\n"));
        // rejected (or at least, whatever the old path says about them)
        c.put("malformed: unclosed", doc("<rdf:Description rdf:about=\"http://example.org/a\"><ex:p>v</rdf:Description>\n"));
        c.put("malformed: truncated", HEAD + "<rdf:Description rdf:about=\"http://example.org/a\"><ex:p>v</ex:p>");
        c.put("malformed: empty", "");
        c.put("malformed: text only", "not xml at all");
        c.put("malformed: bad entity", doc("<rdf:Description rdf:about=\"http://example.org/a\"><ex:p>&nope;</ex:p></rdf:Description>\n"));
        c.put("bad IRI: space", doc("<rdf:Description rdf:about=\"http://example.org/a b\"><ex:p>v</ex:p></rdf:Description>\n"));
        c.put("bad IRI: resource brackets", doc("<rdf:Description rdf:about=\"http://example.org/a\">"
                + "<ex:p rdf:resource=\"http://example.org/&lt;x&gt;\"/></rdf:Description>\n"));
        c.put("bad IRI: bad scheme", doc("<rdf:Description rdf:about=\"ht tp://x\"><ex:p>v</ex:p></rdf:Description>\n"));
        c.put("undeclared prefix", doc("<rdf:Description rdf:about=\"http://example.org/a\"><foo:p>v</foo:p></rdf:Description>\n"));
        c.put("E201 two children", doc("<rdf:Description rdf:about=\"http://example.org/a\"><ex:p>"
                + "<rdf:Description rdf:about=\"http://example.org/b\"/><rdf:Description rdf:about=\"http://example.org/c\"/>"
                + "</ex:p></rdf:Description>\n"));
        c.put("resource and content", doc("<rdf:Description rdf:about=\"http://example.org/a\">"
                + "<ex:p rdf:resource=\"http://example.org/b\">text</ex:p></rdf:Description>\n"));
        c.put("about and nodeID", doc("<rdf:Description rdf:about=\"http://example.org/a\" rdf:nodeID=\"n\">"
                + "<ex:p>v</ex:p></rdf:Description>\n"));
        c.put("aboutEach", doc("<rdf:Description rdf:aboutEach=\"#x\"><ex:p>v</ex:p></rdf:Description>\n"));
        c.put("rdf:li as node", doc("<rdf:li rdf:about=\"http://example.org/a\"/>\n"));
        c.put("rdf:RDF nested", doc("<rdf:Description rdf:about=\"http://example.org/a\"><rdf:RDF/></rdf:Description>\n"));
        c.put("bad nodeID", doc("<rdf:Description rdf:nodeID=\"1 2\"><ex:p>v</ex:p></rdf:Description>\n"));
        c.put("bad lang", doc("<rdf:Description rdf:about=\"http://example.org/a\"><ex:p xml:lang=\"12 34\">v</ex:p></rdf:Description>\n"));
        c.put("bad parseType", doc("<rdf:Description rdf:about=\"http://example.org/a\">"
                + "<ex:p rdf:parseType=\"Bogus\"><ex:q>x</ex:q></ex:p></rdf:Description>\n"));
        c.put("duplicate rdf:ID", doc("<rdf:Description xml:base=\"http://example.org/doc\" rdf:ID=\"x\"/>\n"
                + "<rdf:Description xml:base=\"http://example.org/doc\" rdf:ID=\"x\"/>\n"));
        c.put("rdf:type as attr value bad", doc("<rdf:Description rdf:about=\"http://example.org/a\" rdf:type=\"not an iri\"/>\n"));
        c.put("non-rdf root", "<ex:T xmlns:ex=\"http://example.org/ns#\"><ex:p>v</ex:p></ex:T>\n");
        return c;
    }

    /**
     * hasRDFError as it was before the reader was kept per thread: a new
     * RIOT reader per call through RDFDataMgr, same message formatting.
     */
    private static String freshCheck(String rdf) {
        try {
            Model model = ModelFactory.createDefaultModel();
            RDFDataMgr.read(model, new ByteArrayInputStream(rdf.getBytes(StandardCharsets.UTF_8)), Lang.RDFXML);
        } catch (Exception e) {
            return MappingResult.formatRDFError(e, rdf);
        }
        return "";
    }

    @Test
    public void corpusHasBothOutcomes() {
        long rejected = corpus().values().stream().filter(rdf -> !freshCheck(rdf).isEmpty()).count();
        long accepted = corpus().size() - rejected;
        assertTrue(rejected >= 15, "too few rejected cases: " + rejected);
        assertTrue(accepted >= 10, "too few accepted cases: " + accepted);
    }

    @Test
    public void sameOutcomeAndMessageAsAFreshReader() {
        for (Map.Entry<String, String> e : corpus().entrySet()) {
            assertEquals(freshCheck(e.getValue()), MappingResult.hasRDFError(e.getValue()), e.getKey());
        }
    }

    @Test
    public void orderDoesNotMatterOnOneThread() {
        Map<String, String> corpus = corpus();
        Map<String, String> expectedFirst = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : corpus.entrySet()) {
            expectedFirst.put(e.getKey(), freshCheck(e.getValue()));
        }
        List<String> names = new ArrayList<>(corpus.keySet());
        Random random = new Random(20261126L);
        for (int round = 0; round < 20; round++) {
            Collections.shuffle(names, random);
            for (String name : names) {
                assertEquals(expectedFirst.get(name), MappingResult.hasRDFError(corpus.get(name)),
                        "round " + round + ": " + name);
            }
        }
    }

    @Test
    public void everyPairOnOneThread() {
        // Each record checked right after each other record, on one thread:
        // a failure mid-parse, a prefix, an xml:base, an rdf:ID or a nodeID
        // of the first must not change the outcome of the second.
        Map<String, String> corpus = corpus();
        Map<String, String> alone = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : corpus.entrySet()) {
            alone.put(e.getKey(), freshCheck(e.getValue()));
        }
        for (Map.Entry<String, String> first : corpus.entrySet()) {
            for (Map.Entry<String, String> second : corpus.entrySet()) {
                MappingResult.hasRDFError(first.getValue());
                assertEquals(alone.get(second.getKey()), MappingResult.hasRDFError(second.getValue()),
                        first.getKey() + " then " + second.getKey());
            }
        }
    }

    @Test
    public void declaredPrefixDoesNotLeakIntoTheNextRecord() {
        String declares = corpus().get("prefix declared");
        String undeclared = doc("<rdf:Description rdf:about=\"http://example.org/a\"><foo:p>v</foo:p></rdf:Description>\n");
        assertEquals("", MappingResult.hasRDFError(declares));
        assertFalse(MappingResult.hasRDFError(undeclared).isEmpty());
        assertEquals("", MappingResult.hasRDFError(declares));
        assertFalse(MappingResult.hasRDFError(undeclared).isEmpty());
    }

    @Test
    public void rdfIdDoesNotCollideAcrossRecords() {
        String once = corpus().get("rdf:ID with xml:base");
        assertEquals(freshCheck(once), MappingResult.hasRDFError(once));
        assertEquals(freshCheck(once), MappingResult.hasRDFError(once));
    }

    @Test
    public void reusedReaderStartsEveryRecordFresh() {
        // The property the reuse relies on, checked on Jena itself: one
        // ReaderRIOTRDFXML, two loads. The same rdf:nodeID in both documents
        // gives two different blank nodes, an xml:base or prefix of the first
        // does not resolve anything in the second, and a load after a failed
        // one gives the same triples as a fresh reader.
        ReaderRIOTRDFXML reader = new ReaderRIOTRDFXML(ErrorHandlerFactory.getDefaultErrorHandler());
        String withNodeId = corpus().get("nodeID");

        Graph g1 = load(reader, withNodeId);
        assertThrows(Exception.class, () -> load(reader, corpus().get("malformed: unclosed")));
        assertThrows(Exception.class, () -> load(reader, corpus().get("undeclared prefix")));
        Graph g2 = load(reader, withNodeId);

        assertTrue(g1.isIsomorphicWith(fresh(withNodeId)));
        assertTrue(g2.isIsomorphicWith(fresh(withNodeId)));
        Set<Node> b1 = blankNodes(g1);
        Set<Node> b2 = blankNodes(g2);
        assertEquals(1, b1.size());
        assertEquals(1, b2.size());
        assertTrue(Collections.disjoint(b1, b2), "nodeID scope leaked across loads: " + b1 + " " + b2);

        Graph withBase = load(reader, corpus().get("rdf:ID with xml:base"));
        Graph relative = load(reader, corpus().get("relative about, no base"));
        assertTrue(withBase.isIsomorphicWith(fresh(corpus().get("rdf:ID with xml:base"))));
        assertTrue(relative.isIsomorphicWith(fresh(corpus().get("relative about, no base"))));
    }

    private static Graph load(ReaderRIOTRDFXML reader, String rdf) {
        Graph graph = GraphFactory.createDefaultGraph();
        reader.read(new ByteArrayInputStream(rdf.getBytes(StandardCharsets.UTF_8)), null, WebContent.ctRDFXML,
                StreamRDFLib.graph(graph), RIOT.getContext().copy());
        return graph;
    }

    private static Graph fresh(String rdf) {
        Graph graph = GraphFactory.createDefaultGraph();
        RDFDataMgr.read(graph, new ByteArrayInputStream(rdf.getBytes(StandardCharsets.UTF_8)), Lang.RDFXML);
        return graph;
    }

    private static Set<Node> blankNodes(Graph graph) {
        Set<Node> nodes = new HashSet<>();
        for (Triple t : graph.find().toList()) {
            if (t.getSubject().isBlank()) nodes.add(t.getSubject());
            if (t.getObject().isBlank()) nodes.add(t.getObject());
        }
        return nodes;
    }
}
