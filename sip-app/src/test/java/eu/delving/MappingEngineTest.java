package eu.delving;

import eu.delving.groovy.GroovyCodeResource;
import eu.delving.metadata.CachedResourceResolver;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import eu.delving.schema.SchemaRepository;
import eu.delving.sip.Application;
import eu.delving.sip.MappingCLI;
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
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.*;

import javax.xml.transform.Source;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static eu.delving.sip.base.HttpClientFactory.createHttpClient;
import static eu.delving.sip.files.Storage.NARTHEX_URL;
import static eu.delving.sip.files.Storage.XSD_VALIDATION;
import static org.mockito.Mockito.mock;

@RunWith(Parameterized.class)
@Category(Object.class)
public class MappingEngineTest {

    private static Stream<Arguments> testData() {
        return Stream.of(
            Arguments.of(
                "source.xml.gz",
                "aidsmemorial__2016_07_25_20_52.sip.zip",
                "mapping_edm.xml",
                "edm_5.2.6_record-definition.xml"),
            Arguments.of(
                "source.xml.gz",
                "2-24-01-08-art__2021_02_11_11_37.sip.zip",
                "mapping_naa.xml",
                "naa_0.0.16_record-definition.xml"),
            Arguments.of(
                "source.xml.gz",
                "bronbeek__2019_09_30_18_26.sip.zip",
                "D8D1BA57946413A33FD02E3B61109305__mapping_edm.xml",
                "edm_5.2.6_record-definition.xml")
        );
    }

    private GroovyCodeResource groovyCodeResource;

    @BeforeEach
    public void before() {
        groovyCodeResource = new GroovyCodeResource(getClass().getClassLoader());
    }


    @MethodSource("testData")
    @ParameterizedTest
    public void test_file_processor(String inputFilename,
                                    String sipFilename,
                                    String mappingFilename,
                                    String recDefFilename) throws Exception {
        Path testDir = new File(getClass().getResource("/mapped/").getFile()).toPath();

        Path workDir = HomeDirectory.WORK_DIR.toPath();
        Path outputSipDir = workDir.resolve(sipFilename);

        File controlSipDir = testDir.resolve(sipFilename).toFile();
        Path inputFile = controlSipDir.toPath().resolve(inputFilename);
        Path mappingFile = controlSipDir.toPath().resolve(mappingFilename);
        Path recDefFile = controlSipDir.toPath().resolve(recDefFilename);
        Path controlOutputDir = controlSipDir.toPath().resolve("output");

        MappingCLI.main(new String[]{inputFile.toString(), mappingFile.toString(), recDefFile.toString(), "false", outputSipDir.toString()});

        Path outputDir = outputSipDir.resolve("output");
        Assert.assertTrue(Files.exists(outputDir));

        compareXMLs(controlOutputDir, outputDir);
    }


    private void compareXMLs(Path controlOutputDir, Path outputDir) throws IOException {
        DifferenceEngine diff = new DOMDifferenceEngine();
        diff.addDifferenceListener((comparison, comparisonResult) -> Assertions.fail("found a difference: " + comparison));
        int recordNumber = 0;
        for (Path controlXMLFile : Files.list(controlOutputDir).collect(Collectors.toList())) {
            Source expectedXml = Input.fromPath(controlXMLFile).build();
            Source actualXml = Input.fromPath(outputDir.resolve(controlXMLFile.getFileName().toString())).build();
            diff.compare(expectedXml, actualXml);
            recordNumber++;
        }
        // Smoke test to check contents of the control output dir and actual output dir are most likely equal
        // and one doesn't contain more files than the other
        Assert.assertTrue(!Files.exists(outputDir.resolve(recordNumber + ".xml")));
    }
}
