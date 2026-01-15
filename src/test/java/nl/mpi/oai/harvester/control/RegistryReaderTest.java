/*
 * Copyright (C) 2014, The Max Planck Institute for
 * Psycholinguistics.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * A copy of the GNU General Public License is included in the file
 * LICENSE-gpl-3.0.txt. If that file is missing, see
 * <http://www.gnu.org/licenses/>.
 */
package nl.mpi.oai.harvester.control;

import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.NodeList;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import com.google.common.base.Charsets;
import java.util.Collection;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Tests for RegistryReader. (Only parsing of canned responses is tested. No
 * connection to a registry is made.)
 *
 * @author Lari Lampen (MPI-PL)
 * @author twan@clarin.eu
 * @author menzo.windhouwer@di.huc.knaw.nl
 */
public class RegistryReaderTest {

    private static final String REGISTRY_PATH = "/model";
    private static final String REGISTRY_CENTRE_INFO = REGISTRY_PATH + "/Centre";
    private static final String REGISTRY_CENTRE_RESOURCE = "/centre-registry-Centre.json";
    private static final String REGISTRY_ENDPOINT_INFO = REGISTRY_PATH + "/OAIPMHEndpoint";
    private static final String REGISTRY_ENDPOINT_RESOURCE = "/centre-registry-OAIPMHEndpoint.json";
    private static final String REGISTRY_SET_INFO = REGISTRY_PATH + "/OAIPMHEndpointSet";
    private static final String REGISTRY_SET_RESOURCE = "/centre-registry-OAIPMHEndpointSet.json";
    private static final String REGISTRY_CONSORTIUM_INFO = REGISTRY_PATH + "/Consortium";
    private static final String REGISTRY_CONSORTIUM_RESOURCE = "/centre-registry-Consortium.json";
    private String registryURl;
    
    private RegistryReader registry;

    @ClassRule
    public static WireMockClassRule wireMockRule = new WireMockClassRule(8089);

    @Rule
    public WireMockClassRule wireMockInstanceRule = wireMockRule;
    
    @Before
    public void setUp() throws Exception {
        registryURl = "http://localhost:" + wireMockRule.getOptions().portNumber() + REGISTRY_PATH;

        registry = new RegistryReader(new URL(registryURl));

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
          }

    /**
     * Test of getEndpoints method, of class RegistryReader.
     */
    @Test
    public void testGetEndpoints() throws Exception {
        List<String> result = registry.getEndpoints();
        assertEquals(50, result.size());
    }

    /**
     * Test of getEndpoint method, of class RegistryReader.
     */
    @Test
    public void testGetEndpoint() throws Exception {
        String expResult = "http://clarin.dk/oaiprovider/";

        List<String> result = registry.getEndpoints();
        assertEquals(expResult, result.get(0));
    }
    
    @Test
    public void testGetOaiSets() throws Exception {
        final String endpointUrl1 = "http://www.phonetik.uni-muenchen.de/cgi-bin/BASRepository/oaipmh/oai.pl";
        final String endpointUrl2 = "http://clarin.dk/oaiprovider/";
        
        final Map<String, Collection<CentreRegistrySetDefinition>> map = registry.getEndPointOaiPmhSetMap();
        assertEquals(50, map.size());
        assertTrue(map.containsKey(endpointUrl1));
        assertEquals(2, map.get(endpointUrl1).size());
        assertTrue(map.containsKey(endpointUrl2));
        assertEquals(0, map.get(endpointUrl2).size());
    }

    @Test
    public void testGetOaiPmhSetsNone() throws Exception {
        String endpoint = "http://www.clarin.eu";
        final Map<String, Collection<CentreRegistrySetDefinition>> map = registry.getEndPointOaiPmhSetMap();
        assertNull(map.get(endpoint));
    }
    
    @Test
    public void testEndpointMapping() throws Exception {
        String entry = registry.endpointMapping("http://clarin.dk/oaiprovider/","CLARIN DK OAI");
        assertEquals(entry,"\"http://clarin.dk/oaiprovider/\",\"CLARIN_DK_OAI\",\"The CLARIN, Centre at the \"\"University of Copenhagen\"\"\",\"CLARIN-DK\"");
    }
    
    private static String getResourceAsString(String resourceName) throws IOException {
        final String registryOverviewString;
        try (InputStream infoResourceStream = RegistryReaderTest.class.getResourceAsStream(resourceName)) {
            registryOverviewString = IOUtils.toString(infoResourceStream, Charsets.UTF_8.name());
        }
        return registryOverviewString;
    }
}
