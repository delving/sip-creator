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
import org.apache.commons.cli.*;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamException;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.*;
import java.util.Map;
import java.util.TreeMap;

public class Main {

    public static void main(String[] args) throws ParseException {
        Options options = new Options();
        options.addOption("mapping", true, "mapping file");
        options.addOption("recdef", true, "record definition");
        options.addOption("validate", false, "run validation");
        options.addOption("xsd", true, "validation file");
        options.addOption("sourceFile", true, "source file");
        options.addOption("sourceString", true, "source string");
        options.addOption("localId", true, "record identifier");
        CommandLineParser parser = new BasicParser();
        CommandLine cmd = parser.parse(options, args);
        if (cmd.hasOption("mapping")) {
            System.out.println("rec-def: "+cmd.getOptionValue("recdef"));
            System.out.println("mapping: "+cmd.getOptionValue("mapping"));
            Boolean runValidate = cmd.hasOption("validate");
            System.out.println("run-validate: "+runValidate);
            System.out.println("xsd: "+cmd.getOptionValue("xsd"));
            System.out.println("source-file: "+cmd.getOptionValue("sourceFile"));
            System.out.println("source-string: "+cmd.getOptionValue("sourceString"));
            System.out.println("record-id: "+cmd.getOptionValue("localId"));
            try {
                try {
                    System.out.println(
                        "\n\n=====\nrecord: "+Main.processRecord(
                            cmd.getOptionValue("recdef"),
                            cmd.getOptionValue("xsd"),
                            cmd.getOptionValue("mapping"),
                            cmd.getOptionValue("sourceString"),
                            cmd.getOptionValue("localId")
                        )
                    );
                } catch (SAXException e) {
                    e.printStackTrace();
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                } catch (XMLStreamException e) {
                    e.printStackTrace();
                } catch (MappingException e) {
                    e.printStackTrace();
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        else {
            HelpFormatter help = new HelpFormatter();
            help.printHelp("sip-cli", options);
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

}
