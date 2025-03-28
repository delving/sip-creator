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

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static eu.delving.metadata.StringUtil.csvLineParse;
import static org.junit.Assert.assertEquals;

public class TestStringUtil {

    @Test
    public void samples() {
        assertEquals("simple", expect("gumby", "pokey"), csvLineParse("gumby, pokey"));
        assertEquals("spaces", expect("gumby", "pokey"), csvLineParse(" gumby , pokey "));
        assertEquals("numbers", expect("1", "2", "3"), csvLineParse("1,2,3"));
        assertEquals("quotes", expect("1", " 2", "3"), csvLineParse("1,\" 2\",3"));
        assertEquals("internal quotes", expect("1", " 2", "they say \"3\""), csvLineParse("1,\" 2\", \"they say \"\"3\"\"\""));
    }

    private static List<String> expect(String... values) {
        return Arrays.asList(values);
    }
}
