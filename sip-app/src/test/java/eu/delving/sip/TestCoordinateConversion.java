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

package eu.delving.sip;

import com.thoughtworks.xstream.XStream;

import eu.delving.XStreamFactory;
import eu.delving.groovy.GroovyCodeResource;
import eu.delving.metadata.GeoUtil;
import eu.delving.metadata.MappingFunction;
import groovy.lang.Binding;
import groovy.lang.Script;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test the coordinate conversion functions
 *
 *
 */

public class TestCoordinateConversion {
    private Pattern utmPattern = Pattern.compile("\\s*SRID=(\\d+);POINT\\((\\d+|\\d+\\.\\d+),(\\d+|\\d+\\.\\d+)\\)");
    private List<String> inputLines;
    private GroovyCodeResource groovyCodeResource = new GroovyCodeResource(ClassLoader.getSystemClassLoader());
    private Script functionScript;

    @Before
    public void prep() throws IOException {
        URL input = getClass().getResource("/geo/TestCoordinateConversion.txt");
        inputLines = FileUtils.readLines(new File(input.getFile()));
        URL func = getClass().getResource("/geo/TestCoordinateConversion.xml");
        MappingFunction mappingFunction = (MappingFunction) getStream().fromXML(new File(func.getFile()));
        Map<String, String> facts = new TreeMap<String, String>();
        functionScript = groovyCodeResource.createFunctionScript(mappingFunction, facts, null);
    }

    @Test
    public void runThrough() {
        for (String line : inputLines) {
            if (line.trim().isEmpty())
                continue;
            String latLongString = functionCall(line, false);
            checkLatLong(line, latLongString);
            String utmString = functionCall(line, true);
            Matcher matcher = utmPattern.matcher(utmString);
            Assert.assertTrue("SRID format violation: " + utmString, matcher.matches());
            String srid = matcher.group(1);
            String east = matcher.group(2);
            String north = matcher.group(3);
            String latLongConverted = GeoUtil.convertUTM(Integer.parseInt(srid) % 100 + " V " + east + " " + north, false);
            checkLatLong(line, latLongConverted);
        }
    }

    private void checkLatLong(String line, String latLongString) {
        String[] latLong = latLongString.split(",");
        double latitude = Double.parseDouble(latLong[0]);
        double longitude = Double.parseDouble(latLong[1]);
        Assert.assertTrue(
                String.format("Latitude is outside Norway [%s]=>[%s]", line, latLongString),
                latitude > 58.0 && latitude < 72.0);
        Assert.assertTrue(
                String.format("Longitude is outside Norway [%s]=>[%s]", line, latLongString),
                longitude > 4.0 && longitude < 30.0);
    }

    private String functionCall(String param, boolean utmOut) {
        //return GeoUtil.convertUTM(param, utmOut);
        Binding binding = new Binding();
        binding.setVariable("param", param);
        binding.setVariable("utmOut", utmOut);
        functionScript.setBinding(binding);
        Object result = functionScript.run();
        Assert.assertNotNull("No result for: " + param, result);
        Assert.assertFalse("Empty result for: " + param, result.toString().isEmpty());
        return result.toString();
    }

    private static XStream getStream() {
        return XStreamFactory.getStreamFor(MappingFunction.class);
    }

}
