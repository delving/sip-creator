package eu.delving.groovy;

import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class AbstractMappingRunner implements MappingRunner{

    protected RecMapping recMapping;
    protected String code;

    public AbstractMappingRunner(RecMapping recMapping, String code) {
        this.recMapping = recMapping;
        this.code = code;
    }

    protected List<GroovyNode> initFactsNode(final Map<String, String> facts){
        GroovyNode factsNode = new GroovyNode(null, "facts");
        for (Map.Entry<String, String> entry : facts.entrySet()) {
            new GroovyNode(factsNode, entry.getKey(), entry.getValue());
        }
        return Collections.singletonList(factsNode);
    }
    @Override
    public RecDefTree getRecDefTree() {
        return recMapping.getRecDefTree();
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public abstract Node runMapping(MetadataRecord metadataRecord) throws MappingException;

    protected Node stripEmptyElements(Object nodeObject) {
        Node node = (Node) nodeObject;
        stripEmpty(node);
        return node;
    }

    private void stripEmpty(Node node) {
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

    protected class ScriptBinding {
        public Object _optLookup;
        public Object output;
        public Object input;
        public Object _facts;
    }

}
