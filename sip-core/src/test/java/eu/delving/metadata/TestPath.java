/*
 * Copyright 2011 DELVING BV
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

package eu.delving.metadata;

import junit.framework.Assert;
import org.junit.Test;

/**
 * Make sure the path is working right
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestPath {

    @Test
    public void lineage() {
        Path descendent = Path.create("/one/two/three/four");
        Path ancestor = Path.create("/one/two");
        Assert.assertTrue(ancestor.isAncestorOf(descendent));
        Assert.assertFalse(ancestor.isAncestorOf(ancestor));
        Assert.assertFalse(descendent.isAncestorOf(ancestor));
        Assert.assertEquals(ancestor, descendent.chop(ancestor.size()));
        Assert.assertEquals(Path.create("/three/four"), descendent.minusAncestor(ancestor));
    }
}
