/*
 * Copyright 2010 DELVING BV
 *
 * Licensed under the EUPL, Version 1.0 or as soon they
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

package eu.delving.sip.desktop;

import org.apache.log4j.Logger;

import java.io.File;
import java.io.FilenameFilter;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * todo: add description
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class WorkspaceImpl implements DesktopPreferences.Workspace {

    transient private static final String DIRECTORY_HOST_FORMAT = "([A-Za-z0-9_]+)_([0-9]+)";
    transient private static final Logger LOG = Logger.getRootLogger();

    transient private Pattern pattern = Pattern.compile(DIRECTORY_HOST_FORMAT);
    transient private FilenameFilter filenameFilter = new FilenameFilter() {
        @Override
        public boolean accept(File file, String s) {
            Matcher matcher = pattern.matcher(s);
            return matcher.matches() && new File(file, s).isDirectory();
        }
    };

    private String workspacePath;

    public WorkspaceImpl(String workspacePath) {
        this.workspacePath = workspacePath;
    }

    @Override
    public String getWorkspacePath() {
        return workspacePath;
    }

    @Override
    public Set<String> getHostDirectories() {
        Set<String> fileNames = new HashSet<String>();
        for (File file : new File(workspacePath).listFiles(filenameFilter)) {
            String name = file.getName();
            Pattern pattern = Pattern.compile(DIRECTORY_HOST_FORMAT);
            Matcher matcher = pattern.matcher(name);
            if (matcher.matches()) {
                fileNames.add(String.format("%s:%s", matcher.group(1).replace("_", "."), matcher.group(2)));
            }
        }
        return fileNames;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkspaceImpl workspace = (WorkspaceImpl) o;
        return !(workspacePath != null ? !workspacePath.equals(workspace.workspacePath) : workspace.workspacePath != null);
    }

    @Override
    public int hashCode() {
        return workspacePath != null ? workspacePath.hashCode() : 0;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("WorkspaceImpl");
        sb.append("{workspacePath='").append(workspacePath).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
