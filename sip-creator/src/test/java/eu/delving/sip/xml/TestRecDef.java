package eu.delving.sip.xml;

import eu.delving.metadata.RecDef;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

/**
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class TestRecDef {

    private static final File FILE = new File("sip-creator/src/test/resources/lido-recdef.xml");

    @Test
    public void readIn() throws FileNotFoundException {
        RecDef recDef = RecDef.read(new FileInputStream(FILE));
        recDef.resolve();
        StringBuilder out = new StringBuilder();
        recDef.print(out);
        System.out.println("gives\n" + out);
        System.out.println("Paths:\n" + recDef);
    }


}
