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

/**
 * todo: add description
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class WorkspaceImpl implements DesktopPreferences.Workspace {

    private String workspacePath;

    public WorkspaceImpl(String workspacePath) {
        this.workspacePath = workspacePath;
    }

    @Override
    public String getWorkspacePath() {
        return workspacePath;
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
