package eu.delving;

import org.apache.commons.cli.*;

import java.util.Optional;

public class CliApplication {

    private static final String HELP_HEADER = "SIP-cli will process a dataset for you, just like the GUI does. Either specify a dataset-dir, or specify the mapping, " +
        "recdef, facts and hints-files individually. The option d/dataset-dir takes precedence if specified and will cause the other options to be ignored." +
        "\n\nOutput will be on standard-out\n\n" +
        "Arguments: \n";

    private enum Mode {
        // will we get out mapping info from a dataset directory or a number of individual files?
        Directory, Files
    }

    private static final Option MAPPING_FILE_OPTION = Option.builder("m").longOpt("mapping").desc("Path to mapping file").hasArg().build();
    private static final Option REC_DEF_FILE_OPTION = Option.builder("r").longOpt("rec-def").desc("Path to record-definition file").hasArg().build();
    private static final Option FACTS_FILE_OPTION = Option.builder("f").longOpt("facts").desc("Path to facts file").hasArg().build();
    private static final Option HINTS_FILE_OPTION = Option.builder().longOpt("hints").desc("Path to hints definition file").hasArg().build();
    private static final Option SOURCE_DATA_FILE_OPTION = Option.builder("s").longOpt("source").desc("Path to .xml.gz source file").hasArg().build();

    private static final Option DATA_SET_DIR_OPTION = Option.builder("d").longOpt("data-set-dir").desc("Path to data-set directory").hasArg().build();

    public static void main(String[] args) throws ParseException {

        final Options options = new Options();
        options.addOption(MAPPING_FILE_OPTION);
        options.addOption(REC_DEF_FILE_OPTION);
        options.addOption(FACTS_FILE_OPTION);
        options.addOption(HINTS_FILE_OPTION);
        options.addOption(SOURCE_DATA_FILE_OPTION);
        options.addOption(DATA_SET_DIR_OPTION);

        CommandLineParser parser = new DefaultParser();

        CommandLine cmd = parser.parse(options, args);

        Optional<Mode> mode = fileOrDirMode(cmd);

        if (!mode.isPresent()) {
            printHelp(options);
            System.exit(0);
        }

        System.out.println("Mode: " + mode.get());
    }

    private static Optional<Mode> fileOrDirMode(final CommandLine commandLine) {
        if (commandLine.hasOption(DATA_SET_DIR_OPTION.getOpt())) {
            return Optional.of(Mode.Directory);
        }
        else if (commandLine.hasOption(MAPPING_FILE_OPTION.getOpt()) &&
            commandLine.hasOption(REC_DEF_FILE_OPTION.getOpt()) &&
            commandLine.hasOption(FACTS_FILE_OPTION.getOpt()) &&
            commandLine.hasOption(HINTS_FILE_OPTION.getOpt())
            ) {
            return Optional.of(Mode.Files);
        }
        return Optional.empty();
    }


    private static void printHelp(final Options options) {
        HelpFormatter help = new HelpFormatter();
        help.printHelp(HELP_HEADER, options);
    }
}
