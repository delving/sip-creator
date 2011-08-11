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

import java.util.List;

/**
 * todo: add description
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class DesktopStateImpl implements DesktopPreferences.DesktopState {

    private String spec;
    private List<WindowState> windowStates;

    public DesktopStateImpl(String spec, List<WindowState> windowStates) {
        this.spec = spec;
        this.windowStates = windowStates;
    }

    @Override
    public String getSpec() {
        return spec;
    }

    @Override
    public List<WindowState> getWindows() {
        return windowStates;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DesktopStateImpl that = (DesktopStateImpl) o;
        return !(spec != null ? !spec.equals(that.spec) : that.spec != null);
    }

    @Override
    public int hashCode() {
        return spec != null ? spec.hashCode() : 0;
    }
}
