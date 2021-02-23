package eu.delving;

import eu.delving.groovy.GroovyCodeResource;
import eu.delving.metadata.CachedResourceResolver;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import eu.delving.rdf.MapToCRM;
import eu.delving.schema.SchemaRepository;
import eu.delving.sip.Application;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.files.*;
import eu.delving.sip.model.Feedback;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.model.SipProperties;
import eu.delving.sip.model.StatsModel;
import eu.delving.sip.xml.FileProcessor;
import org.apache.http.client.HttpClient;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.Assert;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

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

    @ParameterizedTest
    @ValueSource(strings = {"aidsmemorial__2016_07_25_20_52.sip.zip"})
    public void test_file_processor(String filename) throws Exception {
        Path workDir = new File("/home/q/PocketMapper/work/").toPath();
        String sipId = filename.split("__")[0];
        String rdfFilename = sipId + "__edm.xml";

//        InputStream in = getClass().getResourceAsStream("/mapped/" + rdfFilename);
//        ArrayList<String> expectedLines = new ArrayList<>();
//        try(BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
//            String line;
//            while((line = reader.readLine()) != null) {
//                expectedLines.add(line);
//            }
//        }

        ProgressListener progressListener = mock(ProgressListener.class);

        Feedback feedback = mock(Feedback.class);
        doAnswer((Answer<Void>) invocationOnMock -> {
            throw (Throwable)invocationOnMock.getArgument(1);
        }).when(feedback).alert(Mockito.anyString(), Mockito.any());

        StatsModel statsModel = mock(StatsModel.class);
        when(statsModel.getRecordCount()).thenReturn(0);

        Properties properties = new Properties();

        SipModel sipModel = mock(SipModel.class);
        when(sipModel.getPreferences()).thenReturn(properties);
        when(sipModel.getFeedback()).thenReturn(feedback);
        when(sipModel.getStatsModel()).thenReturn(statsModel);

        File sipFile = workDir.resolve(filename).toFile();
        Path mappingFile = sipFile.toPath().resolve("mapping_edm.xml");
        Path recFile = sipFile.toPath().resolve("edm_5.2.6_record-definition.xml");

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

        Path rdfFile = workDir.resolve(filename).resolve(rdfFilename);
        Assert.assertTrue(Files.exists(rdfFile));

        List<String> actualLines = Files.readAllLines(rdfFile);
//        Assert.assertEquals(expectedLines, actualLines);
    }
}
