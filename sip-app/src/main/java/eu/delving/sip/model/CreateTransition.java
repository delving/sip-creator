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

package eu.delving.sip.model;

/**
 * Describe the transitions that can take place in the create model
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public enum CreateTransition {

    NOTHING_TO_SOURCE(true, false, false), // source set
    SOURCE_TO_NOTHING(true, false, false), // source set
    SOURCE_TO_SOURCE(true, false, false), // source to something new

    NOTHING_TO_TARGET(false, true, false), // target set
    TARGET_TO_NOTHING(false, true, false), // target set
    TARGET_TO_TARGET(false, true, false),

    ARMED_TO_TARGET(true, false, false), // source cleared
    ARMED_TO_SOURCE(false, true, false), // target cleared

    ARMED_TO_ARMED_SOURCE(true, false, false),
    ARMED_TO_ARMED_TARGET(false, true, false),

    ARMED_TO_COMPLETE_SOURCE(true, false, true),
    ARMED_TO_COMPLETE_TARGET(false, true, true),

    SOURCE_TO_ARMED(false, true, false), // target set
    TARGET_TO_ARMED(true, false, false), // source set

    COMPLETE_TO_ARMED_SOURCE(true, false, true), // source set and nodeMapping cleared
    COMPLETE_TO_ARMED_TARGET(false, true, true), // target set and nodeMapping cleared

    NOTHING_TO_COMPLETE(true, true, true), // node set
    COMPLETE_TO_COMPLETE(true, true, true),

    TARGET_TO_COMPLETE(true, false, true), // source and nodeMapping set
    SOURCE_TO_COMPLETE(false, true, true), // target and nodeMapping set

    CREATE_COMPLETE(false, false, true); // only nodeMapping set

    public final boolean sourceChanged, targetChanged, nodeMappingChanged;

    private CreateTransition(boolean sourceChanged, boolean targetChanged, boolean nodeMappingChanged) {
        this.sourceChanged = sourceChanged;
        this.targetChanged = targetChanged;
        this.nodeMappingChanged = nodeMappingChanged;
    }
}
