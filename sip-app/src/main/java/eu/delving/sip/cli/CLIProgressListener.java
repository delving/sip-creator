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

import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.model.Feedback;

public class CLIProgressListener implements ProgressListener {
    private int lastPrintedPercentage = -1;
    private String currentMessage = "";
    private Long startTime = null;
    private int totalSteps;
    private int currentStep;

    @Override
    public void prepareFor(int steps) {
        this.totalSteps = steps;
        System.out.println("Preparing to process " + steps + " steps...");
    }

    @Override
    public Feedback getFeedback() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setProgress(int percentage) {
        if (startTime == null) {
            startTime = System.currentTimeMillis();
        }

        if (percentage != lastPrintedPercentage) {
            this.currentStep = percentage;
            if (percentage % 1000 == 0) {
                printProgress();
            }
            lastPrintedPercentage = percentage;
        }
    }

    @Override
    public void setProgressMessage(String message) {
        this.currentMessage = message;
        System.out.println(message);
    }

    private void printProgress() {
        long currentTime = System.currentTimeMillis();
        long elapsedTimeMs = currentTime - startTime;
        double elapsedTimeSeconds = elapsedTimeMs / 1000.0;

        // Calculate records per second
        double recordsPerSecond = currentStep / elapsedTimeSeconds;

        // Format elapsed time into hours:minutes:seconds
        long hours = elapsedTimeMs / (3600 * 1000);
        long minutes = (elapsedTimeMs % (3600 * 1000)) / (60 * 1000);
        long seconds = (elapsedTimeMs % (60 * 1000)) / 1000;

        System.out.printf("\rProgress: %d | Time: %02d:%02d:%02d | Avg: %.2f records/sec%n",
                currentStep,
                hours,
                minutes,
                seconds,
                recordsPerSecond);
    }
}
