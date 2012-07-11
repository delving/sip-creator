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

package eu.delving.sip;

import eu.delving.metadata.Path;
import eu.delving.sip.xml.SourceConverter;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;

/**
 * Make sure the source converter works as expected
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestSourceConverter {
    private static String[] INPUT = {
            "<?xml version=\"1.0\"?>",
            "<the-root",
            " xmlns=\"http://rootns\"",
            " xmlns:a=\"http://a\"",
            " xmlns:b=\"http://b\"",
            " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"", // will be stripped
            ">",
            "<sub-root xmlns:c=\"http://c\" xmlns=\"rootns\">", // repeated, both of them
            "<we-are-in-record>",
            "<a:boo>&amp;three > two &gt; 1</a:boo>",
            "<a:wrapper>",
            "<a:middle>",
            "<a:inside>yolk</a:inside>",
            "</a:middle>",
            "</a:wrapper>",
            "<a:unique " + // the rest will be stripped
                    "xsi:schemaLocation=\"http://www.blabla.org/ http://www.blabla.org/boobies.xsd\">03030030</a:unique>",
            "<b:shh silent=\"very\">quiet</b:shh>",
            "</we-are-in-record>",
            "               <we-are-in-record xmlns:c=\"http://c\">           ",
            "<a:boo>very scary</a:boo>",
            "<b:shh>deathly quiet",
            "</b:shh>",
            "<a:unique>0404040404</a:unique>",
            "<c:long lang=\"stupid\">",
            "this is very much ",
            "a multi-line field ",
            "it even contains       strange spaces",
            "</c:long>",
            "</we-are-in-record>",
            "<we-are-in-record>",
            "<a:boo>this one has no unique identifier</a:boo>",
            "<b:shh>so it will simply be discarded</b:shh>",
            "</we-are-in-record>",
            "</sub-root>",
            "</the-root>",
    };
    private static Path ROOT = Path.create("/the-root/sub-root/we-are-in-record");
    private static Path UNIQ = Path.create("/the-root/sub-root/we-are-in-record/a:unique");
    private static Map<String, String> namespaces = new TreeMap<String, String>();
    private static final String UNIQUE_CONVERTER = ".(.*):::before:$1:after";

    static {
        namespaces.put("", "http://rootns");
        namespaces.put("a", "http://a");
        namespaces.put("b", "http://b");
        namespaces.put("c", "http://c");
    }

    private SourceConverter converter = new SourceConverter(null, ROOT, 2, UNIQ, 100, UNIQUE_CONVERTER, namespaces);

    @Test
    public void runThrough() throws IOException, XMLStreamException {
        String inputString = StringUtils.join(INPUT, "\n");
        InputStream in = new ByteArrayInputStream(inputString.getBytes("UTF-8"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        converter.parse(in, out);
        String outputString = out.toString("UTF-8");
        String[] lines = outputString.split("\n");
        for (String line : lines) {
            System.out.println("\"" + line.replaceAll("\"", "\\\\\"") + "\",");
        }
        String[] expect = {
                "<?xml version='1.0' encoding='UTF-8'?>",
                "<delving-sip-source xmlns:a=\"http://a\" xmlns:b=\"http://b\" xmlns:c=\"http://c\">",
                "<input id=\"before_3030030_after\">",
                "<a:boo>&amp;three > two > 1</a:boo>",
                "<a:wrapper>",
                "<a:middle>",
                "<a:inside>yolk</a:inside>",
                "</a:middle>",
                "</a:wrapper>",
                "<a:unique>03030030</a:unique>",
                "<b:shh silent=\"very\">quiet</b:shh>",
                "</input>",
                "<input id=\"before_404040404_after\">",
                "<a:boo>very scary</a:boo>",
                "<b:shh>deathly quiet</b:shh>",
                "<a:unique>0404040404</a:unique>",
                "<c:long lang=\"stupid\">this is very much</c:long>",
                "<c:long lang=\"stupid\">a multi-line field</c:long>",
                "<c:long lang=\"stupid\">it even contains strange spaces</c:long>",
                "</input>",
                "</delving-sip-source>",
        };
        Assert.assertEquals("Unexpected output", StringUtils.join(expect, '\n'), StringUtils.join(lines, '\n'));
    }

    @Test
    public void testUniqueness() throws Exception {
        String input = "<?xml version='1.0' encoding='UTF-8'?>" +
                "<the-root xmlns:a='http://a'>" +
                "   <sub-root>" +
                "       <we-are-in-record>" +
                "           <a:unique>abc</a:unique>" +
                "       </we-are-in-record>" +
                "   </sub-root>" +
                "   <sub-root>" +
                "       <we-are-in-record>" +
                "           <a:unique>abc</a:unique>" +
                "       </we-are-in-record>" +
                "   </sub-root>" +
                "</the-root>";
        InputStream in = new ByteArrayInputStream(input.getBytes("UTF-8"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        converter.parse(in, out);
        Assert.assertEquals("Too many lines!", 6, new String(out.toByteArray()).split("\n").length);
    }
}
