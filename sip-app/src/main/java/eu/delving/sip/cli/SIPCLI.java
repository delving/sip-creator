package eu.delving.sip.cli;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

import eu.delving.sip.Application;
import eu.delving.sip.model.FactModel;
import eu.delving.sip.xml.AnalysisParser;
import eu.delving.stats.Stats;
import org.apache.jena.riot.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.delving.groovy.GroovyCodeResource;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import eu.delving.sip.cli.SIPFilesFinder.SIPFiles;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.files.StorageException;
import eu.delving.sip.grpc.MappingServer;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.xml.FileProcessor;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static eu.delving.sip.files.Storage.MAX_UNIQUE_VALUE_LENGTH;

@Command(name = "sipcli", description = "SIP (Submission Information Package) Processing Tool", subcommands = {
        ProcessCommand.class,
        AnalyzeCommand.class,
        CheckCommand.class,
        ListCommand.class,
        CleanCommand.class,
        ServerCommand.class
})
public class SIPCLI implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(SIPCLI.class);

    @Option(names = { "-h", "--help" }, usageHelp = true, description = "Show this help message and exit")
    private boolean helpRequested = false;

    public static void main(String[] args) {
        Application.init("cli");
        int exitCode = new CommandLine(new SIPCLI()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    static Path resolve(Path workingDir, String uri) {
        Path path = Paths.get(uri);
        if (path.isAbsolute()) {
            return path;
        }
        return workingDir.resolve(path);
    }

    static void logFoundFiles(SIPFiles sipFiles) {
        System.out.println("Found required files:");
        System.out.println("Record definition: " + sipFiles.getRecordDefinition());
        System.out.println("Validation schema: " + sipFiles.getValidationSchema());
        System.out.println("Mapping file: " + sipFiles.getMappingFile());
        System.out.println("Source file: " + sipFiles.getSourceFile());
    }

    static void logTimingResults(long startTime, long endTime) {
        long totalTime = endTime - startTime;
        System.out.println("Total execution time: " + totalTime + "ms");
    }
}

@Command(name = "process", description = "Process a SIP directory with mapping")
class ProcessCommand implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(ProcessCommand.class);
    private final GroovyCodeResource groovyCodeResource = new GroovyCodeResource(getClass().getClassLoader());

    @Parameters(index = "0", description = "SIP directory path")
    private String sipDirPath;

    @Option(names = { "-v", "--validate", "--validate-xsd" }, description = "Enable XSD validation")
    private boolean validateXsd = false;

    @Option(names = { "--validate-shacl" }, description = "Enable SHACL validation")
    private boolean validateShacl = false;

    @Option(names = {
            "--uri-base" }, description = "Base URI for generation", defaultValue = "http://delving.org/narthex")
    private String uriBase;

    @Option(names = { "-d", "--dataset" }, description = "Specific dataset to process")
    private String datasetName;

    @Override
    public Integer call() {
        try {
            String userDir = Objects.requireNonNull(System.getProperty("user.dir"));
            Path workingDir = Paths.get(userDir);
            Path sipDir = SIPCLI.resolve(workingDir, sipDirPath);

            validateDirectory(sipDir);

            if (datasetName != null) {
                processSpecificDataset(sipDir, datasetName);
            } else {
                processDirectory(sipDir);
            }

            return 0;
        } catch (Exception e) {
            logger.error("Error processing directory", e);
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private void validateDirectory(Path sipDir) throws IllegalStateException {
        if (!Files.exists(sipDir) || !Files.isDirectory(sipDir)) {
            throw new IllegalStateException("Invalid SIP directory: " + sipDir);
        }
    }

    private void processSpecificDataset(Path sipDir, String datasetName) throws IOException, StorageException {
        SIPFiles sipFiles = SIPFilesFinder.findRequiredFiles(sipDir);
        Map<String, DataSet> datasets = sipFiles.getStorage().getDataSets();

        DataSet dataset = datasets.get(datasetName);
        if (dataset == null) {
            throw new IllegalArgumentException("Dataset not found: " + datasetName);
        }

        processDataset(sipFiles, dataset);
    }

    private void processDirectory(Path sipDir) throws IOException, StorageException {
        System.out.println("Starting mapping engine...");
        long startTime = System.currentTimeMillis();

        SIPFiles sipFiles = SIPFilesFinder.findRequiredFiles(sipDir);
        SIPCLI.logFoundFiles(sipFiles);

        DataSet sourceXML = sipFiles.getStorage().createDataSet(sipFiles.getSipDir().getFileName().toString());
        processDataset(sipFiles, sourceXML);

        SIPCLI.logTimingResults(startTime, System.currentTimeMillis());
    }

    private void processDataset(SIPFiles sipFiles, DataSet sourceXML) throws IOException {
        String mappingFileName = sipFiles.getMappingFile().getFileName().toString();
        String[] parts = mappingFileName.split("__");
        String prefix = (parts.length > 1 ? parts[1] : parts[0]).replace(".xml", "");

        RecMapping recMapping = getRecMapping(sipFiles.getMappingFile(), sipFiles.getRecordDefinition());
        FileProcessor processor = createFileProcessor(sourceXML, recMapping, prefix);
        processor.setProgressListener(new CLIProgressListener());

        processor.run();
    }

    private FileProcessor createFileProcessor(DataSet sourceXML, RecMapping recMapping, String prefix) {
        FileProcessor.UriGenerator uriGenerator = new SipModel.Generator(
                uriBase,
                prefix,
                recMapping.getPrefix());

        recMapping.getFacts().clear();
        recMapping.getFacts().putAll(sourceXML.getDataSetFacts());
        return new FileProcessor(
                new CLIFeedback(),
                validateXsd,
                validateShacl,
                sourceXML,
                recMapping,
                true,
                groovyCodeResource,
                uriGenerator,
                new CLIProcessorListener(),
                RDFFormat.RDFXML);
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

@Command(name = "analyze", description = "Analyze a SIP directory")
class AnalyzeCommand implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(ProcessCommand.class);
    private final GroovyCodeResource groovyCodeResource = new GroovyCodeResource(getClass().getClassLoader());

    @Parameters(index = "0", description = "SIP directory path")
    private String sipDirPath;

    @Option(names = { "-d", "--dataset" }, description = "Specific dataset to process")
    private String datasetName;

    @Override
    public Integer call() {
        try {
            String userDir = Objects.requireNonNull(System.getProperty("user.dir"));
            Path workingDir = Paths.get(userDir);
            Path sipDir = SIPCLI.resolve(workingDir, sipDirPath);

            validateDirectory(sipDir);

            if (datasetName != null) {
                analyzeSpecificDataset(sipDir, datasetName);
            } else {
                analyzeDirectory(sipDir);
            }

            return 0;
        } catch (Exception e) {
            logger.error("Error processing directory", e);
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private void validateDirectory(Path sipDir) throws IllegalStateException {
        if (!Files.exists(sipDir) || !Files.isDirectory(sipDir)) {
            throw new IllegalStateException("Invalid SIP directory: " + sipDir);
        }
    }

    private void analyzeSpecificDataset(Path sipDir, String datasetName) throws IOException, StorageException {
        SIPFiles sipFiles = SIPFilesFinder.findRequiredFiles(sipDir);
        Map<String, DataSet> datasets = sipFiles.getStorage().getDataSets();

        DataSet dataset = datasets.get(datasetName);
        if (dataset == null) {
            throw new IllegalArgumentException("Dataset not found: " + datasetName);
        }

        processDataset(sipFiles, dataset);
    }

    private void analyzeDirectory(Path sipDir) throws IOException, StorageException {
        System.out.println("Starting mapping engine...");
        long startTime = System.currentTimeMillis();

        SIPFiles sipFiles = SIPFilesFinder.findRequiredFiles(sipDir);
        SIPCLI.logFoundFiles(sipFiles);

        DataSet sourceXML = sipFiles.getStorage().createDataSet(sipFiles.getSipDir().getFileName().toString());
        processDataset(sipFiles, sourceXML);

        SIPCLI.logTimingResults(startTime, System.currentTimeMillis());
    }

    private void processDataset(SIPFiles sipFiles, DataSet sourceXML) throws IOException {
        // See StatsModel::getMaxUniqueValueLength()
        FactModel hintsModel = new FactModel();
        String max = hintsModel.get(MAX_UNIQUE_VALUE_LENGTH);
        int maxUniqueValueLength = max == null ? Stats.DEFAULT_MAX_UNIQUE_VALUE_LENGTH : Integer.parseInt(max);

        AnalysisParser processor = new AnalysisParser(sourceXML, null, maxUniqueValueLength, new AnalysisParser.Listener() {
            @Override
            public void success(Stats stats) {
                try {
                    sourceXML.setStats(stats);
                } catch (StorageException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void failure(String message, Exception exception) {
            }
        });
        processor.setProgressListener(new CLIProgressListener());

        processor.run();
    }
}

@Command(name = "check", description = "Check a SIP directory structure")
class CheckCommand implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(CheckCommand.class);

    @Parameters(index = "0", description = "SIP directory path")
    private String sipDirPath;

    @Override
    public Integer call() {
        try {
            String userDir = Objects.requireNonNull(System.getProperty("user.dir"));
            Path workingDir = Paths.get(userDir);
            Path sipDir = SIPCLI.resolve(workingDir, sipDirPath);

            if (!Files.exists(sipDir) || !Files.isDirectory(sipDir)) {
                System.err.println("Invalid SIP directory: " + sipDir);
                return 1;
            }

            SIPFiles sipFiles = SIPFilesFinder.findRequiredFiles(sipDir);

            System.out.println("Check successful!");
            System.out.println("Found all required files:");
            System.out.println("- Record definition: " + sipFiles.getRecordDefinition());
            System.out.println("- Validation schema: " + sipFiles.getValidationSchema());
            System.out.println("- Mapping file: " + sipFiles.getMappingFile());
            System.out.println("- Source file: " + sipFiles.getSourceFile());

            return 0;
        } catch (Exception e) {
            logger.error("Check failed", e);
            System.err.println("Check failed: " + e.getMessage());
            return 1;
        }
    }
}

@Command(name = "list", description = "List available datasets in the specified SIP directory")
class ListCommand implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(ProcessCommand.class);
    @Parameters(index = "0", description = "SIP directory path")
    private String sipDirPath;

    @Option(names = { "-s", "--sort" }, description = "Sort by: name, size, date", defaultValue = "name")
    private String sortBy;

    @Override
    public Integer call() {
        try {
            String userDir = Objects.requireNonNull(System.getProperty("user.dir"));
            Path workingDir = Paths.get(userDir);
            Path sipDir = SIPCLI.resolve(workingDir, sipDirPath);

            // Validate directory exists
            if (!Files.exists(sipDir) || !Files.isDirectory(sipDir)) {
                System.err.println("Invalid SIP directory: " + sipDir);
                return 1;
            }

            // Get SIPFiles and storage
            SIPFiles sipFiles = SIPFilesFinder.findRequiredFiles(sipDir);
            Map<String, DataSet> datasets = sipFiles.getStorage().getDataSets();

            // Print header
            System.out.printf("Found %d datasets in %s%n", datasets.size(), sipDir.getParent());
            System.out.println("----------------------------------------");

            // Sort and print datasets
            datasets.values().stream()
                    .sorted(getComparator())
                    .forEach(this::printDatasetInfo);

            return 0;
        } catch (Exception e) {
            System.err.println("Error listing datasets: " + e.getMessage());
            logger.error("Error listing datasets", e);
            return 1;
        }
    }

    private Comparator<DataSet> getComparator() {
        switch (sortBy.toLowerCase()) {
            case "size":
                return Comparator.comparingLong(dataset -> {
                    try {
                        return dataset.getSourceFile().length();
                    } catch (Exception e) {
                        return 0L;
                    }
                });
            //case "date":
            // return Comparator.comparing(dataset -> {
            // try {
            // return Files.getLastModifiedTime(dataset.getSourceFile()).toInstant();
            // } catch (Exception e) {
            // return Instant.EPOCH;
            // }
            // });
            default:
                return Comparator.comparing(DataSet::getSpec);
        }
    }

    private void printDatasetInfo(DataSet dataset) {
        try {
            String name = dataset.getSpec();
            Path sourcePath = dataset.getSourceFile().toPath();
            long size = Files.size(sourcePath);
            String lastModified = Files.getLastModifiedTime(sourcePath)
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            System.out.printf("%-30s | %8s | %s%n",
                    name,
                    formatFileSize(size),
                    lastModified);
        } catch (Exception e) {
            System.out.printf("%-30s | Error reading dataset info%n", dataset.getSpec());
        }
    }

    private String formatFileSize(long size) {
        String[] units = { "B", "KB", "MB", "GB" };
        int unitIndex = 0;
        double fileSize = size;

        while (fileSize > 1024 && unitIndex < units.length - 1) {
            fileSize /= 1024;
            unitIndex++;
        }

        return String.format("%.1f %s", fileSize, units[unitIndex]);
    }
}

@Command(name = "clean", description = "Clean a SIP directory by removing old timestamped files")
class CleanCommand implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(CheckCommand.class);

    @Parameters(index = "0", description = "SIP directory path")
    private String sipDirPath;

    @Option(names = { "--all" }, description = "Remove all timestamped files instead of keeping the most recent")
    private boolean isAll = false;

    @Override
    public Integer call() {
        try {
            String userDir = Objects.requireNonNull(System.getProperty("user.dir"));
            Path workingDir = Paths.get(userDir);
            Path sipDir = SIPCLI.resolve(workingDir, sipDirPath);

            if (!Files.exists(sipDir) || !Files.isDirectory(sipDir)) {
                System.err.println("Invalid SIP directory: " + sipDir);
                return 1;
            }

            cleanDirectory(sipDir);

            return 0;
        } catch (Exception e) {
            logger.error("Clean failed", e);
            System.err.println("Clean failed: " + e.getMessage());
            return 1;
        }
    }

    private void cleanDirectory(Path sipDir) throws IOException, StorageException {
        System.out.println("Starting cleaning...");
        long startTime = System.currentTimeMillis();

        SIPFiles sipFiles = SIPFilesFinder.findRequiredFiles(sipDir);
        SIPCLI.logFoundFiles(sipFiles);

        DataSet sourceXML = sipFiles.getStorage().createDataSet(sipFiles.getSipDir().getFileName().toString());
        sourceXML.clean(isAll ? 0 : 1);

        SIPCLI.logTimingResults(startTime, System.currentTimeMillis());
    }

}

@Command(name = "server", description = "Start the gRPC mapping server")
class ServerCommand implements Callable<Integer> {

    @Option(names = { "-p",
            "--port" }, description = "Port to listen on (default: ${DEFAULT-VALUE})", defaultValue = "50051")
    private int port;

    @Option(names = { "-b",
            "--basePath" }, description = "Path to find sip directories (default: ${DEFAULT-VALUE})", defaultValue = ".")
    private String basePath;

    @Override
    public Integer call() {
        try {
            MappingServer server = new MappingServer(port, basePath);
            server.start();
            System.out.printf("gRPC server started on port %d%n", port);
            server.blockUntilShutdown();
            return 0;
        } catch (Exception e) {
            System.err.println("Server failed to start: " + e.getMessage());
            return 1;
        }
    }
}
