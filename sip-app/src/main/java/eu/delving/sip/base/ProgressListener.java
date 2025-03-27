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

package eu.delving.sip.base;

import eu.delving.sip.model.Feedback;

/**
 * Allows for progress reporting from a long term process to the work model
 *
 *
 */

public interface ProgressListener {

    void setProgressMessage(String message);

    void prepareFor(int total);

    void setProgress(int progress) throws CancelException;

    Feedback getFeedback();

}
