package eu.delving.groovy;

import org.junit.Test;

import javax.script.CompiledScript;

import static org.junit.Assert.*;

public class GroovyCodeResourceTest {

    private static final String CODE = "return true";

    @Test
    public void testCreateMappingScript() throws Exception {
        runSingle();
    }

    private void runSingle() throws Exception {
        GroovyCodeResource groovyCodeResource = new GroovyCodeResource(this.getClass().getClassLoader());
        CompiledScript script = groovyCodeResource.createMappingScript(CODE);
        assertNotNull(script);
        Object result = script.eval();
        assertNotNull(result);
    }

    @Test
    public void testCreateThousandsOfScripts() throws Exception {
        for (int i = 0; i < 2000; i++ ) {
            runSingle();
        }
    }



}
