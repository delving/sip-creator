/*
 * Copyright 2011, 2012 Delving BV
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.test;

import eu.delving.metadata.Path;
import eu.delving.metadata.Tag;
import junit.framework.Assert;
import org.junit.Test;

import static junit.framework.Assert.*;

/**
 * Make sure the path is working right
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestPath {

    @Test
    public void lineage() {
        Path descendent = Path.create("/one/two/three/four");
        assertTrue(descendent.equals(Path.create("/one/two/three/four")));
        assertFalse(descendent.equals(Path.create("/one")));
        assertEquals(4, descendent.size());
        Path ancestor = Path.create("/one/two");
        Assert.assertTrue(ancestor.isAncestorOf(descendent));
        assertFalse(ancestor.isAncestorOf(ancestor));
        assertFalse(descendent.isAncestorOf(ancestor));
        assertEquals(ancestor, descendent.takeFirst(ancestor.size()));
        assertEquals(Path.create("/two/three/four"), descendent.extendAncestor(ancestor));
    }

    @Test
    public void prefix() {
        Path opt1 = Path.create("/but:one/but:two");
        Path opt2 = Path.create("/one/two");
        Path prefixed = opt2.withDefaultPrefix("but");
        assertTrue(opt1.equals(prefixed));
    }

    @Test
    public void removeRoot() {
        Path opt1 = Path.create("/gumby/pokey/forever/together");
        Path opt2 = Path.create("/pokey/forever/together");
        Path removed = opt1.withRootRemoved();
        assertEquals(opt2, removed);
    }

    @Test
    public void fetch() {
        Path path = Path.create("/gumby/pokey/forever/together");
        assertEquals(path.getTag(0), Tag.create("gumby"));
        assertEquals(path.getTag(1), Tag.create("pokey"));
        assertEquals(path.getTag(2), Tag.create("forever"));
        assertEquals(path.getTag(3), Tag.create("together"));
        assertNull(path.getTag(4));
    }

    @Test
    public void opts() {
        Path opt1 = Path.create("/one/two[the first]/gumby");
        Path opt2 = Path.create("/one/two[the / second]/gumby");
        assertFalse(opt1.equals(opt2));
        Path opt3 = Path.create("/one/two/gumby");
        assertTrue(opt2.compareTo(opt3) > 0);
    }

    @Test
    public void compare() {
        Path optA = Path.create("/one/two1/gumby");
        Path optB = Path.create("/one/two2/gumby");
        Path optC = Path.create("/one/two2");
        assertTrue(optA.compareTo(optB) < 0);
        assertTrue(optB.compareTo(optA) > 0);
        assertTrue(optC.compareTo(optB) < 0);
        assertTrue(optB.compareTo(optC) > 0);
        Path optD = Path.create("/one/two2/with/much/more");
        assertTrue(optC.compareTo(optD) < 0);
        assertTrue(optD.compareTo(optC) > 0);
    }

    @Test
    public void extend() {
        Path a = Path.create("/one/two");
        Path b = Path.create("three/four");
        assertTrue(a.descendant(b).equals(Path.create("/one/two/three/four")));
    }
}
