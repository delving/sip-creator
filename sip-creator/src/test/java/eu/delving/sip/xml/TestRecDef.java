package eu.delving.sip.xml;

import eu.delving.metadata.RecDef;
import org.junit.Test;

import java.io.IOException;

/**
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class TestRecDef {

    @Test
    public void readIn() throws IOException {
        RecDef recDef = RecDef.read(getClass().getResource("/lido-recdef.xml").openStream());
        recDef.resolve();
        StringBuilder out = new StringBuilder();
        recDef.print(out);
        System.out.println("gives\n" + out);
        System.out.println("Paths:\n" + recDef);
    }


}
