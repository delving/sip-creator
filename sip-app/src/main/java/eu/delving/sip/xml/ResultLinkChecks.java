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

package eu.delving.sip.xml;

import eu.delving.metadata.RecDef;
import eu.delving.sip.base.Swing;
import eu.delving.sip.base.Work;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.LinkCheck;
import eu.delving.sip.files.LinkChecker;
import eu.delving.sip.files.ReportWriter;
import eu.delving.sip.model.Feedback;
import org.apache.commons.lang.StringEscapeUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Gather the link checks from valid records coming from the MappingResultImpl
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class ResultLinkChecks {
    private DataSet dataSet;
    private String prefix;
    private LinkChecker linkChecker;

    public ResultLinkChecks(DataSet dataSet, String prefix, LinkChecker linkChecker) {
        this.dataSet = dataSet;
        this.prefix = prefix;
        this.linkChecker = linkChecker;
    }

    public Work.DataSetPrefixWork checkLinks(final String localId, List<String> lines, final Feedback feedback, final Swing afterCheck) {
        if (lines == null) return null;
        final List<String> safeLines = new ArrayList<String>(lines);
        return new Work.DataSetPrefixWork() {

            @Override
            public String getPrefix() {
                return prefix;
            }

            @Override
            public DataSet getDataSet() {
                return dataSet;
            }

            @Override
            public Job getJob() {
                return Job.CHECK_LINK;
            }

            @Override
            public void run() {
                try {
                    boolean linkChecked = false;
                    for (String line : safeLines) {
                        Matcher matcher = ReportWriter.LINK.matcher(line);
                        if (!matcher.matches()) continue; // RuntimeException?
                        RecDef.Check check = RecDef.Check.valueOf(matcher.group(1));
                        if (!check.fetch) continue;
                        String url = matcher.group(2);
                        if (!linkChecker.contains(url)) {
                            linkChecker.request(url, dataSet.getSpec(), dataSet.getOrganization(), localId);
                            linkChecked = true;
                        }
                    }
                    if (linkChecked) {
                        Swing.Exec.later(afterCheck);
                    }
                }
                catch (IOException e) {
                    feedback.alert("Unable to check link", e);
                }
            }
        };
    }

    public static void validLinesToHTML(List<String> lines, LinkChecker linkChecker, StringBuilder out) {
        out.append("<table width='100%'>\n");
        for (String line : lines) {
            Matcher matcher = ReportWriter.LINK.matcher(line);
            if (!matcher.matches()) continue; // RuntimeException?
            RecDef.Check check = RecDef.Check.valueOf(matcher.group(1));
            String content = matcher.group(2);
            out.append("<tr><td>");
            out.append(String.format("<table width='100%%'><tr><td><h2>%s</h2></td></tr><td>", check));
            switch (check) {
                case LANDING_PAGE:
                case DIGITAL_OBJECT:
                case THUMBNAIL:
                case DEEP_ZOOM:
                case THESAURUS_REFERENCE:
                case LOD_REFERENCE:
                    out.append(String.format(
                            "<a href=\"%s\">%s<a><br>\n",
                            content, StringEscapeUtils.escapeHtml(content))
                    );
                    if (linkChecker == null || !linkChecker.contains(content)) {
                        out.append("<ul><li>unchecked</li></ul>");
                    }
                    else {
                        if (check == RecDef.Check.THUMBNAIL) {
                            out.append(String.format(
                                    "<table cellpadding=10><tr><td><img src=\"%s\"/></td</tr></table><br>",
                                    content
                            ));
                        }
                        LinkCheck linkCheck = linkChecker.lookup(content);
                        out.append("<ul>\n");
                        out.append(String.format("<li>Ok: %s</li>\n", linkCheck.ok));
                        out.append(String.format("<li>Checked: %s</li>\n", linkCheck.getTime()));
                        out.append(String.format("<li>HTTP status: %d</li>\n", linkCheck.httpStatus));
                        out.append(String.format("<li>Status reason: %s</li>\n", linkCheck.getStatusReason()));
                        out.append(String.format("<li>File size: %d</li>\n", linkCheck.fileSize));
                        out.append(String.format("<li>MIME type: %s</li>\n", linkCheck.mimeType));
                        out.append("</ul>\n");
                    }
                    break;
                case GEO_COORDINATE:
                    String thumbnail = String.format(
                            "http://maps.google.com/maps/api/staticmap?center=%s&size=400x400&zoom=10&maptype=roadmap&format=jpg&sensor=false&markers=color:blue%%7Clabel:S%%7C%s",
                            content, content
                    );
                    out.append(String.format(
                            "<table cellpadding=10><tr><td><img src=\"%s\"/></td</tr></table><br>",
                            thumbnail
                    ));
                    out.append(String.format(
                            "<a href=\"https://maps.google.com/maps?q=%s\">%s<a><br>",
                            content, content
                    ));
                    break;
                case DATE:
                    out.append(content);
                    out.append("<ul><li>not checking yet</li></ul>");
                    break;
            }
            out.append("</td></tr></table>");
            out.append("</td><tr>");
        }
        out.append("</table>\n");
    }
}
