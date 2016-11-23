package eu.delving.groovy;

import eu.delving.metadata.RecDefTree;
import org.w3c.dom.Node;

public interface MappingRunner {
    RecDefTree getRecDefTree();

    String getCode();

    Node runMapping(MetadataRecord metadataRecord) throws MappingException;
}
