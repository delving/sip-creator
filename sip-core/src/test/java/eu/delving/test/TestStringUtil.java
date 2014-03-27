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

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static eu.delving.metadata.StringUtil.csvDelimiter;
import static eu.delving.metadata.StringUtil.csvLineParse;
import static junit.framework.Assert.assertEquals;

/**
 * Make sure the String Util
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestStringUtil {

    @Test
    public void samples() {
        assertEquals("simple", expect("gumby", "pokey"), csvLineParse("gumby, pokey", ','));
        assertEquals("spaces", expect("gumby", "pokey"), csvLineParse(" gumby , pokey ", ','));
        assertEquals("numbers", expect("1", "2", "3"), csvLineParse("1,2,3", ','));
        assertEquals("quotes", expect("1", " 2", "3"), csvLineParse("1,\" 2\",3", ','));
        assertEquals("internal quotes", expect("1", " 2", "they say \"3\""), csvLineParse("1,\" 2\", \"they say \"\"3\"\"\"", ','));
        String semi = "csv;delimited;with;semicolons?;idiots!";
        assertEquals("semi", expect("csv", "delimited", "with", "semicolons?", "idiots!"), csvLineParse(semi, csvDelimiter(semi)));
    }

    private static List<String> expect(String... values) {
        return Arrays.asList(values);
    }
}
