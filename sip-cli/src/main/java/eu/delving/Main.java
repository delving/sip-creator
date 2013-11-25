package eu.delving;

import org.apache.commons.cli.*;

public class Main {
    public static void main(String[] args) throws ParseException {
        Options options = new Options();
        options.addOption("m", true, "mapping file");
        CommandLineParser parser = new BasicParser();
        CommandLine cmd = parser.parse(options, args);
        if (cmd.hasOption("m")) {
            String value = cmd.getOptionValue("m");
            System.out.println("m: "+value);
        }
        else {
            HelpFormatter help = new HelpFormatter();
            help.printHelp("sip-cli", options);
        }
    }
}