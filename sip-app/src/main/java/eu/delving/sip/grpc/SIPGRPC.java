package eu.delving.sip.grpc;

import static eu.delving.sip.files.Storage.SHACL_VALIDATION;
import static eu.delving.sip.files.Storage.XSD_VALIDATION;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;

import eu.delving.sip.Application;
import eu.delving.sip.cli.CLIFeedback;
import eu.delving.sip.cli.CLIProcessorListener;
import eu.delving.sip.cli.SIPFilesFinder;
import org.apache.jena.riot.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.delving.groovy.GroovyCodeResource;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import eu.delving.sip.base.ProgressListener;
import eu.delving.sip.cli.SIPFilesFinder.SIPFiles;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.xml.FileProcessor;
import io.grpc.stub.StreamObserver;

public class SIPGRPC {
    private static final Logger logger = LoggerFactory.getLogger(SIPGRPC.class);
    private final GroovyCodeResource groovyCodeResource = new GroovyCodeResource(getClass().getClassLoader());

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = 50051; // Default gRPC port
        String basePath = "";
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
            basePath = args[1];
        }

        Application.init("grpc");
        final MappingServer server = new MappingServer(port, basePath);
        server.start();
        server.blockUntilShutdown();
    }

    // New method for gRPC usage
    public void startMappingGrpc(Path sipDir, StreamObserver<MappingProgress> responseObserver)
            throws IOException, StorageException {
        processSourceXML(sipDir, new GrpcProgressTracker(responseObserver));
    }

    private void processSourceXML(Path sipDir, ProgressListener progressListener) throws IOException, StorageException {
        long startTime = System.currentTimeMillis();

        // Use SIPFilesFinder to discover required files
        SIPFiles sipFiles = SIPFilesFinder.findRequiredFiles(sipDir);
        logFileInfo(sipFiles);

        // Get the mapping and RecDef
        RecMapping recMapping = getRecMapping(sipFiles.getMappingFile(), sipFiles.getRecordDefinition());

        // Create dataset
        DataSet sourceXML = sipFiles.getStorage().createDataSet(sipFiles.getSipDir().getFileName().toString());
        String prefix = sourceXML.getRecDef().prefix;

        // Create URI generator
        FileProcessor.UriGenerator uriGenerator = new SipModel.Generator(
                "http://delving.org/narthex",
                prefix,
                recMapping.getPrefix());

        // Create file processor
        recMapping.getFacts().clear();
        recMapping.getFacts().putAll(sourceXML.getDataSetFacts());
        FileProcessor fileProcessor = new FileProcessor(
                progressListener instanceof GrpcProgressTracker ? progressListener.getFeedback() : new CLIFeedback(),
                sipFiles.getProperties().getProperty(XSD_VALIDATION) == "true",
                sipFiles.getProperties().getProperty(SHACL_VALIDATION) == "true",
                sourceXML,
                recMapping,
                false,
                groovyCodeResource,
                uriGenerator,
                new CLIProcessorListener(),
                RDFFormat.RDFXML);

        fileProcessor.setProgressListener(progressListener);

        // Record initialization time
        long initializationTime = System.currentTimeMillis() - startTime;
        logger.info("Initialization took: {}ms", initializationTime);

        // If using gRPC, send initialization status
        if (progressListener instanceof GrpcProgressTracker) {
            ((GrpcProgressTracker) progressListener).onInitializationComplete(sipFiles, initializationTime);
        }

        // Start processing
        long runStartTime = System.currentTimeMillis();
        fileProcessor.run();
        long runTime = System.currentTimeMillis() - runStartTime;

        logger.info("Processing took: {}ms", runTime);
        logger.info("Total execution time: {}ms", (System.currentTimeMillis() - startTime));

        // If using gRPC, send completion status
        if (progressListener instanceof GrpcProgressTracker) {
            ((GrpcProgressTracker) progressListener).onComplete();
        }
    }

    private void logFileInfo(SIPFiles sipFiles) {
        logger.info("Found required files:");
        logger.info("Record definition: {}", sipFiles.getRecordDefinition());
        logger.info("Validation schema: {}", sipFiles.getValidationSchema());
        logger.info("Mapping file: {}", sipFiles.getMappingFile());
        logger.info("Source file: {}", sipFiles.getSourceFile());
    }

    private RecMapping getRecMapping(Path mappingFile, Path recDefFile) throws IOException {
        try (FileInputStream mappingStream = new FileInputStream(mappingFile.toFile());
                FileInputStream recDefStream = new FileInputStream(recDefFile.toFile())) {
            RecDef recDef = RecDef.read(recDefStream);
            RecDefTree recDefTree = RecDefTree.create(recDef);
            return RecMapping.read(mappingStream, recDefTree);
        }
    }

}
