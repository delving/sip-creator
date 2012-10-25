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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Somewhat naively concatenate xml files from a zip file in to one sequence of lines
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class ZipEntryXmlReader {
    private ZipInputStream zipIn;
    private BufferedReader reader;

    public ZipEntryXmlReader(InputStream zipIn) {
        this.zipIn = new ZipInputStream(zipIn);
    }

    public String readLine() throws IOException {
        if (reader != null) {
            String line = reader.readLine();
            if (line != null) return line;
            reader = null;
        }
        while (true) {
            ZipEntry zipEntry = zipIn.getNextEntry();
            if (zipEntry == null) return null;
            String fileName = zipEntry.getName();
            if (!(fileName.endsWith(".xml") || fileName.endsWith(".XML"))) continue;
            reader = new BufferedReader(new InputStreamReader(zipIn, "UTF-8"));
            String line = reader.readLine();
            if (line != null) return line;
        }
    }

}
