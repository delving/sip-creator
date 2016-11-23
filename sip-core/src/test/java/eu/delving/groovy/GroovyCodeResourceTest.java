package eu.delving.groovy;

import groovy.lang.Script;
import org.junit.Test;

import static org.junit.Assert.*;

public class GroovyCodeResourceTest {

    private static final String CODE = "return true";

    @Test
    public void testCreateMappingScript() throws Exception {
        runSingle();
    }

    private void runSingle() {
        GroovyCodeResource groovyCodeResource = new GroovyCodeResource(this.getClass().getClassLoader());
        Script script = groovyCodeResource.createMappingScript(CODE);
        assertNotNull(script);
    }

    @Test
    public void testCreateThousandsOfScripts(){
        for (int i = 0; i < 2000; i++ ) {
            runSingle();
        }
    }



}
