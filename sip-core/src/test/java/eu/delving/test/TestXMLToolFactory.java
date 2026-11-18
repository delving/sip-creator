/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.test;

import com.ctc.wstx.stax.WstxEventFactory;
import com.ctc.wstx.stax.WstxInputFactory;
import com.ctc.wstx.stax.WstxOutputFactory;
import eu.delving.XMLToolFactory;
import org.codehaus.stax2.XMLStreamReader2;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Pins the StAX factories to Woodstox, whatever class loader the calling
 * thread carries. MetadataRecordFactory casts readers to XMLStreamReader2, so a
 * JDK parser slipping in fails every record (seen under Narthex's Play dev mode).
 */
public class TestXMLToolFactory {

    /** Every factory is the Woodstox one, and its readers are Stax2 readers. */
    @Test
    public void factoriesAreWoodstox() throws Exception {
        assertInstanceOf(WstxInputFactory.class, XMLToolFactory.xmlInputFactory());
        assertInstanceOf(WstxOutputFactory.class, XMLToolFactory.xmlOutputFactory());
        assertInstanceOf(WstxEventFactory.class, XMLToolFactory.xmlEventFactory());
        assertInstanceOf(XMLStreamReader2.class,
                XMLToolFactory.xmlInputFactory().createXMLStreamReader(new StringReader("<a/>")));
    }

    /**
     * With a context class loader that cannot see Woodstox, the old
     * newInstance() lookup fell back to the JDK parser; construction must not.
     */
    @Test
    public void woodstoxEvenWhenContextLoaderCannotSeeIt() throws Exception {
        AtomicReference<Object> factory = new AtomicReference<>();
        Thread thread = new Thread(() -> factory.set(XMLToolFactory.xmlInputFactory()));
        thread.setContextClassLoader(new URLClassLoader(new URL[0], ClassLoader.getPlatformClassLoader()));
        thread.start();
        thread.join();
        assertInstanceOf(WstxInputFactory.class, factory.get());
    }
}
