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

package eu.delving.sip.files;

import eu.delving.MappingResult;
import eu.delving.groovy.MappingException;
import eu.delving.groovy.MetadataRecord;
import eu.delving.groovy.XmlNodePrinter;
import eu.delving.groovy.XmlSerializer;
import eu.delving.sip.xml.LinkCheckExtractor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.CountingOutputStream;

import javax.xml.xpath.XPathExpressionException;
import java.io.*;
import java.util.regex.Pattern;

/**
 * Describes how a report is written during dataset processing
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class ReportWriter {
    public static final String DIVIDER = "::::";
    public static Pattern START = Pattern.compile("<<(\\d+),([^>]+)>>(.*)");
    public static Pattern END = Pattern.compile("<<>>");
    public static Pattern LINK = Pattern.compile("<<<([^>]+)>>>(.*)");
    private XmlSerializer serializer = new XmlSerializer();
    private File reportFile;
    private File reportIndexFile;
    private LinkCheckExtractor linkCheckExtractor;
    private CountingOutputStream count;
    private Writer out;
    private DataOutputStream indexOut;
    private int recordNumber;

    public enum ReportType {
        VALID,
        INVALID,
        DISCARDED,
        UNEXPECTED
    }

    public ReportWriter(File reportFile, File reportIndexFile, LinkCheckExtractor linkCheckExtractor) throws FileNotFoundException, XPathExpressionException, UnsupportedEncodingException {
        this.reportFile = reportFile;
        this.reportIndexFile = reportIndexFile;
        this.linkCheckExtractor = linkCheckExtractor;
        this.indexOut = new DataOutputStream(new FileOutputStream(reportIndexFile));
        this.count = new CountingOutputStream(new FileOutputStream(reportFile));
        this.out = new OutputStreamWriter(count, "UTF-8");
    }

    public void valid(String id, MappingResult mappingResult) throws XPathExpressionException, IOException {
        report(ReportType.VALID, id);
        for (String line : linkCheckExtractor.getChecks(mappingResult)) {
            out.write(line);
            out.write("\n");
        }
        terminate();
    }

    public void invalid(MappingResult mappingResult, Exception e) throws IOException {
        report(ReportType.INVALID, e.getMessage());
        out.write(serializer.toXml(mappingResult.root(), true));
        terminate();
    }

    public void discarded(MetadataRecord inputRecord, String discardMessage) throws IOException {
        report(ReportType.DISCARDED, discardMessage);
        out.write("Reason: ");
        out.write(discardMessage);
        out.write("\n");
        out.write(XmlNodePrinter.toXml(inputRecord.getRootNode()));
        terminate();
    }

    public void unexpected(MetadataRecord inputRecord, MappingException exception) throws IOException {
        report(ReportType.UNEXPECTED, exception.getMessage());
        exception.printStackTrace(new PrintWriter(out));
        out.write(XmlNodePrinter.toXml(inputRecord.getRootNode()));
        terminate();
    }

    public void abort() {
        try {
            out.close();
            indexOut.close();
        }
        catch (IOException e) {
            throw new RuntimeException("Unable to close", e);
        }
        FileUtils.deleteQuietly(reportFile);
        FileUtils.deleteQuietly(reportIndexFile);
    }

    public void finish(int validCount, int invalidCount) {
        try {
            indexOut.close();
            out.flush();
            out.write(DIVIDER);
            out.write("\n");
            out.write(String.format("total=%d\n", validCount + invalidCount));
            out.write(String.format("valid=%d\n", validCount));
            out.write(String.format("invalid=%d\n", invalidCount));
            out.close();
        }
        catch (IOException e) {
            throw new RuntimeException("Unable to finish report", e);
        }

    }

    private void report(ReportType type, String message) throws IOException {
        out.flush();
        indexOut.writeLong(count.getByteCount());
        out.write(String.format("<<%d,%s>>%s\n", recordNumber++, type, message));
    }

    private void terminate() throws IOException {
        out.write("<<>>\n");
    }
}
