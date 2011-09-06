/*
 * Copyright 2010 DELVING BV
 *
 * Licensed under the EUPL, Version 1.0 or as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the
 * Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.delving.sip.desktop;

import eu.delving.sip.desktop.windows.WindowId;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.awt.Dimension;
import java.awt.Point;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Store and retrieve preferences.
 *
 * @author Serkan Demirel <serkan@blackbuilt.nl>
 */
public class DesktopPreferencesImplTest implements Serializable {

    private static final String USERNAME = "bob";
    private static final String PASSWORD = "secret";
    private static final String SERVER_ADDRESS = "localhost";
    private static final String SPEC = "FakeCollection";
    private static final int SERVER_PORT = 9000;
    private static final Logger LOG = Logger.getRootLogger();
    private static final String WORKSPACE_PATH = "/Users/serkan.delving/Metadata/file-store/";

    private final DesktopPreferences desktopPreferences = new DesktopPreferencesImpl(getClass());

    @Before
    public void setUp() throws Exception {
        desktopPreferences.clear();
    }

    @Test
    public void testWorkspace() throws Exception {
        DesktopPreferences.Workspace workspace = new WorkspaceImpl(WORKSPACE_PATH);
        desktopPreferences.saveWorkspace(workspace);
        Assert.assertEquals(workspace, desktopPreferences.getWorkspace());
    }

    @Test
    public void testCredentials() throws Exception {
        LOG.info("Writing to preferences");
        DesktopPreferences.Credentials expected = new CredentialsImpl(USERNAME, PASSWORD, SERVER_ADDRESS, SERVER_PORT);
        desktopPreferences.saveCredentials(expected);
        DesktopPreferences.Credentials actual = desktopPreferences.getCredentials().iterator().next(); // todo: fix
        Assert.assertEquals(expected.getUsername(), actual.getUsername());
        Assert.assertEquals(expected.getPassword(), actual.getPassword());
        Assert.assertEquals(expected.getServerAddress(), actual.getServerAddress());
        Assert.assertEquals(expected.getServerPort(), actual.getServerPort());
    }

    @Test
    public void testCredentialsList() throws Exception {
        DesktopPreferences.Credentials test1 = new CredentialsImpl(USERNAME, PASSWORD, SERVER_ADDRESS, SERVER_PORT);
        DesktopPreferences.Credentials test2 = new CredentialsImpl("serkan", "password", "test.blackbuilt.nl", 9000);
        // This is a duplicate and should replace the previous one.
        DesktopPreferences.Credentials test3 = new CredentialsImpl("serkan", "password", "test.blackbuilt.nl", 9000);
        DesktopPreferences.Credentials test4 = new CredentialsImpl("serkan", "password", "some_host", 9000);
        desktopPreferences.saveCredentials(test1);
        desktopPreferences.saveCredentials(test2);
        desktopPreferences.saveCredentials(test3);
        desktopPreferences.saveCredentials(test4);
        Set<DesktopPreferences.Credentials> credentialsList = desktopPreferences.getCredentials();
        LOG.info("Reading credentials : " + credentialsList);
        Assert.assertTrue("Expecting 3 credentials, found " + credentialsList.size(), 3 == credentialsList.size());
    }

    @Test
    public void testDesktopState() throws Exception {
        List<WindowState> windows = new ArrayList<WindowState>();
        windows.add(new WindowState(WindowId.ANALYZE, new Dimension(200, 200), new Point(400, 211), true));
        windows.add(new WindowState(WindowId.NORMALIZE, new Dimension(200, 200), new Point(3, 400), false));
        windows.add(new WindowState(WindowId.MAPPING, new Dimension(555, 123), new Point(233, 51), false));
        DesktopPreferences.DesktopState desktopState = new DesktopStateImpl(SPEC, windows);
        desktopPreferences.saveDesktopState(desktopState);
        DesktopPreferences.DesktopState actual = desktopPreferences.getDesktopState();
        Assert.assertEquals(desktopState.getSpec(), actual.getSpec());
        Assert.assertEquals(desktopState.getWindowStates().size(), actual.getWindowStates().size());
    }

    @After
    public void tearDown() throws Exception {
        desktopPreferences.clear();
    }
}

