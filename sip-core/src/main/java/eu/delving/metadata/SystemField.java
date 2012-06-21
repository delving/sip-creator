/*
 * Copyright 2011, 2012 Delving BV
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.metadata;

/**
 * The fields that the hub system knows about.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public enum SystemField {
    TITLE("delving_title"),
    DESCRIPTION("delving_description"),
    PROVIDER("delving_provider"),
    OWNER("delving_owner"),
    CREATOR("delving_creator"),
    THUMBNAIL("delving_thumbnail"),
    LANDING_PAGE("delving_landingPage"),
    DEEP_ZOOM_URL("delving_deepZoomUrl"),
    SPEC("delving_spec");

    private final String tag;

    private SystemField(String tag) {
        this.tag = tag;
    }

    public String getTag() {
        return tag;
    }
}
