package eu.delving.sip;

import com.google.common.io.CharSink;
import com.google.common.io.Files;
import eu.delving.sip.files.ReportWriter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Optional;

public class CliApplication {

    private final Configuration configuration;

    private static final String HELP_HEADER = "SIP-cli will process a dataset for you, just like the GUI does." +
        "\n\nOutput will be on stdout\n\n" +
        "Arguments: \n";

    private static final Option DATA_SET_DIR_OPTION = Option.builder("d").longOpt("data-set-dir").required()
        .desc("Path to data-set directory").hasArg().build();
    private static final Option REPORT_DEST_FILE = Option.builder("r").longOpt("report-file")
        .desc("Report file").hasArg().build();

    public CliApplication(final Configuration configuration) {
        this.configuration = configuration;
    }

    public static void main(String[] args) throws ParseException {

        final Options options = new Options();

        options.addOption(DATA_SET_DIR_OPTION);

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);


        Optional<Configuration> config = parseConfiguration(cmd);
        if (!config.isPresent()) {
            System.err.println("Unable to open dataSet directory or reportPath (if specified) is not writable.\nGiving up.");
            printHelp(options);
            System.exit(1);
        }
        CliApplication cliApplication = new CliApplication(config.get());
        cliApplication.process();
    }

    private static Optional<Configuration> parseConfiguration(final CommandLine cmd) {
        String path = cmd.getOptionValue(DATA_SET_DIR_OPTION.getOpt());
        String rpArg = cmd.getOptionValue(REPORT_DEST_FILE.getOpt());
        Optional<String> reportPath = Optional.ofNullable(rpArg);

        Optional<CharSink> reportOut = reportPath.
            map(p -> new File(p)).
            filter(file -> file.canWrite()).
            map(file -> Files.asCharSink(file, Charset.forName("UTF-8")));


        File dataSetDir = new File(path);
        if (!dataSetDir.canRead()) {
            return Optional.empty();
        }

        return Optional.of(new Configuration(dataSetDir, System.out, reportOut));
    }

    private ReportWriter process() {
        return null;
    }


    private static void printHelp(final Options options) {
        HelpFormatter help = new HelpFormatter();
        help.printHelp(HELP_HEADER, options);
    }

    static private class Configuration {
        public final File dataSetDir;
        public final OutputStream out;
        public final Optional<CharSink> reportOut;

        public Configuration(File dataSetDir, OutputStream out, Optional<CharSink> reportOut) {
            this.dataSetDir = dataSetDir;
            this.out = out;
            this.reportOut = reportOut;
        }
    }
}
