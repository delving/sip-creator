/*
 * Copyright 2011, 2012 Delving BV
 *
 * Licensed under the EUPL, Version 1.0 or? as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the
 * Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.delving.sip;

import com.ctc.wstx.util.StringUtil;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import eu.delving.sip.base.NetworkClient;
import junit.framework.Assert;
import org.junit.Test;

import java.util.Arrays;

/**
 * Make sure that lists coming back from the hub are properly interpreted
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestListFetch {

    @Test
    public void testDeserialize() {
        String[] DS = {
                "<data-set-list>",
                "<data-set>",
                "<spec>legermuseum-voertuigen</spec>",
                "<name>Legermuseum, Voertuigen</name>",
                "<orgId>dimcon</orgId>",
                "<createdBy>",
                "<username>hansschraven</username>",
                "<fullname>Hans Schraven</fullname>",
                "<email>hans.schraven@gmail.com</email>",
                "</createdBy>",
                "<schemaVersions>",
                "<schemaVersion>",
                "<prefix>gumby</prefix>",
                "<version>pokey</version>",
                "</schemaVersion>",
                "</schemaVersions>",
                "<state>enabled</state>",
                "<recordCount>92</recordCount>",
                "</data-set>",
                "</data-set-list>",
        };
        String listString = StringUtil.concatEntries(Arrays.asList(DS), "\n", "\n");
        NetworkClient.DataSetList list = (NetworkClient.DataSetList) getStream().fromXML(listString);
        Assert.assertEquals("oops", "legermuseum-voertuigen", list.list.get(0).spec);
        Assert.assertEquals("oops", "pokey", list.list.get(0).schemaVersions.get(0).version);
    }

    private static XStream getStream() {
        XStream xstream = new XStream(new PureJavaReflectionProvider());
        xstream.setMode(XStream.NO_REFERENCES);
        xstream.processAnnotations(NetworkClient.DataSetList.class);
        return xstream;
    }

}
