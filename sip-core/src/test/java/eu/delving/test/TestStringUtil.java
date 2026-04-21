/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.test;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static eu.delving.metadata.StringUtil.csvLineParse;
import static eu.delving.metadata.StringUtil.sanitizeId;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestStringUtil {

    @Test
    public void samples() {
        assertEquals(expect("gumby", "pokey"), csvLineParse("gumby, pokey"), "simple");
        assertEquals(expect("gumby", "pokey"), csvLineParse(" gumby , pokey "), "spaces");
        assertEquals(expect("1", "2", "3"), csvLineParse("1,2,3"), "numbers");
        assertEquals(expect("1", " 2", "3"), csvLineParse("1,\" 2\",3"), "quotes");
        assertEquals(expect("1", " 2", "they say \"3\""), csvLineParse("1,\" 2\", \"they say \"\"3\"\"\""), "internal quotes");
    }

    @Test
    public void sanitizeIdCollapsesDashes() {
        assertEquals("XN1012-1", sanitizeId("XN1012 - 1"), "spaces around dash");
        assertEquals("a-b", sanitizeId("a_ b"), "underscore and space");
        assertEquals("a-b", sanitizeId("a--b"), "existing double dash");
        assertEquals("foo-bar", sanitizeId("foo/ bar"), "slash collapsed");
        assertEquals("clean-id", sanitizeId("clean-id"), "no illegal chars");
    }

    private static List<String> expect(String... values) {
        return Arrays.asList(values);
    }
}
