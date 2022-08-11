package eu.delving.sip;

import eu.delving.XStreamFactory;
import eu.delving.metadata.RecDef;
import eu.delving.sip.webservice.MappingServiceHandler;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;

class MappingServiceApp {

    public static void main(String[] args) throws Exception {
        XStreamFactory.init();
        RecDef.read(new FileInputStream("/home/q/PocketMapper/work/edm_5.2.6_record-definition.xml"));

        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(8090);
        server.setConnectors(new Connector[] {connector});

        ServletHandler handler = new ServletHandler();
        handler.addServletWithMapping(MappingServiceHandler.class, "/status");
        server.setHandler(handler);
        server.start();
    }
}


