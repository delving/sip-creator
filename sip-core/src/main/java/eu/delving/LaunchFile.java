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

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.URL;
import java.util.List;

/**
 * Produce the text content of the JNLP file for the Hub to deliver
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class LaunchFile {

    public static String createJNLP(String homeUrl, String codeBaseUrl, String user) {
        StringBuilder out = new StringBuilder();
        for (String line : PRE_DEPENDENCIES) {
            line = replace(line, HOME_URL, homeUrl);
            line = replace(line, CODE_BASE_URL, codeBaseUrl);
            out.append(line).append('\n');
        }
        try {
            URL resource = LaunchFile.class.getResource("/LaunchFIle.txt");
            List<String> lines = IOUtils.readLines(resource.openStream(), "UTF-8");
            for (String line : lines) {
                out.append(line).append('\n');
            }
        }
        catch (IOException e) {
            throw new RuntimeException("Unable to load resource");
        }
        for (String line : POST_DEPENDENCIES) {
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
    private static final String[] PRE_DEPENDENCIES = {
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
            "<jnlp spec=\"1.0+\" codebase=\"" + CODE_BASE_URL + "\" href=\"" + HOME_URL + "/sip-creator.jnlp\">",
            "<information>",
            "    <title>SIP-Creator</title>",
            "    <vendor>Delving</vendor>",
            "    <description kind=\"one-line\">SIP-Creator</description>",
            "    <description kind=\"short\">Submission Information Package Creator</description>",
            "    <icon href=\"" + HOME_URL + "/sip-creator-logo.png\" kind=\"default\"/>",
            "    <shortcut online=\"true\">",
            "        <desktop/>",
            "    </shortcut>",
            "</information>",
            "<security>",
            "    <all-permissions/>",
            "</security>",
            "<resources>",
            "    <j2se version=\"1.6+\" initial-heap-size=\"256m\" max-heap-size=\"512m\"/>",
            "    <property name=\"jnlp.versionEnabled\" value=\"false\"/>",
    };
    private static final String[] POST_DEPENDENCIES = {
            "</resources>",
            "<application-desc main-class=\"eu.delving.sip.Application\">",
            "  <argument>" + USER + "</argument>",
            "</application-desc>",
            "</jnlp>",
    };
}
