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

package eu.delving.sip.desktop.windows;

/**
 * The menus in the menu bar are generated from these values. Add a new group and pass it as menuGroup to WindowId.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 * @see WindowId
 */
public enum MenuGroup {

    FILE("File"),
    ACCOUNT("Account"),
    HELP("Help");

    private String title;

    MenuGroup(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
