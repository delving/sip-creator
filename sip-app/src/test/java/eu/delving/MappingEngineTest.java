package eu.delving;

import eu.delving.groovy.GroovyCodeResource;
import eu.delving.metadata.CachedResourceResolver;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import eu.delving.schema.SchemaRepository;
import eu.delving.sip.Application;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.files.*;
import eu.delving.sip.model.Feedback;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.model.SipProperties;
import eu.delving.sip.xml.FileProcessor;
import org.apache.http.client.HttpClient;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testng.Assert;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.DOMDifferenceEngine;
import org.xmlunit.diff.DifferenceEngine;

import javax.xml.transform.Source;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static eu.delving.sip.base.HttpClientFactory.createHttpClient;
import static eu.delving.sip.files.Storage.NARTHEX_URL;
import static org.mockito.Mockito.mock;

@RunWith(Parameterized.class)
@Category(Object.class)
public class MappingEngineTest {

    private static Stream<Arguments> testData() {
        return Stream.of(
            Arguments.of(
                "aidsmemorial__2016_07_25_20_52.sip.zip",
                "mapping_edm.xml",
                "edm_5.2.6_record-definition.xml",
                "edm"),
            Arguments.of(
                "2-24-01-08-art__2021_02_11_11_37.sip.zip",
                "FF9F5FE58279B40C9CB8251F5AEF657B__mapping_naa.xml",
                "naa_0.0.16_record-definition.xml",
                "naa"),
            Arguments.of(
                "bronbeek__2019_09_30_18_26.sip.zip",
                "D8D1BA57946413A33FD02E3B61109305__mapping_edm.xml",
                "edm_5.2.6_record-definition.xml",
                "edm"
            )
        );
    }

    private GroovyCodeResource groovyCodeResource;

    @BeforeEach
    public void before() {
        groovyCodeResource = new GroovyCodeResource(getClass().getClassLoader());
    }

    private SchemaRepository createSchemaRepository() throws IOException {
        SipProperties sipProperties = new SipProperties();
        String serverUrl = sipProperties.getProp().getProperty(NARTHEX_URL, "http://delving.org/narthex");
        HttpClient httpClient = createHttpClient(serverUrl).build();
        return new SchemaRepository(new SchemaFetcher(httpClient));
    }

    private Feedback createFeedback() {
        return new Feedback() {
            @Override
            public void info(String message) {
                System.out.println("info: " + message);
            }

            @Override
            public void alert(String message) {
                System.err.println("alert: " + message);
            }

            @Override
            public void alert(String message, Throwable throwable) {
                System.err.println("alert: " + message);
                System.err.println(throwable.getMessage());
                throwable.printStackTrace(System.err);
            }

            @Override
            public String ask(String question) {
                throw new IllegalStateException();
            }

            @Override
            public String ask(String question, String defaultValue) {
                throw new IllegalStateException();
            }

            @Override
            public boolean confirm(String title, String message) {
                throw new IllegalStateException();
            }

            @Override
            public boolean form(String title, Object... components) {
                throw new IllegalStateException();
            }

            @Override
            public String getHubPassword() {
                throw new IllegalStateException();
            }

            @Override
            public boolean getNarthexCredentials(Map<String, String> fields) {
                throw new IllegalStateException();
            }
        };
    }

    private SipModel createSipModel() throws StorageException, IOException {
        Feedback feedback = createFeedback();

        SipProperties sipProperties = new SipProperties();
        String serverUrl = sipProperties.getProp().getProperty(NARTHEX_URL, "http://delving.org/narthex");
        HttpClient httpClient = createHttpClient(serverUrl).build();
        SchemaRepository schemaRepository = new SchemaRepository(new SchemaFetcher(httpClient));
        Application.ResolverContext context = new Application.ResolverContext();
        Storage storage = new StorageImpl(HomeDirectory.WORK_DIR, schemaRepository, new CachedResourceResolver(context));
        context.setStorage(storage);
        context.setHttpClient(httpClient);

        return new SipModel(
            null,
            storage,
            groovyCodeResource,
            feedback,
            new SipProperties()
        );
    }

    private InputStream readTestData(String filename) {
        InputStream testData = getClass().getResourceAsStream("/mapped/" + filename);
        assert(testData != null);
        return testData;
    }

    @MethodSource("testData")
    @ParameterizedTest
    public void test_file_processor(String sipFilename, String mappingFilename, String recDefFilename, String orgId) throws Exception {
        Path workDir = HomeDirectory.WORK_DIR.toPath();
        File sipFile = workDir.resolve(sipFilename).toFile();
        Path mappingFile = sipFile.toPath().resolve(mappingFilename);
        Path recDefFile = sipFile.toPath().resolve(recDefFilename);

        FileProcessor.Listener listener = mock(FileProcessor.Listener.class);
        ProgressListener progressListener = mock(ProgressListener.class);
        processSourceXML(sipFile, mappingFile, recDefFile, listener, progressListener);

        String sipPrefix = sipFilename.split("__")[0];
        String controlXMLFilename = sipPrefix + "__" + orgId + ".xml";
        InputStream controlXMLStream = readTestData(controlXMLFilename);
        Path outputXMLFile = workDir.resolve(sipFilename).resolve(controlXMLFilename);
        Assert.assertTrue(Files.exists(outputXMLFile));

        compareXMLs(controlXMLStream, outputXMLFile);
    }

    private void processSourceXML(File sipFile,
                                  Path mappingFile,
                                  Path recDefFile,
                                  FileProcessor.Listener listener,
                                  ProgressListener progressListener) throws IOException, StorageException {
        SipModel sipModel = createSipModel();
        RecMapping recMapping = getRecMapping(mappingFile, recDefFile);
        DataSet sourceXML = new StorageImpl.DataSetImpl(sipFile, createSchemaRepository());
        sourceXML.getDataSetFacts(); // will actually initialize data facts and prevent null pointer later on
        FileProcessor.UriGenerator uriGenerator = new SipModel.Generator("http://localhost/narthex/", sourceXML.getSpec(), "");

        FileProcessor fileProcessor = new FileProcessor(
            sipModel,
            sourceXML,
            recMapping,
            false,
            groovyCodeResource,
            uriGenerator,
            listener
        );
        fileProcessor.setProgressListener(progressListener);
        fileProcessor.run();
    }

    private RecMapping getRecMapping(Path mappingFile, Path recFile) throws FileNotFoundException, UnsupportedEncodingException {
        RecDef recDef = RecDef.read(new FileInputStream(recFile.toFile()));
        RecDefTree recDefTree = RecDefTree.create(recDef);
        RecMapping mappingDef = RecMapping.read(new FileInputStream(mappingFile.toFile()), recDefTree);
        return mappingDef;
    }

    private void compareXMLs(InputStream controlXMLStream, Path outputXMLFile) {
        Source expectedXml = Input.fromStream(controlXMLStream).build();
        Source actualXml = Input.fromPath(outputXMLFile).build();
        DifferenceEngine diff = new DOMDifferenceEngine();
        diff.addDifferenceListener((comparison, comparisonResult) -> Assertions.fail("found a difference: " + comparison));
        diff.compare(expectedXml, actualXml);
    }
}
