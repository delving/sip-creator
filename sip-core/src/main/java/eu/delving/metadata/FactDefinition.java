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

package eu.delving.metadata;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import org.apache.commons.io.IOUtils;

import java.io.*;

/**
 * Facts are the givens associated with a dataset, such as where it came from,
 * which language it is in, etc.  Some of the entries involve a choice from
 * a number of options.
 *
 * Each fact definition describes one entry in the Facts map associated with
 * each dataset.  Facts are read-only on the client side, and is used to
 * define global values which can be accessed by any mapping code.
 *
 * The CultureHub uses facts to define the fields that appear when a
 * user creates a dataset.
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

@XStreamAlias("fact-definition")
public class FactDefinition {

    @XStreamAsAttribute
    public String name;

    public String prompt;

    public java.util.List<String> options;

    public String toString() {
        return String.format("FactDefinition(%s)", name);
    }

    @XStreamAlias("fact-definition-list")
    public static class List {

        @XStreamImplicit
        public java.util.List<FactDefinition> factDefinitions;
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
