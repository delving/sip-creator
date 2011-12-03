/*
 * Copyright 2011 DELVING BV
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
 * The fields that will be considered common to all record definitions are marked by this
 * in the record definition.
 *
 * todo: this enum has yet to be integrated into the new RecDef, and it should be done
 * todo: in the form of path -> enum entries so that the list of fields is easy to
 * todo: read and modify and can point to anywhere in the RecDef.
 * 
 * The following summary fields cannot be set in the Record Definition TYPE, HUB_ID, VISIBILITY, OWNER.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public enum SummaryField {

    CREATOR,
    TITLE,
    DESCRIPTION,
    SNIPPET,
    THUMBNAIL,
    TYPE("recordType"),
    HUB_ID("hubId"),
    VISIBILITY,
    OWNER;

    private static final String NS = "delving_";

    public final String tag;

    SummaryField() {
        this.tag = NS + name().toLowerCase();
    }

    SummaryField(String tag) {
        this.tag = NS + tag;
    }
}
