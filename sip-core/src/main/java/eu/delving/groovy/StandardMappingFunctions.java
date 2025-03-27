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

import eu.delving.metadata.CodeOut;
import eu.delving.metadata.MappingFunction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class StandardMappingFunctions {

    private static List<String> mappingFunctionsScript = getMappingFunctionsScript();
    private static List<MappingFunction> mappingFunctions = getMappingFunctionsFromScript(mappingFunctionsScript);

    private StandardMappingFunctions() {
    }

    private static List<String> getMappingFunctionsScript() {
        List<String> script = new ArrayList<>();
        InputStream in = StandardMappingFunctions.class.getResourceAsStream("/functions.groovy");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                script.add(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return script;
    }

    private static List<MappingFunction> getMappingFunctionsFromScript(List<String> script) {
        List<MappingFunction> mappingFunctions = new ArrayList<>();
        for(String line : script) {
            if (line.contains("#def")) {
                String name = line.split("\\s+", 2)[1].split("\\(")[0].trim();
                String[] typedParameters = line.split("\\(")[1].split("\\)")[0].split(",");
                List<String> parameterNames = new ArrayList<>();
                for(String typedParam : typedParameters) {
                    parameterNames.add(typedParam.trim().split("\\s+")[1]);
                }

                mappingFunctions.add(MappingFunction.createStandardMappingFunction(name, parameterNames));
            }
        }
        return mappingFunctions;
    }

    public static void appendStandardFunctionsToScript(CodeOut codeOut) {
        for (String line : mappingFunctionsScript) {
            codeOut.line(line);
        }
    }

    public static List<MappingFunction> asList() {
        return mappingFunctions;
    }
}