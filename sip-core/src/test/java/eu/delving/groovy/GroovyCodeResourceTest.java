package eu.delving.groovy;

import groovy.lang.Script;
import org.junit.Test;

import static org.junit.Assert.*;

public class GroovyCodeResourceTest {

    @Test
    public void createMappingScript() throws Exception {
        GroovyCodeResource groovyCodeResource = new GroovyCodeResource(this.getClass().getClassLoader());
        Script script = groovyCodeResource.createMappingScript("println 'hello world'");
        assertNotNull(script);
    }

}
