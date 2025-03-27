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

/**
 *
 * Thread-safe holder of the singleton Groovy scripting engine that processes our mappings
 * Intentional package access only.
 */
public class EngineHolder {

    private static final Logger LOG = LoggerFactory.getLogger(EngineHolder.class);

    private static final URL MAPPING_CATEGORY = EngineHolder.class.getResource("/MappingCategory.groovy");

    private static GroovyScriptEngineImpl INSTANCE;

    private EngineHolder() { }

    public static GroovyScriptEngineImpl getInstance(){
        if (INSTANCE == null) {
            synchronized (EngineHolder.class) {
                if (INSTANCE == null) {
                    LOG.debug("Initializing Groovy ScriptEngine");
                    GroovyClassLoader categoryLoader = new GroovyClassLoader(BulkMappingRunner.class.getClassLoader());
                    LOG.debug("Loading MappingCategory code");
                    String categoryCode = readResourceCode(MAPPING_CATEGORY);
                    categoryLoader.parseClass(categoryCode);
                    INSTANCE = new GroovyScriptEngineImpl(categoryLoader);
                }
            }
        }
        return INSTANCE;
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
