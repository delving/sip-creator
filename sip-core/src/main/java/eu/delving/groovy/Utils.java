package eu.delving.groovy;

import eu.delving.metadata.OptList;
import eu.delving.metadata.RecDef;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.script.SimpleBindings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Utils {

    public static class ScriptBinding {
        public Object _optLookup;
        public Object output;
        public Object input;
        public Object _facts;
    }

    private Utils() { // for static use only
    }

    public static Node stripEmptyElements(Object nodeObject) {
        Node node = (Node) nodeObject;
        stripEmpty(node);
        return node;
    }

    public static List<GroovyNode> initFactsNode(final Map<String, String> facts){
        GroovyNode factsNode = new GroovyNode(null, "facts");
        for (Map.Entry<String, String> entry : facts.entrySet()) {
            new GroovyNode(factsNode, entry.getKey(), entry.getValue());
        }
        return Collections.singletonList(factsNode);
    }

    public static SimpleBindings bindingsFor(final Map<String, String> facts,
                                             final RecDef recDef, GroovyNode rootNode, Map<String, Map<String, OptList.Opt>> valueOptLookup){
        final SimpleBindings bindings = new SimpleBindings();
        final ScriptBinding ourScriptIO = new ScriptBinding();
        bindings.put("WORLD", ourScriptIO);

        ourScriptIO._facts = initFactsNode(facts);
        ourScriptIO._optLookup = valueOptLookup;
        ourScriptIO.output = DOMBuilder.createFor(recDef);
        ourScriptIO.input = Collections.singletonList(rootNode);
        return bindings;
    }

    private static void stripEmpty(Node node) {
        NodeList kids = node.getChildNodes();
        List<Node> dead = new ArrayList<>();
        for (int walk = 0; walk < kids.getLength(); walk++) {
            Node kid = kids.item(walk);
            switch (kid.getNodeType()) {
                case Node.ATTRIBUTE_NODE:
                    break;
                case Node.TEXT_NODE:
                case Node.CDATA_SECTION_NODE:
                    if (kid.getTextContent().trim().isEmpty()) dead.add(kid);
                    break;
                case Node.ELEMENT_NODE:
                    stripEmpty(kid);
                    if (!(kid.hasChildNodes() || kid.hasAttributes())) dead.add(kid);
                    break;
                default:
                    throw new RuntimeException("Node type not implemented: " + kid.getNodeType());
            }
        }
        for (Node kill : dead) node.removeChild(kill);
    }

    public static String stripNonPrinting(String xmlString) {
        // Replace non-printable characters with an empty string
        return xmlString.replaceAll("[^\\x20-\\x7e]", "");
    }
}
