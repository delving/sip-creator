package eu.delving;

import eu.delving.groovy.GroovyCodeResource;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import eu.delving.schema.SchemaRepository;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.files.*;
import eu.delving.sip.model.Feedback;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.model.SipProperties;
import eu.delving.sip.model.StatsModel;
import eu.delving.sip.xml.FileProcessor;
import org.apache.http.client.HttpClient;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.testng.Assert;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.*;

import javax.xml.transform.Source;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static eu.delving.sip.base.HttpClientFactory.createHttpClient;
import static eu.delving.sip.files.Storage.NARTHEX_URL;
import static org.mockito.Mockito.*;

@RunWith(Parameterized.class)
@Category(Object.class)
public class MappingEngineTest {

    private SchemaRepository createSchemaRepository() throws IOException {
        SipProperties sipProperties = new SipProperties();
        String serverUrl = sipProperties.getProp().getProperty(NARTHEX_URL, "http://delving.org/narthex");
        HttpClient httpClient = createHttpClient(serverUrl).build();
        return new SchemaRepository(new SchemaFetcher(httpClient));
    }


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
                "naa")
        );
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
                System.err.println(throwable);
            }

            @Override
            public String ask(String question) {
                System.out.println("ask: " + question);
                return null;
            }

            @Override
            public String ask(String question, String defaultValue) {
                System.out.println("ask: question=" + question + ", defaultValue=" + defaultValue);
                return null;
            }

            @Override
            public boolean confirm(String title, String message) {
                System.out.println("confirm: title=" + title + ", message=" + message);
                return false;
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

    private InputStream readTestData(String filename) {
        InputStream testData = getClass().getResourceAsStream("/mapped/" + filename);
        assert(testData != null);
        return testData;
    }

    @MethodSource("testData")
    @ParameterizedTest
    public void test_file_processor(String sipFilename, String mappingFilename, String recDefFilename, String orgId) throws Exception {
        Path workDir = HomeDirectory.WORK_DIR.toPath();

        String sipId = sipFilename.split("__")[0];
        String rdfFilename = sipId + "__" + orgId + ".xml";


        InputStream in = readTestData(rdfFilename);

        Source expectedXml = Input.fromStream(in).build();

        ProgressListener progressListener = mock(ProgressListener.class);

        Feedback feedback = createFeedback();

        StatsModel statsModel = mock(StatsModel.class);
        when(statsModel.getRecordCount()).thenReturn(0);

        Properties properties = new Properties();

        SipModel sipModel = mock(SipModel.class);
        when(sipModel.getPreferences()).thenReturn(properties);
        when(sipModel.getFeedback()).thenReturn(feedback);
        when(sipModel.getStatsModel()).thenReturn(statsModel);

        File sipFile = workDir.resolve(sipFilename).toFile();
        Path mappingFile = sipFile.toPath().resolve(mappingFilename);
        Path recFile = sipFile.toPath().resolve(recDefFilename);

        RecDef recDef = RecDef.read(new FileInputStream(recFile.toFile()));
        RecDefTree recDefTree = RecDefTree.create(recDef);
        RecMapping mappingDef = RecMapping.read(new FileInputStream(mappingFile.toFile()), recDefTree);
        DataSet dataSet = new StorageImpl.DataSetImpl(sipFile, createSchemaRepository());
        dataSet.getDataSetFacts(); // will actually initialize data facts and prevent null pointer later on


        GroovyCodeResource groovyCodeResource = new GroovyCodeResource(getClass().getClassLoader());
        String mappingModelPrefix = "";
        FileProcessor.Listener listener = mock(FileProcessor.Listener.class);
        FileProcessor fileProcessor = new FileProcessor(
            sipModel,
            dataSet,
            mappingDef,
            false,
            groovyCodeResource,
            new SipModel.Generator("http://localhost/narthex/", dataSet.getSpec(), mappingModelPrefix),
            listener
        );
        fileProcessor.setProgressListener(progressListener);
        fileProcessor.run();

        Path rdfFile = workDir.resolve(sipFilename).resolve(rdfFilename);
        Assert.assertTrue(Files.exists(rdfFile));

        Source actualXml = Input.fromPath(rdfFile).build();
        DifferenceEngine diff = new DOMDifferenceEngine();
        diff.addDifferenceListener((comparison, comparisonResult) -> {
            Assert.fail("found a difference: " + comparison);
        });
        diff.compare(expectedXml, actualXml);
    }
}
