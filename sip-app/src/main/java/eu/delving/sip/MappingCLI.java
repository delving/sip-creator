package eu.delving.sip;

import eu.delving.groovy.GroovyCodeResource;
import eu.delving.metadata.RecDef;
import eu.delving.metadata.RecDefTree;
import eu.delving.metadata.RecMapping;
import eu.delving.sip.cli.CLIDatasetImpl;
import eu.delving.sip.cli.CLIFeedback;
import eu.delving.sip.cli.CLIProcessorListener;
import eu.delving.sip.cli.CLIProgressListener;
import eu.delving.sip.files.DataSet;
import eu.delving.sip.model.SipModel;
import eu.delving.sip.xml.FileProcessor;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class MappingCLI {

    private final GroovyCodeResource groovyCodeResource = new GroovyCodeResource(getClass().getClassLoader());

    private static Path resolve(Path workingDir, String uri) {
        Path path = Paths.get(uri);
        if (path.isAbsolute()) {
            return path;
        }
        return workingDir.resolve(path);
    }

    public static void main(String[] args) throws Exception {
        String userDir = Objects.requireNonNull(System.getProperty("user.dir"));
        Path workingDir = Paths.get(userDir);
        if (!Files.exists(workingDir) || !Files.isDirectory(workingDir)) {
            throw new IllegalStateException("Invalid working dir: " + workingDir);
        }

        if (args.length <= 2 || args.length >= 6) {
            System.out.println("Invalid amount of arguments supplied: " + args.length);
            exitWithHelpMessage();
        }

        Path inputFile = resolve(workingDir, args[0]);
        if (!Files.exists(inputFile)) {
            System.out.println("Input file does not exist: " + inputFile);
            exitWithHelpMessage();
        }

        Path mappingFile = resolve(workingDir, args[1]);
        if (!Files.exists(mappingFile)) {
            System.out.println("Mapping file does not exist: " + mappingFile);
            exitWithHelpMessage();
        }

        Path recDefFile = resolve(workingDir, args[2]);
        if (!Files.exists(recDefFile)) {
            System.out.println("Record definition file does not exist: " + recDefFile);
            exitWithHelpMessage();
        }

        Path validationFile = null;
        if (args.length >= 4) {
            if (!args[3].equals("false")) {
                validationFile = resolve(workingDir, args[3]);
                if (!Files.exists(validationFile)) {
                    System.out.println("Validation file does not exist: " + validationFile);
                    exitWithHelpMessage();
                }
            }
        }

        Path outputDir = mappingFile.getParent();
        if (args.length == 5) {
            outputDir = resolve(workingDir, args[4]);
        }
        if (outputDir == null) {
            System.out.println("Parent dir of " + mappingFile + " is not a valid output dir");
            exitWithHelpMessage();
        }
        if (Files.exists(outputDir)) {
            if (!Files.isDirectory(outputDir)) {
                System.out.println("Output dir is not a directory: " + outputDir);
                exitWithHelpMessage();
            }
        } else {
            System.out.println("Creating directories: " + outputDir);
            Files.createDirectories(outputDir);
        }

        MappingCLI mappingCLI = new MappingCLI();
        mappingCLI.startMapping(inputFile, mappingFile, recDefFile, validationFile, outputDir);
    }

    private static void exitWithHelpMessage() {
        System.exit(-1);
    }

    public void startMapping(Path inputFile,
                             Path mappingFile,
                             Path recDefFile,
                             Path validationFile,
                             Path outputDir) throws Exception {
        System.out.println("Starting mapping engine with:");
        System.out.println("Input file: " + inputFile);
        System.out.println("Mapping file: " + inputFile);
        System.out.println("Rec definition file: " + inputFile);
        System.out.println("Validation file: " + inputFile);
        System.out.println("Output directory: " + outputDir);

        processSourceXML(inputFile, mappingFile, recDefFile, validationFile, outputDir);
    }

    private void processSourceXML(Path inputFile,
                                  Path mappingFile,
                                  Path recDefFile,
                                  Path validationFile,
                                  Path outputDir) throws IOException {
        RecMapping recMapping = getRecMapping(mappingFile, recDefFile);
        DataSet sourceXML = new CLIDatasetImpl(inputFile, outputDir);

        FileProcessor.UriGenerator uriGenerator = new SipModel.Generator(
            "http://delving.org/narthex",
            mappingFile.toString().split("__")[1].replace(".xml", ""),
            recMapping.getPrefix());

        FileProcessor fileProcessor = new FileProcessor(
            new CLIFeedback(),
            validationFile != null,
            sourceXML,
            recMapping,
            false,
            groovyCodeResource,
            uriGenerator,
            new CLIProcessorListener()
        );
        fileProcessor.setProgressListener(new CLIProgressListener());
        fileProcessor.run();
    }

    private RecMapping getRecMapping(Path mappingFile, Path recFile) throws FileNotFoundException, UnsupportedEncodingException {
        RecDef recDef = RecDef.read(new FileInputStream(recFile.toFile()));
        RecDefTree recDefTree = RecDefTree.create(recDef);
        return RecMapping.read(new FileInputStream(mappingFile.toFile()), recDefTree);
    }
}
