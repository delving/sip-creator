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



}
