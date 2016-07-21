package eu.delving.sip;

import com.google.common.io.CharSink;
import com.google.common.io.CharSource;
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

    private static final String HELP_HEADER = "SIP-cli will process a dataset for you, just like the GUI does. Either specify a dataset-dir, or specify the mapping, " +
        "recdef, facts and hints-files individually. The option d/dataset-dir takes precedence if specified and will cause the other options to be ignored." +
        "\n\nOutput will be on standard-out\n\n" +
        "Arguments: \n";

    private enum Mode {
        // will we get out mapping info from a dataset directory or a number of individual files?
        Directory, Files
    }

    private static final Option MAPPING_FILE_OPTION = Option.builder("m").longOpt("mapping").
        desc("Path to mapping file").hasArg().build();
    private static final Option REC_DEF_FILE_OPTION = Option.builder("r").longOpt("rec-def")
        .desc("Path to record-definition file").hasArg().build();
    private static final Option FACTS_FILE_OPTION = Option.builder("f").longOpt("facts")
        .desc("Path to facts file").hasArg().build();
    private static final Option HINTS_FILE_OPTION = Option.builder().longOpt("hints")
        .desc("Path to hints definition file").hasArg().build();
    private static final Option SOURCE_DATA_FILE_OPTION = Option.builder("s").longOpt("source")
        .desc("Path to .xml.gz source file").hasArg().build();

    private static final Option DATA_SET_DIR_OPTION = Option.builder("d").longOpt("data-set-dir")
        .desc("Path to data-set directory").hasArg().build();
    private static final Option REPORT_DEST_FILE = Option.builder().longOpt("report-file")
        .desc("Report file").hasArg().build();

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
            System.err.println("You must specify either a dataset dir or the set of input-files, not both");
            printHelp(options);
            System.exit(1);
        }

        Configuration configuration = parseConfiguration(mode.get(), cmd);
        CliApplication cliApplication = new CliApplication(configuration);
        cliApplication.process();
    }

    private static Configuration parseConfiguration(final Mode mode, final CommandLine cmd) {
        if (mode.equals(Mode.Directory)) {
            System.err.println("Directory mode not yet supported, exiting...");
            System.exit(1);
        }
        final CharSource recDef = openFile(cmd.getOptionValue(REC_DEF_FILE_OPTION.getOpt()));
        final CharSource mapping = openFile(cmd.getOptionValue(MAPPING_FILE_OPTION.getOpt()));
        final CharSource facts = openFile(cmd.getOptionValue(FACTS_FILE_OPTION.getOpt()));
        final CharSource hints = openFile(cmd.getOptionValue(HINTS_FILE_OPTION.getOpt()));
        final CharSource source = openFile(cmd.getOptionValue(SOURCE_DATA_FILE_OPTION.getOpt()));

        String reportDest = cmd.getOptionValue(REPORT_DEST_FILE.getOpt());
        final Optional<CharSink> reportOut = reportDest == null ?
            Optional.empty() : Optional.of(Files.asCharSink(new File(reportDest), Charset.forName("UTF-8")));

        final Configuration.Builder builder = new Configuration.Builder();
        return builder.
            setRecDef(recDef).
            setMappingFile(mapping).
            setSource(source).
            setFacts(facts).
            setHints(hints).
            setOut(System.out).
            setReportOut(reportOut).
            createConfiguration();
    }

    private static CharSource openFile(final String path) {
        return Files.asCharSource(new File(path), Charset.forName("UTF-8"));
    }

    private ReportWriter process() {
        return null;
    }


    public CliApplication(final Configuration configuration) {
        this.configuration = configuration;
    }

    private static Optional<Mode> fileOrDirMode(final CommandLine commandLine) {
        if (commandLine.hasOption(DATA_SET_DIR_OPTION.getOpt())) {
            return Optional.of(Mode.Directory);
        } else if (commandLine.hasOption(MAPPING_FILE_OPTION.getOpt()) &&
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

    static private class Configuration {
        public final CharSource recDef;
        public final CharSource mappingFile;
        public final CharSource source;
        public final CharSource facts;
        public final CharSource hints;

        public final OutputStream out;
        public final Optional<CharSink> reportOut;

        public Configuration(CharSource recDef, CharSource mappingFile, CharSource source, CharSource facts, CharSource hints, OutputStream out, Optional<CharSink> reportOut) {
            this.recDef = recDef;
            this.mappingFile = mappingFile;
            this.source = source;
            this.facts = facts;
            this.hints = hints;
            this.out = out;
            this.reportOut = reportOut;
        }

        static class Builder {
            private CharSource recDef;
            private CharSource mappingFile;
            private CharSource source;
            private CharSource facts;
            private CharSource hints;
            private OutputStream out;
            private Optional<CharSink> reportOut;

            public Builder setRecDef(CharSource recDef) {
                this.recDef = recDef;
                return this;
            }

            public Builder setMappingFile(CharSource mappingFile) {
                this.mappingFile = mappingFile;
                return this;
            }

            public Builder setSource(CharSource source) {
                this.source = source;
                return this;
            }

            public Builder setFacts(CharSource facts) {
                this.facts = facts;
                return this;
            }

            public Builder setHints(CharSource hints) {
                this.hints = hints;
                return this;
            }

            public Builder setOut(OutputStream out) {
                this.out = out;
                return this;
            }

            public Builder setReportOut(Optional<CharSink> reportOut) {
                this.reportOut = reportOut;
                return this;
            }

            public CliApplication.Configuration createConfiguration() {
                return new CliApplication.Configuration(recDef, mappingFile, source, facts, hints, out, reportOut);
            }
        }
    }
}
