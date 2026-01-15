
/*
 * Copyright (C) 2015, The Max Planck Institute for
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

package nl.mpi.oai.harvester.harvesting;

import nl.mpi.oai.harvester.Provider;
import nl.mpi.oai.harvester.action.ActionSequence;
import nl.mpi.oai.harvester.metadata.MetadataFactory;
import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

/**
 * <br> Test the list records and list identifiers scenario <br><br>
 *
 * A scenario for listing records or identifiers relies on an action sequence
 * object. Since it only needs two methods, the test can mock the object. Next
 * to this, a scenario needs an endpoint. The test calls on the test helper to
 * supply endpoints.
 *
 * Normally, a scenario would interact with a real OAI endpoint. Instead, by
 * letting the helper supply OAI responses, the scenario does not depend on a
 * real endpoint. This allows the helper to check if scenario yields the
 * expected results.
 *
 * To make things work, the test needs to arrange two things. First, it needs
 * to define mocked OAI responses. Test helper extensions in the package provide
 * these. By creating an object of one of these classes, the test implicitly
 * defines the responses.
 *
 * Second, instead of real responses, the OAI verb factory needs to obtain
 * mocked responses from the helper. To this end, the test will spy on the
 * factory. Similarly, it spies on the metadata factory to inspect the
 * harvesting results.
 *
 * Note: doReturn().when() differs from when().thenReturn() in that in the
 * when applying the second of the two the method indicated is effectively
 * invoked while in the case of the first expression it is not.
 *
 * @author Kees Jan van de Looij (Max Planck Institute for Psycholinguistics)
 */
public class ScenarioTest {

    /**
     * <br> Test the list records scenario
     */
    @Test
    public void listRecordsTest(){

        // create a helper
        ListRecordsTestHelper helper = new ListRecordsTestHelper();
        // do the test
        try {
            listHarvestingTest(helper);
        } catch (ParserConfigurationException
                | TransformerException
                | SAXException
                | NoSuchFieldException
                | IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * <br> Test the list identifiers scenario
     */
    @Test
    public void listIdentifiersTest(){

        // create a helper
        ListIdentifiersTestHelper helper = new ListIdentifiersTestHelper();
        // do the test
        try {
            listHarvestingTest(helper);
        } catch (ParserConfigurationException
                | TransformerException
                | SAXException
                | NoSuchFieldException
                | IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * <br> Follow a list scenario <br><br>
     *
     * Note: the scenario followed, listing identifiers or listing records,
     * depends on the helper specified.
     *
     * @param helper the test helper
     * @throws ParserConfigurationException
     * @throws TransformerException
     * @throws SAXException
     * @throws NoSuchFieldException
     * @throws IOException
     */
    public void listHarvestingTest(TestHelper helper) throws
             ParserConfigurationException,
             TransformerException,
             SAXException,
             NoSuchFieldException,
             IOException {

        // create a factory for OAI protocol objects
        OAIFactory oaiFactory = spy(new OAIFactory());
        // let the helper provide the OAI responses
        when(oaiFactory.connectInterface()).thenReturn(helper);

        // create a factory for metadata
        MetadataFactory metadataFactory = spy(new MetadataFactory());
        // let the helper check the data
        when(metadataFactory.connectInterface()).thenReturn(helper);

        // mock an action sequence
        ActionSequence sequence = mock(ActionSequence.class);
        // define what the mocked object needs to return
        when(sequence.getInputFormat()).thenReturn(helper.getMetadataFormats());

        // get the first provider from the helper
        Provider endpoint = helper.getFirstEndpoint();
        for (; ; ) {
            // create a scenario with the endpoint and the mocked sequence
            OAIScenario scenario = new OAIScenario(endpoint, sequence);

            // create a harvesting object
            FormatHarvesting formatHarvesting = new FormatHarvesting(oaiFactory,
                    endpoint, sequence);

            // follow the prefix list harvesting scenario
            List<String> prefixes = scenario.getPrefixes(formatHarvesting);

            if (helper instanceof ListRecordsTestHelper) {

                // create a record list harvesting object
                RecordListHarvesting recordListHarvesting = new
                        RecordListHarvesting(oaiFactory, endpoint, prefixes,
                        metadataFactory, null);

                // follow the record list harvesting scenario
                scenario.listRecords(recordListHarvesting);

            } else {

                // create a identifier list harvesting object
                IdentifierListHarvesting identifierListHarvesting = new
                        IdentifierListHarvesting(oaiFactory, endpoint, prefixes,
                        metadataFactory, null);

                // follow the identifier list harvesting scenario
                scenario.listIdentifiers(identifierListHarvesting);
            }

            // switch to the next endpoint
            endpoint = helper.getNextEndpoint();
            if (endpoint == null) {
                break;
            }
        }

        // determine if the test was successful
        if (!helper.success()) {
            fail();
        }
    }
}
