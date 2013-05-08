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

import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.regex.Pattern;

/**
 * Describes how a report is written during dataset processing
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class ReportWriter {
    public static final String DIVIDER = "::::::::::::::::::::::::::::::::::::::::";
    public static Pattern START = Pattern.compile("<<(\\d+),([^>]+)>>(.*)");
    public static Pattern END = Pattern.compile("<<>>");
    public static Pattern LINK = Pattern.compile("<<<([^>]+)>>>(.*)");
    private XmlSerializer serializer = new XmlSerializer();
    private File file;
    private LinkCheckExtractor linkCheckExtractor;
    private PrintWriter out;
    private int recordNumber;

    public enum ReportType {
        VALID,
        INVALID,
        DISCARDED,
        UNEXPECTED
    }

    public ReportWriter(File file, LinkCheckExtractor linkCheckExtractor) throws FileNotFoundException, XPathExpressionException {
        this.file = file;
        this.linkCheckExtractor = linkCheckExtractor;
        this.out = new PrintWriter(file);
    }

    public void valid(String id, MappingResult mappingResult) throws XPathExpressionException {
        report(ReportType.VALID, id);
        for (String line : linkCheckExtractor.getChecks(mappingResult)) {
            out.println(line);
        }
        terminate();
    }

    public void invalid(MappingResult mappingResult, Exception e) {
        report(ReportType.INVALID, e.getMessage());
        out.print(serializer.toXml(mappingResult.root(), true));
        terminate();
    }

    public void discarded(MetadataRecord inputRecord, String discardMessage) {
        report(ReportType.DISCARDED, discardMessage);
        out.println("Reason: " + discardMessage);
        out.print(XmlNodePrinter.toXml(inputRecord.getRootNode()));
        terminate();
    }

    public void unexpected(MetadataRecord inputRecord, MappingException exception) {
        report(ReportType.UNEXPECTED, exception.getMessage());
        exception.printStackTrace(out);
        out.print(XmlNodePrinter.toXml(inputRecord.getRootNode()));
        terminate();
    }

    public void abort() {
        out.close();
        FileUtils.deleteQuietly(file);
    }

    public void finish(int validCount, int invalidCount) {
        out.println(DIVIDER);
        out.println("Validation was completed:");
        out.println("Total Valid Records: " + validCount);
        out.println("Total Invalid Records: " + invalidCount);
        out.println("Total Records: " + (validCount + invalidCount));
        out.close();
    }

    private void report(ReportType type, String message) {
        out.printf("<<%d,%s>>%s\n", recordNumber++, type, message);
    }

    private void terminate() {
        out.println("<<>>");
    }
}
