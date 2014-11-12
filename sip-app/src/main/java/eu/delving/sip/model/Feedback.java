/*
 * Copyright 2011, 2012 Delving BV
 *
 * Licensed under the EUPL, Version 1.0 or? as soon they
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

package eu.delving.sip.model;

import java.util.Map;

/**
 * Through this interface, the code can interact with the user.  Pop-up dialogs can give alerts or ask questions
 * in a number of ways.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public interface Feedback {

    void info(String message);

    void alert(String message);

    void alert(String message, Throwable throwable);

    String ask(String question);

    String ask(String question, String defaultValue);

    boolean confirm(String title, String message);

    boolean form(String title, Object ... components);

    String getHubPassword();

    boolean getNarthexCredentials(Map<String,String> fields);

    public interface Log {
        void log(String message, Throwable throwable);
    }
}
