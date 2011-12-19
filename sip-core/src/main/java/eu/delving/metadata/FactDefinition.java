/*
 * Copyright 2010 DELVING BV
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

package eu.delving.metadata;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import org.apache.commons.io.IOUtils;

import java.io.*;


/**
 * An XStream approach for replacing the annotated beans.
 *
 * @author Gerald de Jong <geralddejong@gmail.com>
 */

@XStreamAlias("fact-definition")
public class FactDefinition {

    @XStreamAsAttribute
    public String name;

    public String prompt;
    public String toolTip;
    public boolean automatic;
    public String defaultValue;
    public java.util.List<String> options;

    @XStreamAlias("fact-definition-list")
    public static class List {

        @XStreamImplicit
        public java.util.List<FactDefinition> factDefinitions;

    }

    public String toString() {
        return String.format("FactDefinition(%s)", name);
    }

    public static java.util.List<FactDefinition> read(File file) throws FileNotFoundException {
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            XStream stream = new XStream(new PureJavaReflectionProvider());
            stream.processAnnotations(FactDefinition.List.class);
            FactDefinition.List factDefinitions = (FactDefinition.List) stream.fromXML(reader);
            return factDefinitions.factDefinitions;
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        finally {
            IOUtils.closeQuietly(reader);
        }
    }
}
