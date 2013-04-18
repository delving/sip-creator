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

import org.apache.http.impl.EnglishReasonPhraseCatalog;

import java.io.Serializable;

/**
 * Shared between LinkChecker and LinkFile
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class LinkCheck implements Serializable {
    private static EnglishReasonPhraseCatalog REASON = EnglishReasonPhraseCatalog.INSTANCE;
    public int httpStatus;
    public long time;
    public int fileSize;
    public String mimeType;

    public String getStatusReason() {
        return String.format(
                "%d: %s",
                httpStatus, REASON.getReason(httpStatus, null)
        );
    }

    @Override
    public String toString() {
        return getStatusReason();
    }
}
