package eu.delving.tms;

import eu.delving.sip.xml.RelationalProfile;
import eu.delving.sip.xml.TableExtractor;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import static eu.delving.XStreamFactory.getStreamFor;

/**
 * Test the TableExtractor
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestTableExtractor {

    private Connection connection;

    @Before
    public void createConnection() throws ClassNotFoundException, SQLException {
        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        Properties props = new Properties();
        props.setProperty("user", "Delving");
        props.setProperty("password", "D3lv1ng");
        connection = DriverManager.getConnection("jdbc:sqlserver://192.168.0.1:1433;databaseName=TMS", props);
    }

    @After
    public void closeConnection() throws SQLException {
        connection.close();
    }

    @Ignore
    @Test
    public void testIntrospection() throws SQLException, IOException {
        RelationalProfile profile = RelationalProfile.createProfile(connection);
        FileWriter out = new FileWriter("/tmp/tms-rdbms-profile.xml");
        getStreamFor(RelationalProfile.class).toXML(profile, out);
        out.close();
    }

    @Ignore
    @Test
    public void testDumpTMS() throws ClassNotFoundException, SQLException, XMLStreamException, IOException, UnsupportedEncodingException {
        URL resource = getClass().getResource("/extractor/tms-rdbms-profile.xml");
        RelationalProfile profile = (RelationalProfile) getStreamFor(RelationalProfile.class).fromXML(resource);
        profile.resolve();
        TableExtractor tableExtractor = new TableExtractor(connection, profile);
        tableExtractor.setMaxRows(100); // todo: just for testing
        tableExtractor.fillCaches();
        OutputStream outputStream = new FileOutputStream(new File("/tmp/tms-dump.xml"));
        tableExtractor.dumpTo(outputStream);
        outputStream.close();
    }
}