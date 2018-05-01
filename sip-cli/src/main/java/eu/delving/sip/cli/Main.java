package eu.delving.sip.cli;

/*
 * Steps for creating the CLI
 *  - create RecDef
 *  - create MappingResult
 *  - MappingResult.toString()
 */



import eu.delving.XMLToolFactory;
import eu.delving.groovy.*;
import eu.delving.metadata.*;
import eu.delving.schema.SchemaRepository;
import eu.delving.schema.SchemaVersion;
import eu.delving.sip.files.*;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.xml.FileProcessor;
import org.apache.commons.cli.*;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamException;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.*;
import java.util.Map;
import java.util.TreeMap;

import static eu.delving.sip.base.HttpClientFactory.createHttpClient;

public class Main {

    public static void main(String[] args) throws ParseException {
        Options options = new Options();
        options.addOption("listDatasets", "l", false, "list all datasets in the storage.");
        options.addOption("processDataset", true, "process dataset");
        options.addOption("workDir", true, "work directory of the SipCreator");
        options.addOption("narthexUrl", true, "The remote URL for narthex.");
//        options.addOption("mapping", true, "mapping file");
//        options.addOption("recdef", true, "record definition");
//        options.addOption("validate", false, "run validation");
//        options.addOption("xsd", true, "validation file");
//        options.addOption("sourceFile", true, "source file");
//        options.addOption("sourceString", true, "source string");
//        options.addOption("localId", true, "record identifier");
        CommandLineParser parser = new BasicParser();
        CommandLine cmd = parser.parse(options, args);
//        if (cmd.hasOption("mapping")) {
//            System.out.println("rec-def: "+cmd.getOptionValue("recdef"));
//            System.out.println("mapping: "+cmd.getOptionValue("mapping"));
//            Boolean runValidate = cmd.hasOption("validate");
//            System.out.println("run-validate: "+runValidate);
//            System.out.println("xsd: "+cmd.getOptionValue("xsd"));
//            System.out.println("source-file: "+cmd.getOptionValue("sourceFile"));
//            System.out.println("source-string: "+cmd.getOptionValue("sourceString"));
//            System.out.println("record-id: "+cmd.getOptionValue("localId"));
//            try {
//                try {
//                    System.out.println(
//                        "\n\n=====\nrecord: "+Main.processRecord(
//                            cmd.getOptionValue("recdef"),
//                            cmd.getOptionValue("xsd"),
//                            cmd.getOptionValue("mapping"),
//                            cmd.getOptionValue("sourceString"),
//                            cmd.getOptionValue("localId")
//                        )
//                    );
//                } catch (SAXException e) {
//                    e.printStackTrace();
//                } catch (UnsupportedEncodingException e) {
//                    e.printStackTrace();
//                } catch (XMLStreamException e) {
//                    e.printStackTrace();
//                } catch (MappingException e) {
//                    e.printStackTrace();
//                }
//            } catch (FileNotFoundException e) {
//                e.printStackTrace();
//            }
//        }

        Storage storage = null;
        String narthexUrl = cmd.getOptionValue("narthexUrl", "http://data.brabantcloud.nl/narthex");

        if (cmd.hasOption("workDir")) {
            try {
                storage = getStorageFactory(
                    cmd.getOptionValue("workDir"),
                    narthexUrl
                );
            } catch (IOException e) {
                e.printStackTrace();
            } catch (StorageException e) {
                e.printStackTrace();
            }
        } else {
            HelpFormatter help = new HelpFormatter();
            help.printHelp("sip-cli", options);
        }

        if (cmd.hasOption("listDatasets") && storage != null) {
            Map<String, DataSet> dataSets = storage.getDataSets();
            System.out.println("test test");
            for  (Map.Entry<String, DataSet> entry: dataSets.entrySet()){
                System.out.println("sipZipFile: " + entry.getKey());
                DataSet dataset = entry.getValue();
                System.out.println("spec: " + dataset.getDataSetFacts());
                try {
                    File file = dataset.toSipZip(true);
                    System.out.println(file.getAbsolutePath());
                } catch (StorageException e) {
                    e.printStackTrace();
                }
            }
        }

        if (cmd.hasOption("processDataset") && storage != null) {

            Map<String, DataSet> dataSets = storage.getDataSets();
            DataSet dataSet = null;
            String spec = cmd.getOptionValue("processDataset");
            for (DataSet ds: dataSets.values()) {
                if (ds.getSpec().startsWith(spec)) {
                    dataSet = ds;
                    break;
                }
            }
            try {
                processDateSet(dataSet, narthexUrl, true, false);
            } catch (StorageException e) {
                e.printStackTrace();
            }
        }



    }

