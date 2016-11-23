package eu.delving.groovy;

import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.junit.Test;

public class GroovyShellTest {

    private static final String CODE = "return true";

    @Test
    public void testManyShells(){
        for (int i = 0; i < 10000; i++) {
            GroovyShell groovyShell = new GroovyShell();
            Script script = groovyShell.parse(CODE.intern());
        }
    }
}
