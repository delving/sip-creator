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

package eu.delving.metadata;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * This class manages aspects of using a timestamp as prefix in the naming of files in the data set.
 * It is called Hasher for historical reasons, because previously the prefix was an MD5 hash.
 *
 */
public class Hasher {
    public static final String SEPARATOR = "__";

    public static String extractFileName(File file) {
        String fileName = file.getName();
        int hashSeparator = fileName.indexOf(SEPARATOR);
        if (hashSeparator > 0) {
            fileName = fileName.substring(hashSeparator + 2);
        }
        return fileName;
    }

    public static String extractHash(File file) {
        return extractHashFromFileName(file.getName());
    }

    public static String extractHashFromFileName(String fileName) {
        int hashSeparator = fileName.indexOf(SEPARATOR);
        if (hashSeparator > 0) {
            return fileName.substring(0, hashSeparator);
        }
        else {
            return null;
        }
    }

    public static String prefixFileName(String fileName) {
        return prefixFileName(fileName, Calendar.getInstance().getTime());
    }

    public static String prefixFileName(String fileName, Date time) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HHmmss").format(time);
        return timestamp + SEPARATOR + fileName;
    }

}
