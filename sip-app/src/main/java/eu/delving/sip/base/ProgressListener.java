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

package eu.delving.sip.base;

/**
 * Ties a process to a ProgressMonitor
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

public interface ProgressListener {

    long PATIENCE = 250;

    void setProgressMessage(String message);

    void setIndeterminateMessage(String message);

    void setProgressString(String message);

    void prepareFor(int total);

    boolean setProgress(int progress);

    void finished(boolean success);

    void onFinished(End end);

    public interface End {
        void finished(ProgressListener progressListener, boolean success);
    }
}
