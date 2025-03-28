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

import java.util.Map;

/**
 * Through this interface, the code can interact with the user.  Pop-up dialogs can give alerts or ask questions
 * in a number of ways.
 *
 *
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
