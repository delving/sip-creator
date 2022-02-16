package eu.delving.sip.xml;

import eu.delving.stats.Stats;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.GZIPOutputStream;

public class NQuadWriter implements Runnable {

    public static class Input {

        private final byte[] nQad;
        private final byte[] xml;

        public Input(byte[] nQad, byte[] xml) {
            this.nQad = nQad;
            this.xml = xml;
        }

        private Input() {
            this(null, null);
        }

        private boolean isNull() {
            return nQad == null;
        }
    }

    private final Stats stats = new Stats();
    private final Thread thread = new Thread(this);
    private final Queue<Input> queue = new LinkedBlockingQueue<>();
    private boolean stop;
    private final Path outputDir;
    private Throwable error;

    public NQuadWriter(Path outputDir) {
        stats.maxUniqueValueLength = 1000;
        this.outputDir = outputDir;
    }

    public RuntimeException getError() {
        if (!(error instanceof RuntimeException)) {
            return new RuntimeException(error);
        }
        return (RuntimeException) error;
    }

    public boolean hasError() {
        return error != null;
    }

    public void put(Input input) throws InterruptedException {
        if (hasError()) {
            throw getError();
        }

        queue.add(input);
    }

    @Override
    public void run() {
        try (
            GZIPOutputStream nQadOut = new GZIPOutputStream(Files.newOutputStream(outputDir.resolve("output.nq.gz")), false);
            GZIPOutputStream xmlOut = new GZIPOutputStream(Files.newOutputStream(outputDir.resolve("output.xml.gz")), false);
        ) {

            while (!stop) {
                Input input = queue.poll();

                if (input != null) {
                    if (input.isNull()) {
                        stop = true;

                        nQadOut.finish();
                        nQadOut.flush();

                        xmlOut.finish();
                        xmlOut.flush();

                        try(GZIPOutputStream xmlStatsOut = new GZIPOutputStream(Files.newOutputStream(outputDir.resolve("output.stats.xml.gz")), false)) {
                            Stats.write(stats, xmlStatsOut);
                        }
                        break;
                    }

                    nQadOut.write(input.nQad);
                    if(input.xml != null) {
                        AnalysisParser.updateStats(stats, new ByteArrayInputStream(input.xml));
                        xmlOut.write(input.xml);
                    }
                }
            }

        } catch (Throwable e) {
            error = e;
        }
    }

    public void start() {
        thread.setName(getClass().getName());
        thread.start();
    }

    public void stop() {
        try {
            queue.add(new Input());
            thread.join();
        } catch (InterruptedException e) {
            // nothing to do
        }
    }
}