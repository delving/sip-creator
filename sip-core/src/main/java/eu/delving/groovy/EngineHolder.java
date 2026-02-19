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

package eu.delving.groovy;

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.jsr223.GroovyScriptEngineImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe holder of the singleton Groovy scripting engine that processes our mappings.
 *
 * Includes automatic reset functionality to prevent Metaspace exhaustion from accumulated
 * compiled Groovy classes. When the compilation count reaches the threshold, the engine
 * is reset and the old classloader becomes eligible for garbage collection.
 */
public class EngineHolder {

    private static final Logger LOG = LoggerFactory.getLogger(EngineHolder.class);

    private static final URL MAPPING_CATEGORY = EngineHolder.class.getResource("/MappingCategory.groovy");

    // Volatile for proper visibility across threads
    private static volatile GroovyScriptEngineImpl INSTANCE;

    // Track number of script compilations since last reset
    private static final AtomicInteger compilationCount = new AtomicInteger(0);

    // Reset after N unique script compilations to prevent Metaspace exhaustion
    // Set to 0 to disable automatic reset
    private static int maxCompilationsBeforeReset = 100;

    private EngineHolder() { }

    /**
     * Get the singleton Groovy script engine instance.
     * Thread-safe with double-checked locking.
     */
    public static GroovyScriptEngineImpl getInstance() {
        if (INSTANCE == null) {
            synchronized (EngineHolder.class) {
                if (INSTANCE == null) {
                    INSTANCE = createNewEngine();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Call this after compiling a new script to track compilation count.
     * Will trigger engine reset if threshold is reached.
     */
    public static void notifyCompilation() {
        if (maxCompilationsBeforeReset > 0) {
            int count = compilationCount.incrementAndGet();
            if (count >= maxCompilationsBeforeReset) {
                LOG.info("Compilation count {} reached threshold {}, resetting engine",
                    count, maxCompilationsBeforeReset);
                reset();
            }
        }
    }

    /**
     * Force reset of the engine. The old engine and its classloader
     * become eligible for garbage collection, freeing Metaspace.
     */
    public static synchronized void reset() {
        if (INSTANCE != null) {
            LOG.info("Resetting GroovyScriptEngine to free Metaspace");
            INSTANCE = null;
            compilationCount.set(0);
            // Let JVM handle GC timing naturally. Explicit System.gc() can evict
            // font glyph caches held by SoftReferences, which combined with the
            // Metal pipeline bug (JDK-8349701) causes progressive font rendering
            // corruption on macOS Apple Silicon.
        }
    }

    /**
     * Configure the compilation threshold for automatic reset.
     * @param threshold Number of compilations before reset (0 to disable)
     */
    public static void setResetThreshold(int threshold) {
        maxCompilationsBeforeReset = threshold;
        LOG.info("EngineHolder reset threshold set to {}", threshold);
    }

    /**
     * Get current compilation count since last reset.
     */
    public static int getCompilationCount() {
        return compilationCount.get();
    }

    /**
     * Create a new Groovy script engine with MappingCategory loaded.
     */
    private static GroovyScriptEngineImpl createNewEngine() {
        LOG.debug("Initializing Groovy ScriptEngine");
        GroovyClassLoader categoryLoader = new GroovyClassLoader(BulkMappingRunner.class.getClassLoader());
        LOG.debug("Loading MappingCategory code");
        String categoryCode = readResourceCode(MAPPING_CATEGORY);
        categoryLoader.parseClass(categoryCode);
        return new GroovyScriptEngineImpl(categoryLoader);
    }

    private static String readResourceCode(URL resource) {
        try {
            InputStream in = resource.openStream();
            Reader reader = new InputStreamReader(in);
            return readCode(reader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String readCode(Reader reader) {
        BufferedReader in = new BufferedReader(reader);
        StringBuilder out = new StringBuilder();
        String line;
        try {
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("//")) {
                    continue;
                }
                out.append(line).append('\n');
            }
            in.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return out.toString();
    }
}
