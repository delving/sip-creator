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

import eu.delving.groovy.DiscardRecordException;
import eu.delving.groovy.MappingException;
import eu.delving.groovy.MetadataRecord;
import eu.delving.groovy.XmlNodePrinter;
import eu.delving.metadata.MappingResult;
import org.apache.commons.io.output.CountingOutputStream;

import javax.xml.xpath.XPathExpressionException;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.io.FileUtils.deleteQuietly;
import static org.apache.commons.io.FileUtils.writeLines;

/**
 * Describes how a report is written during dataset processing
 *
 *
 */

public class ReportWriter {

    private final Object lock = new Object();
    private File reportFile;
    private File reportIndexFile;
    private File reportConclusionFile;
    private CountingOutputStream count;
    private Writer out;
    private DataOutputStream indexOut;
    private int recordNumber;

    public enum ReportType {
        INVALID,
        DISCARDED,
        UNEXPECTED
    }

    public ReportWriter(File reportFile, File reportIndexFile, File reportConclusionFile) throws FileNotFoundException, XPathExpressionException, UnsupportedEncodingException {
        this.reportFile = reportFile;
        this.reportIndexFile = reportIndexFile;
        this.reportConclusionFile = reportConclusionFile;
        deleteQuietly(reportConclusionFile);
        this.indexOut = new DataOutputStream(new FileOutputStream(reportIndexFile));
        this.count = new CountingOutputStream(new FileOutputStream(reportFile));
        this.out = new OutputStreamWriter(count, "UTF-8");
    }

    public void invalid(MappingResult mappingResult, Throwable e) throws IOException {
        report(ReportType.INVALID, e.getMessage());
        out.write(mappingResult.toXml());
        terminate();
    }

    public boolean recordError(MetadataRecord metadataRecord, MappingResult result, Throwable e) {
        if (e == null) {
            throw new NullPointerException();
        }

        try {
            if (e instanceof DiscardRecordException) {
                synchronized (lock) {
                    discarded(metadataRecord, e.getMessage());
                }
                return false;
            } else if (e instanceof MappingException) {
                synchronized (lock) {
                    unexpected(metadataRecord, (MappingException) e);
                }
            } else  {
                synchronized (lock) {
                    invalid(result, e);
                }
            }
        } catch (IOException ioe) {
            ioe.addSuppressed(e);
            throw new RuntimeException(ioe);
        }
        return true;
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
        deleteQuietly(reportFile);
        deleteQuietly(reportIndexFile);
        deleteQuietly(reportConclusionFile);
    }

    public void finish(int validCount, int invalidCount) {
        try {
            indexOut.close();
            out.close();
            List<String> lines = new ArrayList<String>();
            lines.add(String.format("Total Records: %d", validCount + invalidCount));
            lines.add(String.format("Valid Records: %d", validCount));
            lines.add(String.format("Invalid Records: %d", invalidCount));
            writeLines(reportConclusionFile, lines);
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
