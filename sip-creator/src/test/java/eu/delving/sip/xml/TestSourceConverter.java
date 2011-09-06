package eu.delving.sip.xml;

import eu.delving.metadata.Path;
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
    private static String [] INPUT = {
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
            "<b:shh>quiet</b:shh>",
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
    public void runThrough() throws IOException, XMLStreamException {
        String inputString = StringUtils.join(INPUT, "\n");
        InputStream in = new ByteArrayInputStream(inputString.getBytes("UTF-8"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        converter.parse(in, out);
        String outputString = out.toString("UTF-8");
        String [] lines = outputString.split("\n");
        for (String line : lines) {
            System.out.println("\""+line.replaceAll("\"", "\\\\\"")+"\",");
        }
        String [] expect = {
                "<?xml version='1.0' encoding='UTF-8'?>",
                "<delving-sip-source xmlns:a=\"http://a\" xmlns:b=\"http://b\" xmlns:c=\"http://c\">",
                "<input id=\"03030030\">",
                "<a:boo>scary</a:boo>",
                "<b:shh>quiet</b:shh>",
                "<c:boring>zzzz</c:boring>",
                "</input>",
                "<input id=\"0404040404\">",
                "<a:boo>very scary</a:boo>",
                "<b:shh>deathly quiet</b:shh>",
                "<c:long>this is very much a multi-line field it even contains strange spaces</c:long>",
                "</input>",
                "</delving-sip-source>",
        };
        Assert.assertArrayEquals("Unexpected output", expect, lines);
    }

}
