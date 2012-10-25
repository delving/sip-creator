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
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileFilter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Locate the workspace and make sure the user has chosen a storage directory
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class StorageFinder {
    private static final File WORKSPACE_DIR = new File(System.getProperty("user.home"), "DelvingSIPCreator");
    private static final Pattern HPU_HUMAN = Pattern.compile("([A-Za-z0-9.-]+):([0-9]+)/([A-Za-z0-9]+)");
    private static final Pattern HPU_DIRECTORY = Pattern.compile("([A-Za-z0-9_-]+)__([0-9]+)___([A-Za-z0-9]+)");
    public static final String CHOSEN_DIRECTORY = "chosenDirectory";

    public static File getStorageDirectory(String[] args) {
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
                return createHostPortDirectory(args);
            case 1:
                return files[0];
            default:
                return chooseDirectory(files);
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
            throw new RuntimeException("Unable to create " + directory.getAbsolutePath());
        }
        return directory;
    }

    private static File createDirectory(String hostPortUser) {
        Matcher matcher = HPU_HUMAN.matcher(hostPortUser);
        if (matcher.matches()) {
            return createDirectory(matcher.group(1), matcher.group(2), matcher.group(3));
        }
        else {
            throw new RuntimeException("Expected host:port/user but got " + hostPortUser);
        }
    }

    private static File createHostPortDirectory(String[] args) {
        if (args.length > 0) {
            String user = args[0].trim();
            URL codebase = getCodebase();
            String host = codebase.getHost();
            int port = codebase.getPort();
            if (port < 0) port = 80;
            return createDirectory(host, String.valueOf(port), user);
        }
        else while (true) {
            String answer = JOptionPane.showInputDialog(null, "<html>Please enter host:port/user for your Culture Hub connection");
            if (answer == null) {
                throw new RuntimeException("No host:port/user, so stopping");
            }
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
        List<String> selectable = new ArrayList<String>();
        for (File directory : directories) {
            if (!HPU_DIRECTORY.matcher(directory.getName()).find()) continue;
            selectable.add(getHostPortUser(directory));
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
        int okCancel = JOptionPane.showConfirmDialog(null, buttonPanel, "Choose server", JOptionPane.OK_CANCEL_OPTION);
        if (okCancel == JOptionPane.CANCEL_OPTION) return null;
        String hostPort = buttonGroup.getSelection().getActionCommand();
        Preferences.userRoot().put(CHOSEN_DIRECTORY, hostPort);
        return createDirectory(hostPort);
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
}
