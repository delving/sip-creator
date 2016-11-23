package eu.delving.groovy;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.jsr223.GroovyScriptEngineImpl;
import org.junit.Test;

public class GroovyShellTest {

    private static final String CODE = "return true";

    @Test
    public void testManyShells() throws Exception{
        for (int i = 0; i < 10000; i++) {
            GroovyScriptEngineImpl scriptEngine = new GroovyScriptEngineImpl(new GroovyClassLoader());
            scriptEngine.compile(CODE);
        }
    }
}
