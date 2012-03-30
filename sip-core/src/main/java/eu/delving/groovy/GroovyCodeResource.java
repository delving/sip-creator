/*
 * Copyright 2011 DELVING BV
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.groovy;

import eu.delving.metadata.MappingFunction;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.lang.GroovySystem;
import groovy.lang.Script;

import java.io.*;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;

/**
 * This class is supposed to make it easy to build Groovy scripts from some bits of code which are
 * stored in the resources of the project.
 * <p/>
 * For DSL aspects, the MappingCategory is automatically wrapped around the builder code
 * which does the mapping transformation.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class GroovyCodeResource {
    private static final URL MAPPING_CATEGORY = GroovyCodeResource.class.getResource("/MappingCategory.groovy");
    private final ClassLoader classLoader;
    private GroovyClassLoader groovyClassLoader;

    public GroovyCodeResource(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }
    
    public Script createFunctionScript(MappingFunction function, Map<String,String> facts, String editedCode) {
        StringBuilder scriptCode = new StringBuilder();
        for (Map.Entry<String,String> entry : facts.entrySet()) {
            scriptCode.append(String.format("String %s = '''%s'''\n", entry.getKey(), entry.getValue()));
        }
        scriptCode.append("String _uniqueIdentifier = 'UNIQUE_IDENTIFIER'\n");
        scriptCode.append(function.toCode(editedCode));
        scriptCode.append(String.format("%s(param)\n", function.name));
        return new GroovyShell(getGroovyClassLoader()).parse(scriptCode.toString());
    }

    public Script createMappingScript(String code) {
        return new GroovyShell(getGroovyClassLoader()).parse(code);
    }

    private GroovyClassLoader getGroovyClassLoader() {
        if (groovyClassLoader == null) {
            try {
                ClassLoader classLoader = this.classLoader;
                GroovyClassLoader categoryClassLoader = new GroovyClassLoader(classLoader);
                String categoryCode = readResourceCode(MAPPING_CATEGORY);
                categoryClassLoader.parseClass(categoryCode);
                groovyClassLoader = new GroovyClassLoader(categoryClassLoader); 
            }
            catch (Exception e) {
                throw new RuntimeException("Cannot initialize Groovy Code Resource", e);
            }
        }
        groovyClassLoader.clearCache();
        return groovyClassLoader;
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

    public void flush() {
        // Groovy generates classes for each script evaluation
        // this ends up eating up all permGen space
        // thus we clear the caches referencing those classes so that GC can remove them

        // additionally Groovy also at each script evaluation generates instances of MetaMethodIndex$Elem
        // those are SoftReferences so they only disappear when the used memory reaches its max allowed heap
        // but they also pretty much impact on the execution time, probably because method cache lookup time increases
        // (maybe because of a poorly implemented equals() & hashcode() implementation)
        // thus in order to get rid of this performance impact we need a reasonabily low -XX:MaxPermSize
        // yet it can't be too low because otherwise Groovy won't be able to generate its classes anymore
        // this is why we now clear those every 50 iterations.

        GroovySystem.setKeepJavaMetaClasses(false);
        for (Iterator it = GroovySystem.getMetaClassRegistry().iterator(); it.hasNext(); ) it.remove();
//        groovyClassLoader.clearCache();
    }
}
