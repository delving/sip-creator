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

package eu.delving.sip;

import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.model.Feedback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pretend to be a progress listener
 *
 *
 */

public class MockProgressListener implements ProgressListener {
    private MockFeedback mockFeedback = new MockFeedback();
    private int progress;
    private List<String> alerts = new ArrayList<String>();

    @Override
    public void setProgressMessage(String message) {
//        System.out.println("message = " + message);
    }

    @Override
    public void prepareFor(int total) {
//        System.out.println("prepareFor = " + total);
    }

    @Override
    public void setProgress(int progress) {
        if (this.progress != progress) {
//            System.out.println("progress = " + progress);
        }
        this.progress = progress;
    }

    @Override
    public Feedback getFeedback() {
        return mockFeedback;
    }

    public List<String> getAlerts() {
        return alerts;
    }

    private class MockFeedback implements Feedback {

        @Override
        public void info(String message) {
            alerts.add(message);
        }

        @Override
        public void alert(String message) {
            alerts.add(message);
        }

        @Override
        public void alert(String message, Throwable throwable) {
            alerts.add(message + " throwable:"+throwable.toString());
        }

        @Override
        public String ask(String question) {
            return "no";
        }

        @Override
        public String ask(String question, String defaultValue) {
            return defaultValue;
        }

        @Override
        public boolean confirm(String title, String message) {
            return true;
        }

        @Override
        public boolean form(String title, Object... components) {
            return false;
        }

        @Override
        public String getHubPassword() {
            return "pw";
        }

        @Override
        public boolean getNarthexCredentials(Map<String,String> fields) {
            return false;
        }
    }
}
