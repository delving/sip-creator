package eu.delving.sip;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharSink;
import com.google.common.io.Files;
import eu.delving.schema.SchemaRepository;
import eu.delving.sip.files.ReportWriter;
import eu.delving.sip.files.SchemaFetcher;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.http.client.HttpClient;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;

import static eu.delving.sip.base.HttpClientFactory.createHttpClient;

public class CliApplication {

    private final Configuration configuration;

    private static final String HELP_HEADER = "SIP-cli will process a dataset for you, just like the GUI does." +
        "\n\nOutput will be on stdout\n\n" +
        "Arguments: \n";

    protected static final Option DATA_SET_DIR_OPTION = Option.builder("d").longOpt("data-set-dir").required()
        .desc("Path to data-set directory").hasArg().build();
    protected static final Option REPORT_DEST_FILE = Option.builder("r").longOpt("report-file")
        .desc("Report file").hasArg().build();

    protected static final Option SERVER_URL = Option.builder("s").longOpt("server-url")
        .desc("Narthex server url, defaults to " + Application.DEFAULT_NARTHEX_URL).build();

    private static List<Option> OPTIONS = ImmutableList.of(DATA_SET_DIR_OPTION, REPORT_DEST_FILE, SERVER_URL);

    public CliApplication(final Configuration configuration) {
        this.configuration = configuration;
    }

    public static void main(String[] args) throws ParseException {

        final Options options = new Options();
        OPTIONS.forEach(option -> options.addOption(option));

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);


        Optional<Configuration> config = parseConfiguration(cmd);
        if (!config.isPresent()) {
            System.err.println("Giving up.");
            printHelp(options);
            System.exit(1);
        }
        CliApplication cliApplication = new CliApplication(config.get());
        cliApplication.process();
    }


    private ReportWriter process() {
        //DataSet dataSet = new StorageImpl.DataSetImpl(configuration.dataSetDir);

        HttpClient httpClient = createHttpClient(configuration.serverUrl.toString());
        SchemaRepository schemaRepository;
        try {
            schemaRepository = new SchemaRepository(new SchemaFetcher(httpClient));
        } catch (IOException e) {
            throw new RuntimeException("Unable to create Schema Repository", e);
        }
        /*
        ResolverContext context = new ResolverContext();
        Storage storage = new StorageImpl(storageDir, schemaRepository, new CachedResourceResolver(context));
        context.setStorage(storage);
        context.setHttpClient(httpClient);
        */
        return null;
    }


    private static void printHelp(final Options options) {
        HelpFormatter help = new HelpFormatter();
        help.printHelp(HELP_HEADER, options);
    }

    @VisibleForTesting
    protected static Optional<Configuration> parseConfiguration(final CommandLine cmd) {
        Optional<String> dataSetPath = Optional.ofNullable(cmd.getOptionValue(DATA_SET_DIR_OPTION.getOpt()));
        if (!dataSetPath.isPresent()) {
            System.err.println("No dataset-dir specified.");
            return Optional.empty();
        }
        String rpArg = cmd.getOptionValue(REPORT_DEST_FILE.getOpt());
        Optional<String> reportPath = Optional.ofNullable(rpArg);

        Optional<CharSink> reportOut = reportPath.
            map(p -> new File(p)).
            filter(file -> file.canWrite()).
            map(file -> Files.asCharSink(file, Charset.forName("UTF-8")));

        if (rpArg != null && !reportOut.isPresent()) {
            System.err.println("Unable to write to report path.");
            return Optional.empty();
        }
        File dataSetDir = new File(dataSetPath.get());
        if (!dataSetDir.canRead()) {
            System.err.println("Unable to open dataSet directory.");
            return Optional.empty();
        }

        Optional<URL> url = resolveUrl(Optional.ofNullable(cmd.getOptionValue(SERVER_URL.getOpt())));

        if (!url.isPresent()) {
            System.err.println("Invalid serverUrl:" + cmd.getOptionValue(SERVER_URL.getOpt()));
            return Optional.empty();
        }


        return Optional.of(new Configuration(dataSetDir, System.out, reportOut, url.get()));
    }

    private static Optional<URL> resolveUrl(Optional<String> nonDefaultUrl) {
        String urlToUse = nonDefaultUrl.isPresent() ? nonDefaultUrl.get() : Application.DEFAULT_NARTHEX_URL;
        try {
            URL url = new URL(urlToUse);
            return Optional.of(url);
        } catch (MalformedURLException e) {
            return Optional.empty();
        }
    }

    static protected class Configuration {
        public final File dataSetDir;
        public final OutputStream out;
        public final Optional<CharSink> reportOut;
        public final URL serverUrl;

        public Configuration(File dataSetDir, OutputStream out, Optional<CharSink> reportOut, URL serverUrl) {
            this.dataSetDir = dataSetDir;
            this.out = out;
            this.reportOut = reportOut;
            this.serverUrl = serverUrl;
        }
    }
}
