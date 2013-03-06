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

package eu.delving;

/**
 * Produce the text content of the JNLP file for the Hub to deliver
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class LaunchFile {

    public static String createJNLP(String homeUrl, String codeBaseUrl, String user) {
        StringBuilder out = new StringBuilder();
        for (String line : LINES) {
            line = replace(line, HOME_URL, homeUrl);
            line = replace(line, CODE_BASE_URL, codeBaseUrl);
            line = replace(line, USER, user);
            out.append(line).append('\n');
        }
        return out.toString();
    }

    private static String replace(String line, String name, String value) {
        return line.contains(name) ? line.replace(name, value) : line;
    }

    private static final String CODE_BASE_URL = "$CODEBASE$";
    private static final String HOME_URL = "$HOME$";
    private static final String USER = "$USER$";
    private static final String[] LINES = {
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
            "<jnlp spec=\"1.0+\" codebase=\"" + CODE_BASE_URL + "\" href=\"" + HOME_URL + "/sip-creator.jnlp\">",
            "<information>",
            "  <title>SIP-Creator</title>",
            "  <vendor>Delving</vendor>",
            "  <description kind=\"one-line\">SIP-Creator</description>",
            "  <description kind=\"short\">Submission Information Package Creator</description>",
            "  <icon href=\"" + HOME_URL + "/sip-creator-logo.png\" kind=\"default\"/>",
            "  <shortcut online=\"true\">",
            "    <desktop/>",
            "  </shortcut>",
            "</information>",
            "<security>",
            "  <all-permissions/>",
            "</security>",
            "<resources>",
            "  <j2se version=\"1.6+\" initial-heap-size=\"256m\" max-heap-size=\"512m\"/>",
            "  <property name=\"jnlp.versionEnabled\" value=\"false\"/>",
            "  <jar href=\"sip-app-1.1.1-SNAPSHOT.jar\" main=\"true\"/>",
            "  <jar href=\"sip-core-1.1.1-SNAPSHOT.jar\"/>",
            "  <jar href=\"schema-repo-1.1.1-SNAPSHOT.jar\"/>",
            "  <jar href=\"oauth2-client-0.2-SNAPSHOT.jar\"/>",
            "  <jar href=\"oauth2-common-0.2-SNAPSHOT.jar\"/>",
            "  <jar href=\"jettison-1.2.jar\"/>",
            "  <jar href=\"slf4j-api-1.6.1.jar\"/>",
            "  <jar href=\"httpclient-4.1.2.jar\"/>",
            "  <jar href=\"httpcore-4.1.2.jar\"/>",
            "  <jar href=\"commons-logging-1.1.1.jar\"/>",
            "  <jar href=\"commons-codec-1.4.jar\"/>",
            "  <jar href=\"log4j-1.2.16.jar\"/>",
            "  <jar href=\"commons-lang-2.3.jar\"/>",
            "  <jar href=\"commons-io-2.0.jar\"/>",
            "  <jar href=\"xstream-1.4.2.jar\"/>",
            "  <jar href=\"xmlpull-1.1.3.1.jar\"/>",
            "  <jar href=\"xpp3_min-1.1.4c.jar\"/>",
            "  <jar href=\"gson-1.7.1.jar\"/>",
            "  <jar href=\"groovy-all-2.0.0.jar\"/>",
            "  <jar href=\"woodstox-core-asl-4.0.9.jar\"/>",
            "  <jar href=\"stax-api-1.0-2.jar\"/>",
            "  <jar href=\"stax2-api-3.0.3.jar\"/>",
            "  <jar href=\"jfreechart-1.0.13.jar\"/>",
            "  <jar href=\"jcommon-1.0.16.jar\"/>",
            "  <jar href=\"jcoord-1.0.jar\"/>",
            "  <jar href=\"proj4j-0.1.0.jar\"/>",
            "  <jar href=\"itext-2.1.7.jar\"/>",
            "  <jar href=\"bcmail-jdk14-138.jar\"/>",
            "  <jar href=\"bcprov-jdk14-138.jar\"/>",
            "  <jar href=\"bctsp-jdk14-1.38.jar\"/>",
            "  <jar href=\"bcprov-jdk14-1.38.jar\"/>",
            "  <jar href=\"bcmail-jdk14-1.38.jar\"/>",
            "  <jar href=\"stringtemplate-3.0.jar\"/>",
            "  <jar href=\"antlr-2.7.7.jar\"/>",
            "  <jar href=\"sqljdbc4-3.0.jar\"/>",
            "  <jar href=\"jgoodies-forms-1.6.0.jar\"/>",
            "  <jar href=\"jgoodies-common-1.4.0.jar\"/>",
            "  <jar href=\"cglib-2.1_3.jar\"/>",
            "  <jar href=\"asm-1.5.3.jar\"/>",
            "  <jar href=\"Saxon-HE-9.4.0.6.jar\"/>",
            "</resources>",
            "<application-desc main-class=\"eu.delving.sip.Application\">",
            "  <argument>" + USER + "</argument>",
            "</application-desc>",
            "</jnlp>",
    };
}
