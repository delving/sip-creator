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

package eu.delving.sip.xml;

import eu.delving.metadata.Path;
import eu.delving.metadata.UniquenessException;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Make sure the source converter works as expected
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class TestSourceConverter {
    private static String[] INPUT = {
            "<?xml version=\"1.0\"?>",
            "<the-root",
            " xmlns:a=\"http://a\"",
            " xmlns:b=\"http://b\"",
            " xmlns:c=\"http://c\"",
            ">",
            "<sub-root xmlns:c=\"http://c\">", // repeated
            "<we-are-in-record>",
            "<a:boo>scary</a:boo>",
            "<a:unique>03030030</a:unique>",
            "<b:shh silent=\"very\">quiet</b:shh>",
            "<c:boring>zzzz</c:boring>",
            "</we-are-in-record>",
            "               <we-are-in-record>           ",
            "<a:boo>very scary</a:boo>",
            "<b:shh>deathly quiet",
            "</b:shh>",
            "<a:unique>0404040404</a:unique>",
            "<c:long>",
            "this is very much ",
            "a multi-line field ",
            "it even contains       strange spaces",
            "</c:long>",
            "</we-are-in-record>",
            "</sub-root>",
            "</the-root>",
    };
    private static Path ROOT = new Path("/the-root/sub-root/we-are-in-record");
    private static Path UNIQ = new Path("/the-root/sub-root/we-are-in-record/a:unique");
    private SourceConverter converter = new SourceConverter(ROOT, 2, UNIQ);

    @Test
    public void runThrough() throws IOException, XMLStreamException, UniquenessException {
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
                "<input>",
                "<_id>03030030</_id>",
                "<a:boo>scary</a:boo>",
                "<a:unique>03030030</a:unique>",
                "<b:shh silent=\"very\">quiet</b:shh>",
                "<c:boring>zzzz</c:boring>",
                "</input>",
                "<input>",
                "<_id>0404040404</_id>",
                "<a:boo>very scary</a:boo>",
                "<b:shh>deathly quiet</b:shh>",
                "<a:unique>0404040404</a:unique>",
                "<c:long>this is very much a multi-line field it even contains strange spaces</c:long>",
                "</input>",
                "</delving-sip-source>",
        };
        Assert.assertArrayEquals("Unexpected output", expect, lines);
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
        try {
            converter.parse(in, out);
        }
        catch (UniquenessException e) {
            System.out.printf("%s%n", e.getMessage());
        }
    }
}
