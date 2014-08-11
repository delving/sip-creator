/*
 * Copyright 2012 Delving BV
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

package eu.delving.sip.frames;

import eu.delving.sip.base.SwingHelper;

import javax.swing.*;

import static eu.delving.sip.base.SwingHelper.ICON_DOWNLOAD;
import static eu.delving.sip.base.SwingHelper.ICON_HUH;
import static eu.delving.sip.base.SwingHelper.ICON_OWNED;
import static eu.delving.sip.base.SwingHelper.ICON_UNAVAILABLE;

/**
 * @author Gerald de Jong <gerald@delving.eu>
 */

public enum HubDataSetState {
    OWNED_BY_YOU(true, true, "yours", ICON_OWNED),
    AVAILABLE(false, true, "free", ICON_DOWNLOAD),
    UNAVAILABLE(false, true, "taken", ICON_UNAVAILABLE),
    BUSY(false, false, "busy", SwingHelper.ICON_BUSY),
    ORPHAN_TAKEN(true, true, "taken/local", ICON_HUH),
    ORPHAN_LONELY(true, false, "only local", ICON_HUH),
    ORPHAN_UPDATE(false, true, "yours notlocal", ICON_HUH),
    ORPHAN_ARCHIVE(true, true, "free/local", ICON_HUH),
    NEEDS_FETCH(true, false, "fetch", ICON_OWNED);

    public final String string;
    public final Icon icon;
    public final boolean selectable;
    public final boolean downloadable;

    HubDataSetState(boolean selectable, boolean downloadable, String string, Icon icon) {
        this.selectable = selectable;
        this.downloadable = downloadable;
        this.string = string;
        this.icon = icon;
    }

    public String toString() {
        return String.format("%s: %s", super.toString(), string);
    }
}
