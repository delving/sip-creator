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

package eu.delving.sip.grpc;

import eu.delving.sip.model.Feedback;

import java.util.Map;

import eu.delving.sip.base.CancelException;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.cli.SIPFilesFinder.SIPFiles;
import io.grpc.stub.StreamObserver;

// Import the generated protobuf classes
import eu.delving.sip.grpc.MappingProgress;
import eu.delving.sip.grpc.ProcessingStatus;
import eu.delving.sip.grpc.InitializationStatus;
import eu.delving.sip.grpc.CompletionStatus;

public class GrpcProgressTracker implements ProgressListener, StreamObserver<MappingProgress> {
    private final StreamObserver<MappingProgress> responseObserver;
    private final Feedback feedback;
    private final int updateFrequency; // How often to send updates (in records)
    private long startTime;
    private long initializationTime;
    private int totalRecords;
    private long lastUpdateTime;
    private int lastRecordCount;

    public GrpcProgressTracker(StreamObserver<MappingProgress> responseObserver, int updateFrequency) {
        this.responseObserver = responseObserver;
        this.startTime = System.currentTimeMillis();
        this.lastUpdateTime = startTime;
        this.feedback = new NoOpFeedback();
        this.updateFrequency = Math.max(1, updateFrequency); // Ensure minimum of 1
    }

    private String formatElapsedTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        seconds = seconds % 60;
        minutes = minutes % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    // Constructor overload for default update frequency
    public GrpcProgressTracker(StreamObserver<MappingProgress> responseObserver) {
        this(responseObserver, 1000); // Default to updating every 100 records
    }

    @Override
    public void setProgressMessage(String message) {
        ProcessingStatus status = ProcessingStatus.newBuilder()
                .setCurrentOperation(message)
                .setRecordsProcessed(0)
                .build();

        MappingProgress progress = MappingProgress.newBuilder()
                .setProcessing(status)
                .build();

        responseObserver.onNext(progress);
    }

    @Override
    public void prepareFor(int total) {
        this.totalRecords = total;
        ProcessingStatus status = ProcessingStatus.newBuilder()
                .setTotalRecords(total)
                .setRecordsProcessed(0)
                .setPercentageComplete(0)
                .setCurrentOperation("Starting processing...")
                .build();

        MappingProgress progress = MappingProgress.newBuilder()
                .setProcessing(status)
                .build();

        responseObserver.onNext(progress);
    }

    @Override
    public Feedback getFeedback() {
        return feedback;
    }

    @Override
    public void onNext(MappingProgress value) {
        responseObserver.onNext(value);
    }

    @Override
    public void onError(Throwable t) {
        responseObserver.onError(t);
    }

    @Override
    public void onCompleted() {
        responseObserver.onCompleted();
    }

    @Override
    public void setProgress(int progressValue) throws CancelException {
        // Only send update if we've processed updateFrequency records or it's the
        // first/last record
        if (progressValue % updateFrequency != 0 && progressValue != totalRecords && progressValue != 0) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;

        // Calculate records per second
        double recordsPerSecond = 0.0;
        if (elapsedTime > 0) {
            recordsPerSecond = (progressValue * 1000.0) / elapsedTime;
        }

        // Calculate percentage
        double percentage = totalRecords > 0 ? (progressValue * 100.0) / totalRecords : 0;

        ProcessingStatus status = ProcessingStatus.newBuilder()
                .setRecordsProcessed(progressValue)
                .setRecordsPerSecond(recordsPerSecond)
                .setElapsedTimeMs(elapsedTime)
                .setCurrentOperation(String.format(
                        "Processing... %.2f records/sec, Time elapsed: %s",
                        recordsPerSecond,
                        formatElapsedTime(elapsedTime)))
                .setTotalRecords(totalRecords)
                .setPercentageComplete(percentage)
                .build();

        MappingProgress progress = MappingProgress.newBuilder()
                .setProcessing(status)
                .build();

        responseObserver.onNext(progress);

        // Debug output
        System.out.printf("Progress update: %d records, %.2f rec/sec, elapsed: %s%n",
                progressValue, recordsPerSecond, formatElapsedTime(elapsedTime));

        // Update last metrics
        lastUpdateTime = currentTime;
        lastRecordCount = progressValue;

    }

    public void onComplete() {
        long totalTime = System.currentTimeMillis() - startTime;
        long processingTime = totalTime - initializationTime;

        if (totalRecords == 0 && lastRecordCount > 0) {
            // If no records were processed, set total time to initialization time
            totalRecords = lastRecordCount;
        }

        // Calculate average records per second
        double averageRecordsPerSecond = 0.0;
        if (processingTime > 0) {
            averageRecordsPerSecond = (totalRecords * 1000.0) / processingTime;
        }

        CompletionStatus status = CompletionStatus.newBuilder()
                .setTotalTimeMs(totalTime)
                .setProcessingTimeMs(processingTime)
                .setTotalRecordsProcessed(totalRecords)
                .setAverageRecordsPerSecond(averageRecordsPerSecond)
                .build();

        MappingProgress progress = MappingProgress.newBuilder()
                .setCompletion(status)
                .build();

        responseObserver.onNext(progress);

        // Debug output
        System.out.printf("Completion: %d records total, %.2f avg rec/sec, total time: %s%n",
                totalRecords, averageRecordsPerSecond, formatElapsedTime(totalTime));
    }

    // No-op implementation of Feedback
    private static class NoOpFeedback implements Feedback {
        @Override
        public void info(String message) {
            // No-op
        }

        @Override
        public void alert(String message, Throwable throwable) {
            // TODO Auto-generated method stub

        }

        @Override
        public void alert(String message) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'alert'");
        }

        @Override
        public String ask(String question) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'ask'");
        }

        @Override
        public String ask(String question, String defaultValue) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'ask'");
        }

        @Override
        public boolean confirm(String title, String message) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'confirm'");
        }

        @Override
        public boolean form(String title, Object... components) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'form'");
        }

        @Override
        public String getHubPassword() {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'getHubPassword'");
        }

        @Override
        public boolean getNarthexCredentials(Map<String, String> fields) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'getNarthexCredentials'");
        }
    }

    public void onInitializationComplete(SIPFiles sipFiles, long initTime) {
        this.initializationTime = initTime;

        InitializationStatus status = InitializationStatus.newBuilder()
                .setRecordDefinition(sipFiles.getRecordDefinition().toString())
                .setValidationSchema(sipFiles.getValidationSchema().toString())
                .setMappingFile(sipFiles.getMappingFile().toString())
                .setSourceFile(sipFiles.getSourceFile().toString())
                .setInitializationTimeMs(initTime)
                .build();

        MappingProgress progress = MappingProgress.newBuilder()
                .setInitialization(status)
                .build();

        responseObserver.onNext(progress);
    }
}
