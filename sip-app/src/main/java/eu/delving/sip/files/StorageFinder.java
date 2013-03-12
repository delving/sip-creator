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

import javax.jnlp.BasicService;
import javax.jnlp.ServiceManager;
import javax.jnlp.UnavailableServiceException;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import java.awt.GridLayout;
import java.io.File;
import java.io.FileFilter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static eu.delving.sip.files.Storage.STANDALONE_DIR;
import static javax.swing.JOptionPane.*;

/**
 * Locate the workspace and make sure the user has chosen a storage directory
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class StorageFinder {
    private static final File WORKSPACE_DIR = new File(System.getProperty("user.home"), "DelvingSIPCreator");
    private static final Pattern HPU_HUMAN = Pattern.compile("([A-Za-z0-9.-]+):([0-9]+)/([A-Za-z0-9]+)");
    private static final Pattern HPU_DIRECTORY = Pattern.compile("([A-Za-z0-9_-]+)__([0-9]+)___([A-Za-z0-9]+)");
    private static final String CHOSEN_DIRECTORY = "chosenDirectory";
    private String[] args;
    private List<File> storageDirs = new ArrayList<File>();

    public StorageFinder() {
        if (!WORKSPACE_DIR.exists()) {
            if (!WORKSPACE_DIR.mkdirs()) {
                throw new RuntimeException(String.format("Unable to create %s", WORKSPACE_DIR.getAbsolutePath()));
            }
        }
        else if (!WORKSPACE_DIR.isDirectory()) {
            throw new RuntimeException(String.format("Expected directory but %s is a file", WORKSPACE_DIR.getAbsolutePath()));
        }
        storageDirs.addAll(Arrays.asList(WORKSPACE_DIR.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory() && HPU_DIRECTORY.matcher(file.getName()).matches();
            }
        })));
    }

    public void setArgs(String[] args) {
        this.args = args;
    }

    public File getStorageDirectory() {
        if (args.length == 1 && args[0].equals(STANDALONE_DIR)) {
            return new File(WORKSPACE_DIR, STANDALONE_DIR);
        }
        switch (storageDirs.size()) {
            case 0:
                return createHostPortDirectory(args);
            case 1:
                return storageDirs.get(0);
            default:
                return chooseDirectory(storageDirs);
        }
    }

    public static String getHostPort(File file) {
        Matcher matcher = getDirectoryMatcher(file);
        if ("80".equals(matcher.group(2))) {
            return getHostName(matcher);
        }
        else {
            return String.format("%s:%s", getHostName(matcher), matcher.group(2));
        }
    }

    public static boolean isStandalone(File file) {
        return file.getName().equals(STANDALONE_DIR);
    }

    public static String getUser(File file) {
        Matcher matcher = getDirectoryMatcher(file);
        return matcher.group(3);
    }

    public static String getHostPortUser(String fileName) {
        Matcher matcher = getDirectoryMatcher(fileName);
        return String.format("%s:%s/%s", getHostName(matcher), matcher.group(2), matcher.group(3));
    }

    public static URL getCodebase() {
        try {
            BasicService bs = (BasicService) ServiceManager.lookup("javax.jnlp.BasicService");
            return bs.getCodeBase();
        }
        catch (UnavailableServiceException ue) {
            throw new RuntimeException("Unable to use JNLP service", ue);
        }
    }

    // ======== private

    private static String getHostName(Matcher matcher) {
        return matcher.group(1).replace("_", ".");
    }

    private static Matcher getDirectoryMatcher(File file) {
        return getDirectoryMatcher(file.getName());
    }

    private static Matcher getDirectoryMatcher(String fileName) {
        Matcher matcher = HPU_DIRECTORY.matcher(fileName);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Directory name is incorrect " + fileName);
        }
        return matcher;
    }

    private static File createDirectory(String host, String port, String user) {
        String mungedHost = host.replaceAll("\\.", "_");
        String directoryName = String.format("%s__%s___%s", mungedHost, port, user);
        File directory = new File(WORKSPACE_DIR, directoryName);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new RuntimeException("Unable to create " + directory.getAbsolutePath());
        }
        return directory;
    }

    private static File createDirectory(String directoryName) {
        if (STANDALONE_DIR.equals(directoryName)) {
            File directory = new File(WORKSPACE_DIR, directoryName);
            if (!directory.exists() && !directory.mkdirs()) {
                throw new RuntimeException("Unable to create " + directory.getAbsolutePath());
            }
            return directory;
        }
        else {
            Matcher matcher = HPU_HUMAN.matcher(getHostPortUser(directoryName));
            if (matcher.matches()) {
                return createDirectory(matcher.group(1), matcher.group(2), matcher.group(3));
            }
            else {
                throw new RuntimeException("Expected host:port/user but got " + directoryName);
            }
        }
    }

    private static File createHostPortDirectory(String[] args) {
        if (args != null && args.length > 0) {
            String user = args[0].trim();
            URL codebase = getCodebase();
            String host = codebase.getHost();
            int port = codebase.getPort();
            if (port < 0) port = 80;
            return createDirectory(host, String.valueOf(port), user);
        }
        else while (true) {
            String answer = showInputDialog(null, "<html>Please enter host:port/user for your Culture Hub connection");
            if (answer == null) {
                throw new RuntimeException("No host:port/user, so stopping");
            }
            Matcher matcher = HPU_HUMAN.matcher(answer);
            if (matcher.matches()) {
                return createDirectory(matcher.group(1), matcher.group(2), matcher.group(3));
            }
            int okCancel = showConfirmDialog(null, "<html>Value does not match \"host:port/user\" pattern. Try again.", "Sorry", OK_CANCEL_OPTION);
            if (okCancel == CANCEL_OPTION) {
                throw new RuntimeException("No directory created");
            }
        }
    }

    private static File chooseDirectory(List<File> directories) {
        List<String> selectable = new ArrayList<String>();
        for (File directory : directories) {
            if (!(STANDALONE_DIR.equals(directory.getName()) || HPU_DIRECTORY.matcher(directory.getName()).find())) continue;
            selectable.add(directory.getName());
        }
        String preference = Preferences.userRoot().get(CHOSEN_DIRECTORY, "");
        JPanel buttonPanel = new JPanel(new GridLayout(0, 1));
        ButtonGroup buttonGroup = new ButtonGroup();
        for (String choice : selectable) {
            JRadioButton b = new JRadioButton(choice);
            b.setActionCommand(choice);
            if (choice.equals(preference)) b.setSelected(true);
            buttonGroup.add(b);
            buttonPanel.add(b);
        }
        int okCancel = showConfirmDialog(null, buttonPanel, "Choose server", OK_CANCEL_OPTION);
        if (okCancel != OK_OPTION) return null;
        String directoryChoice = buttonGroup.getSelection().getActionCommand();
        Preferences.userRoot().put(CHOSEN_DIRECTORY, directoryChoice);
        return createDirectory(directoryChoice);
    }
}
