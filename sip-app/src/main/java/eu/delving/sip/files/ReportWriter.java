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
import eu.delving.XMLToolFactory;
import eu.delving.groovy.MappingException;
import eu.delving.groovy.MetadataRecord;
import eu.delving.groovy.XmlNodePrinter;
import eu.delving.groovy.XmlSerializer;
import eu.delving.metadata.Path;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.XPathContext;
import net.sf.saxon.dom.DOMNodeList;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Node;

import javax.xml.xpath.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private XPathFactory pathFactory = XMLToolFactory.xpathFactory();
    private XPathContext pathContext;
    private Map<Path, XPathExpression> expressionMap = new HashMap<Path, XPathExpression>();
    private XmlSerializer serializer = new XmlSerializer();
    private File file;
    private List<RecDef.FieldMarker> fieldMarkers;
    private PrintWriter out;
    private int recordNumber;

    public enum ReportType {
        VALID,
        INVALID,
        DISCARDED,
        UNEXPECTED
    }

    public ReportWriter(File file, List<RecDef.FieldMarker> fieldMarkers, XPathContext pathContext) throws FileNotFoundException, XPathExpressionException {
        this.file = file;
        this.fieldMarkers = fieldMarkers;
        this.pathContext = pathContext;
        for (RecDef.FieldMarker fieldMarker : fieldMarkers) {
            if (fieldMarker.check == null || fieldMarker.path == null) continue;
            expressionMap.put(fieldMarker.path, createPath().compile(fieldMarker.path.toString()));
        }
        this.out = new PrintWriter(file);
    }

    public void valid(MappingResult mappingResult) throws XPathExpressionException {
        report(ReportType.VALID, "");
        for (RecDef.FieldMarker fieldMarker : fieldMarkers) {
            if (fieldMarker.check == null || fieldMarker.path == null) continue;
            XPathExpression expression = expressionMap.get(fieldMarker.path);
            DOMNodeList nodeList = (DOMNodeList) expression.evaluate(mappingResult.root(), XPathConstants.NODESET);
            for (int walk = 0; walk < nodeList.getLength(); walk++) {
                Node node = nodeList.item(walk);
                out.printf("<<<%s>>>%s\n", fieldMarker.check, node.getTextContent());
            }
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

    private XPath createPath() {
        XPath path = pathFactory.newXPath();
        path.setNamespaceContext(pathContext);
        return path;
    }

}
