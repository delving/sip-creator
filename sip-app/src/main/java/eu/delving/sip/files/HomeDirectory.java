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

package eu.delving.sip.files;

import java.io.File;

/**
 * Locate the workspace and make sure the user has chosen a storage directory
 *
 *
 */

public class HomeDirectory {
    public static final File WORKSPACE_DIR = new File(System.getProperty("user.home"), "PocketMapper");
    public static final File WORK_DIR = new File(WORKSPACE_DIR, "work");
    public static final File UP_DIR = new File(WORKSPACE_DIR, "up");
    public static final File RECORD_DIR = new File(WORKSPACE_DIR, "records");

    static {
        if (!WORKSPACE_DIR.exists()) WORKSPACE_DIR.mkdirs();
        if (!UP_DIR.exists()) UP_DIR.mkdirs();
        if (!WORK_DIR.exists()) WORK_DIR.mkdirs();
        if (!RECORD_DIR.exists()) RECORD_DIR.mkdirs();
    }
}
