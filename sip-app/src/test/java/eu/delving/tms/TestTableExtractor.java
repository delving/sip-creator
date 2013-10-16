package eu.delving.tms;

import org.junit.After;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Test the TableExtractor.  This is currently the way to run the thing too.
 *
 * To make it work, uncomment three functions:
 *  - the @Before function for your database of choice (createConnectionPostgres)
 *  - the @Test function of your choice (testDumpPRIMUS)
 *
 * @author Gerald de Jong <gerald@delving.eu>
 */

public class TestTableExtractor {

    private Connection connection;

//    @Before
//    public void createConnectionPostgres() throws ClassNotFoundException, SQLException {
//        Class.forName("org.postgresql.Driver");
//        Properties props = new Properties();
//        props.setProperty("user", "fluxe");
//        connection = DriverManager.getConnection("jdbc:postgresql:fluxe", props);
//    }

//    @Before
//    public void createConnectionMysql() throws ClassNotFoundException, SQLException {
//        Class.forName("com.mysql.jdbc.Driver");
//        Properties props = new Properties();
//        props.setProperty("user", "gerald");
//        props.setProperty("password", "delving");
//        connection = DriverManager.getConnection("jdbc:mysql://localhost/ca", props);
//    }

//    @Before
//    public void createConnectionTMS() throws ClassNotFoundException, SQLException {
//        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
//        Properties props = new Properties();
//        props.setProperty("user", "");
//        props.setProperty("password", "");
//        connection = DriverManager.getConnection("jdbc:sqlserver://192.168.0.1:1433;databaseName=TMS", props);
//    }

    @Test // because no tests causes problems
    public void fakeTest() {
    }

    @After
    public void closeConnection() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

//    @Test
//    public void testIntrospectionCollectiveAccess() throws SQLException, IOException {
//        RelationalProfile profile = RelationalProfile.createProfile(connection);
//        FileWriter out = new FileWriter("/tmp/ca-rdbms-profile.xml");
//        getStreamFor(RelationalProfile.class).toXML(profile, out);
//        out.close();
//    }

//    @Test
//    public void testDumpCollectiveAccess() throws ClassNotFoundException, SQLException, XMLStreamException, IOException {
//        URL resource = getClass().getResource("/extractor/ca-rdbms-profile.xml");
//        RelationalProfile profile = (RelationalProfile) getStreamFor(RelationalProfile.class).fromXML(resource);
//        profile.resolve();
//        TableExtractor tableExtractor = new TableExtractor(connection, profile);
//        tableExtractor.setMaxRows(100); // todo: just for testing
//        tableExtractor.fillCaches();
//        OutputStream outputStream = new FileOutputStream(new File("/tmp/ca-dump.xml"));
//        tableExtractor.dumpTo(outputStream);
//        outputStream.close();
//    }

//    @Test
//    public void testIntrospectionTMS() throws SQLException, IOException {
//        RelationalProfile profile = RelationalProfile.createProfile(connection);
//        FileWriter out = new FileWriter("/tmp/tms-rdbms-profile.xml");
//        getStreamFor(RelationalProfile.class).toXML(profile, out);
//        out.close();
//    }

//    @Test
//    public void testDumpTMS() throws ClassNotFoundException, SQLException, XMLStreamException, IOException, UnsupportedEncodingException {
//        URL resource = getClass().getResource("/extractor/tms-rdbms-profile.xml");
//        RelationalProfile profile = (RelationalProfile) getStreamFor(RelationalProfile.class).fromXML(resource);
//        profile.resolve();
//        TableExtractor tableExtractor = new TableExtractor(connection, profile);
//        tableExtractor.setMaxRows(100); // todo: just for testing
//        tableExtractor.fillCaches();
//        OutputStream outputStream = new FileOutputStream(new File("/tmp/tms-dump.xml"));
//        tableExtractor.dumpTo(outputStream);
//        outputStream.close();
//    }

//    @Test
//    public void testIntrospectionPRIMUS() throws SQLException, IOException {
//        URL queryResource = getClass().getResource("/extractor/exhibitions-rdbms-queries.xml");
//        RelationalProfile.QueryDefinitions definitions = (RelationalProfile.QueryDefinitions) getStreamFor(RelationalProfile.QueryDefinitions.class).fromXML(queryResource);
//        RelationalProfile profile = RelationalProfile.createProfile(connection, definitions);
//        FileWriter out = new FileWriter("/tmp/exhibitions-rdbms-profile.xml");
//        getStreamFor(RelationalProfile.class).toXML(profile, out);
//        out.close();
//    }

//    @Test
//    public void testDumpPRIMUS() throws ClassNotFoundException, SQLException, XMLStreamException, IOException, UnsupportedEncodingException {
//        URL resource = getClass().getResource("/extractor/exhibitions-rdbms-profile.xml");
//        RelationalProfile profile = (RelationalProfile) getStreamFor(RelationalProfile.class).fromXML(resource);
//        profile.resolve();
//        TableExtractor tableExtractor = new TableExtractor(connection, profile);
//        OutputStream outputStream = new FileOutputStream(new File("/tmp/exhibitions-dump.xml"));
//        tableExtractor.dumpTo(outputStream);
//        outputStream.close();
//    }

}