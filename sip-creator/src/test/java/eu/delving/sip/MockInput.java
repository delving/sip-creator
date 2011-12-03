/*
 * Copyright 2011 DELVING BV
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * Provide some sample data for tests
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class MockInput {
    private static final String SAMPLE_INPUT = "/mock-input.xml.gz";

    public static File sampleFile() throws IOException {
        return new File(MockInput.class.getResource(SAMPLE_INPUT).getFile());
    }

    public static InputStream sampleInputStream() throws IOException {
        return new GZIPInputStream(MockInput.class.getResource(SAMPLE_INPUT).openStream());
    }

}
