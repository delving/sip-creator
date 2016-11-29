package eu.delving.groovy;

import com.google.common.io.ByteStreams;
import eu.delving.groovy.ParallelBulkMappingRunner.SingleMappingJobResult;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.CompiledScript;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ParallelBulkMappingRunnerTest {
    private static Logger LOG = LoggerFactory.getLogger(ParallelBulkMappingRunnerTest.class);

    @Mock
    private RecMapping recMapping;

    @Mock
    private RecDefTree recDefTree;

    @Mock
    private RecDef recDef;

    private CompiledScript compiledScript;

    private ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    @Before
    public void before() throws Exception {
        when(recMapping.getRecDefTree()).thenReturn(recDefTree);
        when(recDefTree.getRecDef()).thenReturn(recDef);
        compiledScript = EngineHolder.getInstance().compile(BulkMappingRunnerTest.NON_RANDOM_CODE);
    }

    private MetadataRecord aRecord() {
        MetadataRecord metadataRecord = mock(MetadataRecord.class);
        GroovyNode rootGroovyNode = mock(GroovyNode.class);
        when(metadataRecord.getRootNode()).thenReturn(rootGroovyNode);
        return metadataRecord;
    }

    @Test
    public void testRunMany() throws Exception {
        int num = 100;
        final ArrayBlockingQueue<MetadataRecord> input = new ArrayBlockingQueue<>(1000);
        final ArrayBlockingQueue<SingleMappingJobResult> output = new ArrayBlockingQueue<>(1000);
        ParallelBulkMappingRunner runner = new ParallelBulkMappingRunner(input, output, compiledScript,
            recMapping, executorService);

        BufferedOutputStream diskSimulator = new BufferedOutputStream(ByteStreams.nullOutputStream());
        Thread processor = new Thread(runner);
        processor.start();

        SimulatedConsumer simulatedConsumer = new SimulatedConsumer(output, diskSimulator);
        Thread reader = new Thread(simulatedConsumer);
        reader.start();

        final long start = System.currentTimeMillis();
        for (int i = 0; i < num; i++) {
            input.put(aRecord());
        }
        LOG.debug("All {} records put on queue", num);

        MetadataRecord poisonPill = MetadataRecord.poisonPill();
        input.put(poisonPill);
        LOG.debug("Added poison pill", num);
        await().forever().until(simulatedConsumer::isDone);
        final long end = System.currentTimeMillis();
        LOG.debug("processed and wrote {} records in est. time of {} ms", num, (end - start));
    }

    private static class SimulatedConsumer implements Runnable {
        final BlockingQueue<SingleMappingJobResult> results;
        final BufferedOutputStream file;

        private boolean done = false;

        /**
         *
         * @return true once we've seen the poison pill
         */
        public boolean isDone() {
            return done;
        }

        public SimulatedConsumer(BlockingQueue<SingleMappingJobResult> results, BufferedOutputStream file) {
            this.results = results;
            this.file = file;
        }

        @Override
        public void run() {
            int i = 0;
            while(!done) {
                try {
                    SingleMappingJobResult take = results.take();
                    if (take.isPoison) {
                        LOG.debug("Poison!");
                        done = true;
                    } else {
                        try {
                            if (i % 1000 == 0) {
                                LOG.debug("Wrote {} items", i);
                            }
                            file.write(take.toString().getBytes());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                i++;
            }

        }
    }
}
