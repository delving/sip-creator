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
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefModel;
import eu.delving.metadata.RecMapping;
import eu.delving.schema.SchemaVersion;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.DataSetState;
import eu.delving.sip.files.ReportFile;
import eu.delving.sip.files.ReportWriter;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.xml.SourceConverter;
import eu.delving.stats.Stats;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import javax.xml.stream.XMLStreamException;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static eu.delving.sip.files.Storage.RECORD_COUNT;
import static eu.delving.sip.files.Storage.RECORD_ROOT_PATH;
import static eu.delving.sip.files.Storage.UNIQUE_ELEMENT_PATH;
import static eu.delving.sip.files.Storage.UNIQUE_VALUE_CONVERTER;

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
            "in between text",
            "and a second annoying line",
            "<a:middle>",
            "<a:inside>yolk</a:inside>",
            "</a:middle>",
            "</a:wrapper>",
            "<a:unique " + // the rest will be stripped
                    "xsi:schemaLocation=\"http://www.blabla.org/ http://www.blabla.org/boobies.xsd\">03030030</a:unique>",
            "<b:shh silent=\"very\">quiet</b:shh>",
            "</we-are-in-record>",
            "               <we-are-in-record xmlns:c=\"http://c\">           ",
            "<a:boo>http://somewhere.com/anurl/path/gumby.jpg</a:boo>",
            "<b:shh>deathly quiet",
            "</b:shh>",
            "<a:unique>0404040404</a:unique>",
            "<empty></empty>",
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

    private SourceConverter converter = new SourceConverter(new MockDataSet(), null);
    //ROOT, 2, UNIQ, 100, UNIQUE_CONVERTER, namespaces

    @Before
    public void setSourceConverter() throws StorageException {
        converter.setProgressListener(new MockProgressListener());
        converter.interpretHints();
    }

    @Test
    public void runThrough() throws IOException, XMLStreamException, StorageException {
        String inputString = StringUtils.join(INPUT, "\n");
        InputStream in = new ByteArrayInputStream(inputString.getBytes("UTF-8"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        converter.parse(in, out, namespaces);
        String outputString = out.toString("UTF-8");
        String[] lines = outputString.split("\n");
        String[] expect = {
                "<?xml version='1.0' encoding='UTF-8'?>",
                "<delving-sip-source xmlns:a=\"http://a\" xmlns:b=\"http://b\" xmlns:c=\"http://c\">",
                "<input id=\"before_3030030_after\">",
                "<a:boo>&amp;three > two > 1</a:boo>",
                "<a:wrapper>",
                "<text_chunk>in between text</text_chunk>",
                "<text_chunk>and a second annoying line</text_chunk>",
                "<a:middle>",
                "<a:inside>yolk</a:inside>",
                "</a:middle>",
                "</a:wrapper>",
                "<a:unique>03030030</a:unique>",
                "<b:shh silent=\"very\">quiet</b:shh>",
                "</input>",
                "<input id=\"before_404040404_after\">",
                "<a:boo>http://somewhere.com/anurl/path/gumby.jpg</a:boo>",
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

    @Ignore
    @Test
    public void runThroughAnon() throws IOException, XMLStreamException, StorageException {
        String inputString = StringUtils.join(INPUT, "\n");
        System.setProperty("anonymousRecords", "10");
        InputStream in = new ByteArrayInputStream(inputString.getBytes("UTF-8"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        converter.parse(in, out, namespaces);
        String outputString = out.toString("UTF-8");
        String[] lines = outputString.split("\n");
        String[] expect = {
                "<?xml version='1.0' encoding='UTF-8'?>",
                "<delving-sip-source xmlns:a=\"http://a\" xmlns:b=\"http://b\" xmlns:c=\"http://c\">",
                "<input id=\"before_3030030_after\">",
                "<a:boo>&amp;bnepw > cab > 1</a:boo>",
                "<a:wrapper>",
                "<a:middle>",
                "<a:inside>vuot</a:inside>",
                "</a:middle>",
                "</a:wrapper>",
                "<a:unique>03030030</a:unique>",
                "<b:shh silent=\"very\">slrbn</b:shh>",
                "</input>",
                "<input id=\"before_404040404_after\">",
                "<a:boo>http://mxqalbsqe.qix/wcgxn/vufv/gumby.jpg</a:boo>",
                "<b:shh>zdljlih svfuv</b:shh>",
                "<a:unique>0404040404</a:unique>",
                "<c:long lang=\"stupid\">zlnn zh ggjr jhui</c:long>",
                "<c:long lang=\"stupid\">j evjhl-spwp dtldn</c:long>",
                "<c:long lang=\"stupid\">tk qqok tlaltkva nrluweh svqimp</c:long>",
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
        converter.parse(in, out, namespaces);
        Assert.assertEquals("Too many lines!", 6, new String(out.toByteArray()).split("\n").length);
    }

    private class MockDataSet implements DataSet {

        @Override
        public String getSpec() {
            return "spek";
        }

        @Override
        public String getOrganization() {
            return "orgy";
        }

        @Override
        public File getMediaDirectory() {
            return null;
        }

        @Override
        public List<SchemaVersion> getSchemaVersions() {
            return null;
        }

        @Override
        public RecDef getRecDef(String prefix) throws StorageException {
            return null;
        }

        @Override
        public Validator newValidator(String prefix) throws StorageException {
            return null;
        }

        @Override
        public boolean isProcessed(String prefix) throws StorageException {
            return false;
        }

        @Override
        public DataSetState getState(String prefix) {
            return null;
        }

        @Override
        public Map<String, String> getDataSetFacts() {
            return null;
        }

        @Override
        public void setDataSetFacts(Map<String, String> dataSetFacts) throws StorageException {
        }

        @Override
        public Map<String, String> getHints() {
            Map<String,String> hints = new HashMap<String, String>();
            hints.put(RECORD_ROOT_PATH, ROOT.toString());
            hints.put(UNIQUE_ELEMENT_PATH, UNIQ.toString());
            hints.put(UNIQUE_VALUE_CONVERTER, UNIQUE_CONVERTER);
            hints.put(RECORD_COUNT, "2");
            return hints;
        }

        @Override
        public void setHints(Map<String, String> hints) throws StorageException {
        }

        @Override
        public boolean isRecentlyImported() {
            return false;
        }

        @Override
        public void deleteConverted() throws StorageException {
        }

        @Override
        public boolean deleteTarget(String prefix) throws StorageException {
            return false;
        }

        @Override
        public void deleteAllTargets() throws StorageException {
        }

        @Override
        public File importedOutput() {
            return null;
        }

        @Override
        public InputStream openImportedInputStream() throws StorageException {
            return null;
        }

        @Override
        public File sourceOutput() {
            return null;
        }

        @Override
        public InputStream openSourceInputStream() throws StorageException {
            return null;
        }

        @Override
        public File targetOutput(String prefix) {
            return null;
        }

        @Override
        public Stats getLatestStats() {
            return null;
        }

        @Override
        public Stats getStats(boolean sourceFormat) {
            return null;
        }

        @Override
        public void setStats(Stats stats, boolean sourceFormat) throws StorageException {
          
        }

        @Override
        public RecMapping getRecMapping(String prefix, RecDefModel recDefModel) throws StorageException {
            return null;
        }

        @Override
        public RecMapping revertRecMapping(File previousMappingFile, RecDefModel recDefModel) throws StorageException {
            return null;
        }

        @Override
        public void setRecMapping(RecMapping recMapping, boolean freeze) throws StorageException {
          
        }

        @Override
        public List<File> getRecMappingFiles(String prefix) throws StorageException {
            return null;
        }

        @Override
        public ReportWriter openReportWriter(RecDef recDef) throws StorageException {
            return null;
        }

        @Override
        public ReportFile getReport(String prefix) throws StorageException {
            return null;
        }

        @Override
        public void deleteSource() {
        }

        @Override
        public List<File> getUploadFiles() throws StorageException {
            return null;
        }

        @Override
        public void fromSipZip(InputStream inputStream, long streamLength, ProgressListener progressListener) throws IOException, StorageException {
          
        }

        @Override
        public void remove() throws StorageException {
          
        }

        @Override
        public int compareTo(DataSet dataSet) {
            return 0;
        }
    }
}
