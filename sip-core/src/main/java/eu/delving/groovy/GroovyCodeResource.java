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

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

import java.io.*;
import java.net.URL;

/**
 * This class is supposed to make it easy to build Groovy scripts from some bits of code which are
 * stored in the resources of the project.
 *
 * For DSL aspects, the MappingCategory is automatically wrapped around the builder code
 * which does the mapping transformation.
 *
 * Various helper functions are automatically added to make record validation nicer.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class GroovyCodeResource {
    private static final URL MAPPING_CATEGORY = GroovyCodeResource.class.getResource("/MappingCategory.groovy");
    private static final URL VALIDATION_HELPERS = GroovyCodeResource.class.getResource("/ValidationHelpers.groovy");
    private final ClassLoader classLoader;

    public GroovyCodeResource(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public Script createMappingScript(String code) {
        return getCategoryShell().parse(code);
    }

    public GroovyShell getCategoryShell() {
        try {
            ClassLoader classLoader = this.classLoader;
            GroovyClassLoader mappingClassLoader = new GroovyClassLoader(classLoader);
            String categoryCode = readResourceCode(MAPPING_CATEGORY);
            mappingClassLoader.parseClass(categoryCode);
            return new GroovyShell(mappingClassLoader);
        } catch (Exception e) {
            throw new RuntimeException("Cannot initialize Groovy Code Resource", e);
        }
    }

    public Script createValidationScript(String code) {
        try {
            String validationHelperCode = readResourceCode(VALIDATION_HELPERS);
            return new GroovyShell(getClass().getClassLoader()).parse(validationHelperCode + "\n//=========\n" + code);
        }
        catch (Exception e) {
            throw new RuntimeException("Cannot initialize Groovy Code Resource", e);
        }
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
}