    public static RecDefTree getRecDefTree(String recDefPath) throws FileNotFoundException {
        RecDef recDef = RecDef.read(new FileInputStream(new File(recDefPath)));
        return RecDefTree.create(recDef);
    }

    public static MetadataRecordFactory getMetadataRecordFactory(RecDefTree recDefTree) {
        Map<String, String> namespaces = new TreeMap<String, String>();
        for(RecDef.Namespace ns : recDefTree.getRecDef().namespaces) {
            namespaces.put(ns.prefix, ns.uri);
        }
        return new MetadataRecordFactory(namespaces);
    }

    public static String processRecord(String recDefPath, String xsdPath, String mappingPath, String sourceRecord, String localId) throws FileNotFoundException, SAXException, UnsupportedEncodingException, XMLStreamException, MappingException {
        RecDefTree recDefTree = getRecDefTree(recDefPath);
        MetadataRecordFactory metadataRecordFactory = getMetadataRecordFactory(recDefTree);

        SchemaFactory schemaFactory = XMLToolFactory.schemaFactory(recDefTree.getRecDef().prefix);
        // todo set resolver?
        Schema schema = schemaFactory.newSchema(new File(xsdPath));
        Validator validator = schema.newValidator();
        XmlSerializer serializer = new XmlSerializer();
        RecMapping recMapping = RecMapping.read(new FileInputStream(new File(mappingPath)), recDefTree);
        CodeGenerator codeGenerator = new CodeGenerator(recMapping);
        String recordMappingCode = codeGenerator.withTrace(false).toRecordMappingCode();
        BulkMappingRunner runner = new BulkMappingRunner(recMapping, recordMappingCode);
        // todo parse string from file
        MetadataRecord metadataRecord = metadataRecordFactory.metadataRecordFrom(sourceRecord);
        Node node = runner.runMapping(metadataRecord);
        MappingResult result = new MappingResult(serializer, localId, node, recDefTree);
        // todo get id from somewhere
        return result.toXml();
    }

    public static Storage getStorageFactory(String path, String serverUrl) throws IOException, StorageException {
        File home = new File(path);
        HttpClient httpClient = createHttpClient(serverUrl).build();
        ResolverContext context = new ResolverContext();

        SchemaRepository repository = new SchemaRepository(new SchemaFetcher(httpClient));
        Storage storage = new StorageImpl(home, repository, new CachedResourceResolver(context));
        context.setStorage(storage);
        context.setHttpClient(httpClient);

        return storage;
    }

    public static boolean processDateSet(DataSet dataSet, String narthexUrl, boolean allowInvalid, boolean xsdValidation) throws StorageException {
        RecDefModel recDefModel = new RecDefModelImpl(dataSet);
        GroovyCodeResource groovyCodeResource = new GroovyCodeResource(Main.class.getClassLoader());
        RecMapping recMapping = dataSet.getRecMapping(recDefModel);
        //SipModel.Generator generator = new SipModel.Generator(narthexUrl, dataSet.getSpec(), recMapping.getPrefix());
        //FileProcessor fileProcessor = new FileProcessor(
            //null,
            //dataSet,
            //recMapping,
            //allowInvalid,
            //groovyCodeResource,
            //generator,
            //null
        //);

        //fileProcessor.runHeadless(xsdValidation);

        return false;
    }

    private static class RecDefModelImpl implements RecDefModel {

        private final DataSet dataSet;

        public RecDefModelImpl(
            DataSet dataSet
        ){
            this.dataSet = dataSet;
        }

        @Override
        public RecDefTree createRecDefTree(SchemaVersion schemaVersion) throws MetadataException {
            try {
                RecDef recDef = dataSet.getRecDef();
                return RecDefTree.create(recDef);
            }
            catch (StorageException e) {
                throw new MetadataException(e);
            }
        }
    }

    private static class ResolverContext implements CachedResourceResolver.Context {
        private Storage storage;
        private HttpClient httpClient;

        public void setStorage(Storage storage) {
            this.storage = storage;
        }

        public void setHttpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
        }

        @Override
        public String get(String url) {
            try {
                HttpGet get = new HttpGet(url);
                HttpResponse response = httpClient.execute(get);
                StatusLine line = response.getStatusLine();
                if (line.getStatusCode() != HttpStatus.SC_OK) {
                    throw new IOException(String.format(
                        "HTTP Error %s (%s) on %s",
                        line.getStatusCode(), line.getReasonPhrase(), url
                    ));
                }
                return EntityUtils.toString(response.getEntity());
            }
            catch (Exception e) {
                throw new RuntimeException("Fetching problem: " + url, e);
            }
        }

        @Override
        public File file(String systemId) {
            return storage.cache(systemId.replaceAll("[/:]", "_"));
        }
    }

}
