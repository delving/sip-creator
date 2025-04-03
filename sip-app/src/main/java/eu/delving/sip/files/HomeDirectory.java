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

package eu.delving.sip.files;

import eu.delving.sip.model.SipProperties;

import java.io.*;
import java.util.Properties;

/**
 * Locate the workspace and make sure the user has chosen a storage directory
 *
 *
 */

public class HomeDirectory {
    public static final String POCKETMAPPER_DIR_NAME = "PocketMapper";
    public static final String WORK_DIR_NAME = "work";
    public static final String UP_DIR_NAME = "up";
    public static final String RECORD_DIR_NAME = "records";

    // Legacy static final directory assignments for single-project PocketMappers
    // WORKSPACE_DIR still used for accessing the PocketMapper directory
    public static final File WORKSPACE_DIR = new File(System.getProperty("user.home"), POCKETMAPPER_DIR_NAME);
    public static final File WORK_DIR = new File(WORKSPACE_DIR, WORK_DIR_NAME);
    public static final File UP_DIR = new File(WORKSPACE_DIR, UP_DIR_NAME);
    public static final File RECORD_DIR = new File(WORKSPACE_DIR, RECORD_DIR_NAME);

    // New static but non-final directory assignments for multi-project PocketMappers
    // (private: use get-methods instead)
    private static File workspaceDir = WORKSPACE_DIR;
    private static File workDir = WORK_DIR;
    private static File upDir = UP_DIR;
    //private static File recordDir = RECORD_DIR; // RECORD_DIR seems unused so no point in having recordDir

    static {
        if (!WORKSPACE_DIR.exists()) WORKSPACE_DIR.mkdirs();
        // Commenting out mkdirs below to not automatically create a "default" project if it doesn't already exist
        //if (!UP_DIR.exists()) UP_DIR.mkdirs();
        //if (!WORK_DIR.exists()) WORK_DIR.mkdirs();
        //if (!RECORD_DIR.exists()) RECORD_DIR.mkdirs();
    }

    public static void openProject(File dir) {
        File sipProperties = new File(dir, SipProperties.FILE_NAME);
        if (!sipProperties.isFile()) {
            throw new RuntimeException("The folder does not contain a PocketMapper project.");
        }

        workspaceDir = dir;
        workDir = new File(dir, WORK_DIR_NAME);
        upDir = new File(dir, UP_DIR_NAME);
        //recordDir = new File(dir, RECORD_DIR_NAME);
        if (!workspaceDir.exists()) workspaceDir.mkdirs();
        if (!workDir.exists()) workDir.mkdirs();
        if (!upDir.exists()) upDir.mkdirs();
        //if (!recordDir.exists()) recordDir.mkdirs();
    }

    public static void createProject(String name, Properties sipProperties) {
        workspaceDir = new File(WORKSPACE_DIR, name);
        if (workspaceDir.exists()) {
            throw new RuntimeException("A project using that name already exists.");
        }

        workDir = new File(workspaceDir, WORK_DIR_NAME);
        upDir = new File(workspaceDir, UP_DIR_NAME);
        //recordDir = new File(dir, RECORD_DIR_NAME);
        workspaceDir.mkdirs();
        workDir.mkdirs();
        upDir.mkdirs();
        //recordDir.mkdirs();

        editProject(workspaceDir, sipProperties);
    }

    public static void editProject(File dir, Properties sipProperties) {
        try {
            OutputStream output = new FileOutputStream(new File(dir, SipProperties.FILE_NAME));
            sipProperties.store(output, null);
            output.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static File getWorkspaceDir() {
        return workspaceDir;
    }

    public static File getWorkDir() {
        return workDir;
    }

    public static File getUpDir() {
        return upDir;
    }
}
