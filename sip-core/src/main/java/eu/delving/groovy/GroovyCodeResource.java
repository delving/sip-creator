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

import eu.delving.metadata.Assertion;
import eu.delving.metadata.MappingFunction;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.lang.GroovySystem;
import groovy.lang.Script;

import java.io.*;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is supposed to make it easy to build Groovy scripts from some bits of code which are
 * stored in the resources of the project.
 * <p/>
 * For DSL aspects, the MappingCategory is automatically wrapped around the builder code
 * which does the mapping transformation.
 *
 */
public class GroovyCodeResource {
    private static final URL MAPPING_CATEGORY = GroovyCodeResource.class.getResource("/MappingCategory.groovy");
    private final ClassLoader classLoader;
    private GroovyClassLoader categoryClassLoader;
    private GroovyClassLoader childClassLoader;
    private Map<Long, Script> mappingScriptsByCode = Collections.synchronizedMap(new HashMap<>());

    public GroovyCodeResource(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public Script createValidationScript(Assertion assertion) {
        String script = assertion.getScript();
        return new GroovyShell(getGroovyClassLoader()).parse(script);
    }

    public Script createFunctionScript(MappingFunction function, Map<String,String> facts, String editedCode) {
        StringBuilder scriptCode = new StringBuilder();
        for (Map.Entry<String,String> entry : facts.entrySet()) {
            String value = entry.getValue();
            if (value != null) {
                value = value.replace("'", "\\'");
            }
            scriptCode.append(String.format("String %s = '''%s'''%n", entry.getKey(), value));
        }
        scriptCode.append(String.format("String _uniqueIdentifier = 'UNIQUE_IDENTIFIER'%n"));
        scriptCode.append(function.toCode(editedCode));
        scriptCode.append(String.format("%s(param)%n", function.name));
        return new GroovyShell(getGroovyClassLoader()).parse(scriptCode.toString());
    }

    public Script createMappingScript(String code) {
        long codeId = code.hashCode() + Thread.currentThread().getId();
        Script script = mappingScriptsByCode.get(codeId);
        if(script != null) {
            return script;
        }

        GroovyShell groovyShell = new GroovyShell(getGroovyClassLoader());
        script = groovyShell.parse(code);

        mappingScriptsByCode.put(codeId, script);
        return script;
    }

    public void clearMappingScripts() {
        mappingScriptsByCode.clear();
    }

    /**
     * Reset the child classloader, forcing a fresh one on next compilation.
     * Call this when the mapping context changes (e.g., new mapping loaded).
     */
    public synchronized void resetClassLoader() {
        childClassLoader = null;
        mappingScriptsByCode.clear();
    }

    private synchronized GroovyClassLoader getGroovyClassLoader() {
        if (categoryClassLoader == null) {
            try {
                categoryClassLoader = new GroovyClassLoader(this.classLoader);
                String categoryCode = readResourceCode(MAPPING_CATEGORY);
                categoryClassLoader.parseClass(categoryCode);
            }
            catch (Exception e) {
                throw new RuntimeException("Cannot initialize Groovy Code Resource", e);
            }
        }
        // Reuse the child classloader to avoid excessive classloader churn.
        // Creating a new GroovyClassLoader on every compilation causes Metaspace
        // pressure that triggers GC, which evicts font glyph caches.
        if (childClassLoader == null) {
            childClassLoader = new GroovyClassLoader(categoryClassLoader);
        }
        return childClassLoader;
    }

    private String readResourceCode(URL resource) throws IOException {
        InputStream in = resource.openStream();
        Reader reader = new InputStreamReader(in);
        return readCode(reader);
    }

    private String readCode(Reader reader) throws IOException {
        BufferedReader in = new BufferedReader(reader);
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("//")) {
                continue;
            }
            out.append(line).append('\n');
        }
        in.close();
        return out.toString();
    }

    static {
        GroovySystem.setKeepJavaMetaClasses(false);
    }
}
