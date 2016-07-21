package eu.delving.sip;

import org.apache.commons.cli.CommandLine;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CliApplicationTest {

    @Mock
    private CommandLine cmd;

    @Before
    public void before() {
        when(cmd.getOptionValue(CliApplication.DATA_SET_DIR_OPTION.getOpt())).thenReturn(System.getProperty("java.io.tmpdir"));
    }

    @Test
    public void testNoArgsShouldFail() {
        when(cmd.getOptionValue(CliApplication.DATA_SET_DIR_OPTION.getOpt())).thenReturn(null);
        Optional<CliApplication.Configuration> configuration = CliApplication.parseConfiguration(cmd);
        assertFalse("No configuration should exist", configuration.isPresent());
    }

    @Test
    public void shouldUseDefaultNarthexUrl() {
        CliApplication.Configuration configuration = CliApplication.parseConfiguration(cmd).get();
        assertEquals(Application.DEFAULT_NARTHEX_URL, configuration.serverUrl.toString());
    }

    @Test
    public void shouldFailOnInvalidNarthexUrl() {
        String invalidUrl = "foo";
        when(cmd.getOptionValue(CliApplication.SERVER_URL.getOpt())).thenReturn(invalidUrl);
        Optional<CliApplication.Configuration> configuration = CliApplication.parseConfiguration(cmd);
        assertFalse("No configuration should exist", configuration.isPresent());
    }

    @Test
    public void shouldUseValidNarthexUrl() {
        String validUrl = "http://foo";
        when(cmd.getOptionValue(CliApplication.SERVER_URL.getOpt())).thenReturn(validUrl);
        CliApplication.Configuration configuration = CliApplication.parseConfiguration(cmd).get();
        assertEquals(validUrl, configuration.serverUrl.toString());

    }

}