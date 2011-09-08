/*
 * Copyright 2010 DELVING BV
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

import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import java.io.File;
import java.io.FileFilter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Locate the workspace and make sure the user has chosen a storage directory
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public class StorageFinder {
    private static final File WORKSPACE_DIR = new File(System.getProperty("user.home"), "SIPCreatorWorkspace");
    private static final Pattern HPU_HUMAN = Pattern.compile("([A-Za-z0-9.]+):([0-9]+)/([A-Za-z0-9]+)");
    private static final Pattern HPU_DIRECTORY = Pattern.compile("([A-Za-z0-9_]+)__([0-9]+)___([A-Za-z0-9]+)");

    public static File getStorageDirectory() {
        if (!WORKSPACE_DIR.exists()) {
            if (!WORKSPACE_DIR.mkdirs()) {
                throw new RuntimeException(String.format("Unable to create %s", WORKSPACE_DIR.getAbsolutePath()));
            }
        }
        else if (!WORKSPACE_DIR.isDirectory()) {
            throw new RuntimeException(String.format("Expected directory but %s is a file", WORKSPACE_DIR.getAbsolutePath()));
        }
        File[] files = WORKSPACE_DIR.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory() && HPU_DIRECTORY.matcher(file.getName()).matches();
            }
        });
        switch (files.length) {
            case 0:
                return createHostPortDirectory();
            case 1:
                return files[0];
            default:
                return chooseDirectory(files);
        }
    }

    public static String getHostPort(File file) {
        Matcher matcher = getDirectoryMatcher(file);
        return String.format("%s:%s", getHostName(matcher), matcher.group(2));
    }

    public static String getUser(File file) {
        Matcher matcher = getDirectoryMatcher(file);
        return matcher.group(3);
    }

    public static String getHostPortUser(File file) {
        Matcher matcher = getDirectoryMatcher(file);
        return String.format("%s:%s/%s", getHostName(matcher), matcher.group(2), matcher.group(3));
    }

    private static String getHostName(Matcher matcher) {
        return matcher.group(1).replace("_", ".");
    }

    private static Matcher getDirectoryMatcher(File file) {
        Matcher matcher = HPU_DIRECTORY.matcher(file.getName());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Directory name is incorrect " + file.getName());
        }
        return matcher;
    }

    private static File createDirectory(String host, String port, String user) {
        String mungedHost = host.replaceAll("\\.", "_");
        String directoryName = String.format("%s__%s___%s", mungedHost, port, user);
        File directory = new File(WORKSPACE_DIR, directoryName);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new RuntimeException("Unable to create "+directory.getAbsolutePath());
        }
        return directory;
    }

    private static File createDirectory(String hostPortUser) {
        Matcher matcher = HPU_HUMAN.matcher(hostPortUser);
        if (matcher.matches()) {
            return createDirectory(matcher.group(1), matcher.group(2), matcher.group(3));
        }
        else {
            throw new RuntimeException("Expected host:port/user but got "+hostPortUser);
        }
    }

    private static File createHostPortDirectory() {
        while (true) {
            String answer = JOptionPane.showInputDialog(null, "<html>Please enter host:port/user for your Culture Hub connection");
            Matcher matcher = HPU_HUMAN.matcher(answer);
            if (matcher.matches()) {
                return createDirectory(matcher.group(1), matcher.group(2), matcher.group(3));
            }
            int okCancel = JOptionPane.showConfirmDialog(null, "<html>Value does not match \"host:port/user\" pattern. Try again.", "Sorry", JOptionPane.OK_CANCEL_OPTION);
            if (okCancel == JOptionPane.CANCEL_OPTION) {
                throw new RuntimeException("No directory created");
            }
        }
    }

    private static File chooseDirectory(File[] directories) {
        String [] hostPorts = new String[directories.length];
        for (int walk=0; walk<directories.length; walk++) {
            hostPorts[walk] = getHostPortUser(directories[walk]);
        }
        JComboBox box = new JComboBox(hostPorts);
        int okCancel = JOptionPane.showConfirmDialog(null, box, "Choose server", JOptionPane.OK_CANCEL_OPTION);
        if (okCancel == JOptionPane.CANCEL_OPTION) {
            throw new RuntimeException("No directory created");
        }
        else {
            String hostPort = (String)box.getSelectedItem();
            return createDirectory(hostPort);
        }
    }

    public static void main(String[] args) {
        System.out.println("Storage directory: " + getStorageDirectory().getAbsolutePath());
    }
}
