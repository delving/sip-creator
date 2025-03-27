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

package eu.delving.sip.cli;

import java.nio.file.Path;

public class FilenameExtractor {

    public static String extractBaseName(String filepath) {
        if (filepath == null || filepath.isEmpty()) {
            return "";
        }

        // Get just the filename from the path (everything after last / or \)
        String filename = filepath.contains("/")
                ? filepath.substring(filepath.lastIndexOf('/') + 1)
                : filepath.contains("\\")
                        ? filepath.substring(filepath.lastIndexOf('\\') + 1)
                        : filepath;

        String suffix = "_record-definition.xml";
        int index = filename.indexOf(suffix);

        return index != -1 ? filename.substring(0, index) : filename;
    }
}
