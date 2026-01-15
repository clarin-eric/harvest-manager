/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.mpi.oai.harvester.control;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import com.google.common.base.Charsets;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import nl.mpi.oai.harvester.Provider;
import nl.mpi.oai.harvester.action.Action;
import nl.mpi.oai.harvester.action.ActionSequence;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.junit.Assert.*;
import org.junit.ClassRule;

/**
 *
 * @author Twan Goosen <twan@clarin.eu>
 */
public class ConfigurationTest {

    private final static Logger logger = LoggerFactory.getLogger(ConfigurationTest.class);
    private static final String BASIC_CONFIG_RESOURCE = "/config/test-config-basic.xml";

    private static final String REGISTRY_PATH = "/model";
    private static final String REGISTRY_CENTRE_INFO = REGISTRY_PATH + "/Centre";
    private static final String REGISTRY_CENTRE_RESOURCE = "/centre-registry-Centre.json";
    private static final String REGISTRY_ENDPOINT_INFO = REGISTRY_PATH + "/OAIPMHEndpoint";
    private static final String REGISTRY_ENDPOINT_RESOURCE = "/centre-registry-OAIPMHEndpoint.json";
    private static final String REGISTRY_SET_INFO = REGISTRY_PATH + "/OAIPMHEndpointSet";
    private static final String REGISTRY_SET_RESOURCE = "/centre-registry-OAIPMHEndpointSet.json";
    private static final String REGISTRY_CONSORTIUM_INFO = REGISTRY_PATH + "/Consortium";
    private static final String REGISTRY_CONSORTIUM_RESOURCE = "/centre-registry-Consortium.json";

    private static Configuration BASIC_CONFIG;

    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(8089);

    @Rule
    public WireMockClassRule wireMockInstanceRule = wireMockRule;

    @Rule
    public TemporaryFolder workdir = new TemporaryFolder();

    private final Function<String, String> testConfigFilter
            = line -> line.replaceAll("\\{\\{workdir\\}\\}", workdir.getRoot().getAbsolutePath());

    @Test
    public void testReadConfig() throws Exception {
        final Configuration config = getBasicConfig();
        assertNotNull(config);
    }

    @Test
    public void testBasicProps() throws Exception {
        final Configuration config = getBasicConfig();
        assertEquals(false, config.isDryRun());
        assertEquals(false, config.isIncremental());
    }

    @Test
    public void testActionSequences() throws Exception {
        final List<ActionSequence> actionSequences = getBasicConfig().getActionSequences();

        assertNotNull(actionSequences);
        assertEquals(4, actionSequences.size());
        assertEquals("namespace", actionSequences.get(0).getInputFormat().getType());
        assertEquals("http://www.clarin.eu/cmd/1", actionSequences.get(0).getInputFormat().getValue());
        
        final List<ResourcePool<Action>> actions = actionSequences.get(0).getActions();
        assertEquals(7, actions.size());
    }

    @Test
    public void testProviders() throws Exception {
        final List<Provider> providers = getBasicConfig().getProviders();
        assertNotNull(providers);
        assertEquals(1, providers.size());
        assertEquals("https://www.meertens.knaw.nl/flat/oai2", providers.get(0).getOaiUrl());
    }

    @Test
    public void testImportFromRegistry() throws Exception {
        //set up mock centre registry REST XML server
        final String registryURl = setUpMockRegistry();
        final Function<String, String> configFilter
                = testConfigFilter.andThen(line -> line.replaceAll("\\{\\{registryUrl\\}\\}", registryURl));

        final File configFile = fileForResource("/config/test-config-import.xml", configFilter);

        final Configuration configuration = new Configuration().readConfig(configFile.getAbsolutePath());

        final List<Provider> providers = configuration.getProviders();
        assertNotNull(providers);
        assertEquals(50, providers.size());

        //check specific provider
        {
            final Optional<Provider> prov = providers.stream().filter(p -> p.getOaiUrl().equals("http://www.phonetik.uni-muenchen.de/cgi-bin/BASRepository/oaipmh/oai.pl")).findAny();
            assertTrue(prov.isPresent());

            //check specific providers for sets; first has two specified in registry response, but one is excluded
            final List<String> sets = Arrays.asList(prov.get().getSets());
            assertEquals(1, sets.size());
            assertTrue(sets.contains("test-set2"));
        }

        //second provider
        {
            final Optional<Provider> prov = providers.stream().filter(p -> p.getOaiUrl().equals("http://www.nb.no/clarino/oai")).findAny();
            assertTrue(prov.isPresent());

            //should have no sets
            assertNull(prov.get().getSets());
        }
    }

    private Configuration getBasicConfig() throws Exception {
        synchronized (ConfigurationTest.class) {
            if (BASIC_CONFIG == null) {
                BASIC_CONFIG = readConfig(BASIC_CONFIG_RESOURCE);
            }
        }
        return BASIC_CONFIG;
    }

    private Configuration readConfig(String name) throws Exception {
        final String filename = pathForResource(name);
        final Configuration config = new Configuration();
        config.readConfig(filename);
        return config;
    }

    private String pathForResource(String resource) {
        return fileForResource(resource, testConfigFilter).getAbsolutePath();
    }

    private File fileForResource(String resource, Function<String, String> filter) {
        try (InputStream stream = getClass().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("Resource not found");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, Charsets.UTF_8.name()))) {
                final File tempFile = File.createTempFile(getClass().getName(), ".xml");
                tempFile.deleteOnExit();

                logger.debug("Resource {} -> file on disk {}", resource, tempFile.getAbsoluteFile());

                try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        writer.write(filter.apply(line));
                        writer.newLine();
                    }
                    return tempFile;
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException("Error while reading resource for test: " + resource, ex);
        }
    }

    private String setUpMockRegistry() throws IOException {

        //set up mock centre registry REST JSON server
        stubFor(get(urlEqualTo(REGISTRY_CENTRE_INFO))
                .willReturn(aResponse()
                        .withBody(getResourceAsString(REGISTRY_CENTRE_RESOURCE))));
        stubFor(get(urlEqualTo(REGISTRY_ENDPOINT_INFO))
                .willReturn(aResponse()
                        .withBody(getResourceAsString(REGISTRY_ENDPOINT_RESOURCE))));
        stubFor(get(urlEqualTo(REGISTRY_SET_INFO))
                .willReturn(aResponse()
                        .withBody(getResourceAsString(REGISTRY_SET_RESOURCE))));
        stubFor(get(urlEqualTo(REGISTRY_CONSORTIUM_INFO))
                .willReturn(aResponse()
                        .withBody(getResourceAsString(REGISTRY_CONSORTIUM_RESOURCE))));

        final String registryURl = "http://localhost:" + wireMockRule.getOptions().portNumber() + REGISTRY_PATH;

        return registryURl;
    }

    private String getResourceAsString(String resourceName) throws IOException {
        final String registryOverviewString;
        try (InputStream infoResourceStream = getClass().getResourceAsStream(resourceName)) {
            registryOverviewString = IOUtils.toString(infoResourceStream, Charsets.UTF_8.name());
        }
        return registryOverviewString;
    }
}
