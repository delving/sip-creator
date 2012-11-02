/*
 * Copyright 2011, 2012 Delving BV
 *
 * Licensed under the EUPL, Version 1.0 or? as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the
 * Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.delving.test;

import eu.delving.MappingCompletion;
import eu.delving.MappingEngine;
import eu.delving.MappingEngineFactory;
import eu.delving.MappingResult;
import eu.delving.metadata.MetadataException;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefModel;
import eu.delving.metadata.RecDefTree;
import eu.delving.schema.SchemaRepository;
import eu.delving.schema.SchemaType;
import eu.delving.schema.SchemaVersion;
import eu.delving.schema.util.FileSystemFetcher;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A unit test of the mapping engine
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestMappingEngine {
    public static final SchemaVersion LIDO_SCHEMA = new SchemaVersion("lido", "1.0.0");
    public static final SchemaVersion ESE_SCHEMA = new SchemaVersion("ese", "3.4.0");
    public static final SchemaVersion ABM_SCHEMA = new SchemaVersion("abm", "1.0.5");
    public static final SchemaVersion TIB_SCHEMA = new SchemaVersion("tib", "1.0.0");
    public static final SchemaVersion AFF_SCHEMA = new SchemaVersion("aff", "0.1.0");
    private SchemaRepository schemaRepo;

    @Before
    public void initRepo() throws IOException {
        schemaRepo = new SchemaRepository(new FileSystemFetcher(true));
    }

    @Test
    public void validateTreeNode() throws Throwable {
        MappingEngine engine = createEngine(
                "lido", "http://www.lido-schema.org"
        );
        engine.addMappingRunner(LIDO_SCHEMA, mapping("lido"));
        MappingResult result = execute(engine, LIDO_SCHEMA, input("lido"));
        Source source = new DOMSource(result.root());
        validator(LIDO_SCHEMA).validate(source);
    }

    @Test
    public void validateESENode() throws Throwable {
        MappingEngine engine = createEngine(
                "dc", "http://purl.org/dc/elements/1.1/",
                "dcterms", "http://purl.org/dc/terms/",
                "europeana", "http://www.europeana.eu/schemas/ese/"
        );
        engine.addMappingRunner(ESE_SCHEMA, mapping("ese"));
        MappingResult result = execute(engine, ESE_SCHEMA, input("ese"));
        System.out.println(result.toXml());
        System.out.println(result.toXmlAugmented());
        Source source = new DOMSource(result.root());
        validator(ESE_SCHEMA).validate(source);
    }

    @Test
    public void validateABMNode() throws Throwable {
        MappingEngine engine = createEngine(
                "dc", "http://purl.org/dc/elements/1.1/",
                "dcterms", "http://purl.org/dc/terms/",
                "europeana", "http://www.europeana.eu/schemas/ese/",
                "delving", "http://schemas.delving.eu/",
                "abm", "http://abmu.org/abm"
        );
        engine.addMappingRunner(ABM_SCHEMA, mapping("abm"));
        MappingResult result = execute(engine, ABM_SCHEMA, input("abm"));
        System.out.println(result.toXml());
        Source source = new DOMSource(result.root());
        Validator validator = validator(ABM_SCHEMA);
        validator.validate(source);
        System.out.println("SystemFields:");
        System.out.println(result.copyFields());
    }

    @Test
    public void rawNode() throws Throwable {
        MappingEngine engine = createEngine(
                "", "http://raw.org",
                "dc", "http://purl.org/dc/elements/1.1/",
                "dcterms", "http://purl.org/dc/terms/",
                "europeana", "http://www.europeana.eu/schemas/ese/",
                "icn", "http://www.icn.nl/schemas/icn/"
        );
        MappingResult result = execute(engine, SchemaVersion.RAW, input("icn"));
        System.out.println(result.toXml());
    }

    @Test
    public void validateTIBNode() throws Throwable {
        MappingEngine engine = createEngine(
                "dc", "http://purl.org/dc/elements/1.1/",
                "dcterms", "http://purl.org/dc/terms/",
                "europeana", "http://www.europeana.eu/schemas/ese/",
                "tib", "http://thuisinbrabant.nl"
        );
        engine.addMappingRunner(TIB_SCHEMA, mapping("tib"));
        MappingResult result = execute(engine, TIB_SCHEMA, input("tib"));
        String augmented = result.toXmlAugmented();
        Matcher matcher = Pattern.compile("<delving:thumbnail>").matcher(augmented);
        Assert.assertTrue("first one not found", matcher.find());
        Assert.assertFalse("second one should not be found", matcher.find());
        Source source = new DOMSource(result.root());
        validator(TIB_SCHEMA).validate(source);
    }

    @Test
    public void tryAff() throws Throwable {
        MappingEngine engine = createEngine(
                "lido", "http://www.lido-schema.org"
        );
        engine.addMappingRunner(AFF_SCHEMA, mapping("aff"));
        execute(engine, AFF_SCHEMA, input("aff"));
    }

    @Test
    public void indexDocumentFromAFF() throws Throwable {
        MappingEngine engine = createEngine(
                "lido", "http://www.lido-schema.org"
        );
        engine.addMappingRunner(AFF_SCHEMA, mapping("aff"));
        MappingResult result = execute(engine, AFF_SCHEMA, input("aff"));
//        System.out.println(serializer.toXml(result.root()));
        Map<String, List<String>> allFields = result.fields();
//        System.out.println(allFields);
        Assert.assertFalse(allFields.isEmpty());
    }

    // private stuff

    private MappingEngine createEngine(String... arg) {
        Map<String, String> namespaces = new TreeMap<String, String>();
        for (int walk = 0; walk < arg.length; walk += 2) namespaces.put(arg[walk], arg[walk + 1]);
        MappingEngineFactory factory = new MappingEngineFactory(getClass().getClassLoader(), new Immediate(), new MockRecDefModel());
        return factory.createEngine(namespaces);
    }

    private class Immediate implements Executor {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }

    private MappingResult execute(MappingEngine engine, final SchemaVersion schemaVersion, String recordXML) throws Throwable {
        SchemaVersion[] sv = new SchemaVersion[]{schemaVersion};
        Done done = new Done(schemaVersion);
        engine.mapRecord(1, String.format("RecordOf(%s)", schemaVersion), recordXML, sv, done);
        return done.getResult();
    }

    private class Done implements MappingCompletion {
        private SchemaVersion schemaVersion;
        private MappingResult result;
        private Throwable throwable;

        private Done(SchemaVersion schemaVersion) {
            this.schemaVersion = schemaVersion;
        }

        @Override
        public void onSuccess(int index, Map<SchemaVersion, MappingResult> results) {
            this.result = results.get(schemaVersion);
        }

        @Override
        public void onFailure(int index, Throwable throwable) {
            this.throwable = throwable;
        }

        public MappingResult getResult() throws Throwable {
            return result;
        }
    }

    private String input(String prefix) {
        return string(String.format("/%s/test-input.xml", prefix));
    }

    private String mapping(String prefix) {
        return string(String.format("/%s/mapping_%s.xml", prefix, prefix));
    }

    private class MockRecDefModel implements RecDefModel {
        @Override
        public RecDefTree createRecDefTree(SchemaVersion schemaVersion) throws MetadataException {
            try {
                String recDefString = schemaRepo.getSchema(schemaVersion, SchemaType.RECORD_DEFINITION);
                if (recDefString == null)
                    throw new RuntimeException("Unable to find record definition " + schemaVersion);
                return RecDefTree.create(RecDef.read(new ByteArrayInputStream(recDefString.getBytes("UTF-8"))));
            }
            catch (IOException e) {
                throw new RuntimeException("Unable to fetch record definition", e);
            }
        }
    }

    private Validator validator(SchemaVersion schemaVersion) {
        try {
            SchemaFactory factory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
            String validationXsd = schemaRepo.getSchema(schemaVersion, SchemaType.VALIDATION_SCHEMA);
            if (validationXsd == null) throw new RuntimeException("Unable to find validation schema " + schemaVersion);
            Schema schema = factory.newSchema(new StreamSource(new StringReader(validationXsd)));
            return schema.newValidator();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ClassLoader classLoader() {
        return getClass().getClassLoader();
    }

    private String string(String resourcePath) {
        try {
            return IOUtils.toString(stream(resourcePath), "UTF-8");
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private InputStream stream(String resourcePath) {
        InputStream resource = getClass().getResourceAsStream(resourcePath);
        if (resource == null) throw new RuntimeException("Resource not found: " + resourcePath);
        return resource;
    }

}
