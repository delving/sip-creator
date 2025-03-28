/*
 * Copyright 2011-2025 Delving BV
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package eu.delving.sip.model;

/**
 * Describe the create model at any given time.
 *
 *
 */

public enum CreateState {
    NOTHING(false, false, false),
    SOURCE_ONLY(true, false, false),
    TARGET_ONLY(false, true, false),
    SOURCE_AND_TARGET(true, true, false),
    COMPLETE(true, true, true);

    public final boolean source, target, node;

    CreateState(boolean source, boolean target, boolean node) {
        this.source = source;
        this.target = target;
        this.node = node;
    }
}
