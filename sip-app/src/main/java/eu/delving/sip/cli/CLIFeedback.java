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

package eu.delving.sip.cli;

import eu.delving.sip.model.Feedback;

import java.util.Arrays;
import java.util.Map;

public class CLIFeedback implements Feedback {

    @Override
    public void info(String message) {
        System.out.println("info: " + message);
    }

    @Override
    public void alert(String message) {
        System.err.println("alert: " + message);
    }

    @Override
    public void alert(String message, Throwable throwable) {
        System.err.println("alert: " + message);
        System.err.println(throwable.getMessage());
        throwable.printStackTrace(System.err);
    }

    @Override
    public String ask(String question) {
        throw new IllegalStateException();
    }

    @Override
    public String ask(String question, String defaultValue) {
        throw new IllegalStateException();
    }

    @Override
    public boolean confirm(String title, String message) {
        throw new IllegalStateException();
    }

    @Override
    public boolean form(String title, Object... components) {
        System.out.println("form: title=" + title + ", components=" + Arrays.toString(components));
        return false;
    }

    @Override
    public String getHubPassword() {
        throw new IllegalStateException();
    }

    @Override
    public boolean getNarthexCredentials(Map<String, String> fields) {
        throw new IllegalStateException();
    }
}
