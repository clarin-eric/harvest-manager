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

import ORG.oclc.oai.harvester2.verb.*;
import net.sf.saxon.s9api.SaxonApiException;
import nl.mpi.oai.harvester.utils.DocumentSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.nio.file.Path;

/**
 * <br> Factory for OAI protocol objects <br><br>
 * <p>
 * By injecting the factory into harvesting package constructors, when testing,
 * the origin of OAI response type objects can be influenced. Instead of getting
 * a real response from an OAI endpoint, a test helper can mock a response. In
 * this way a helper takes over the role of an OAI provider.
 *
 * @author Kees Jan van de Looij (Max Planck Institute for Psycholinguistics)
 */
public class OAIFactory {

    // an object implementing the OAI interface
    OAIInterface oaiInterface = null;

    // for some verbs, remember the resumption token
    private String resumptionToken = null;

    /**
     * <br> Connect an object that implements the OAI interface <br><br>
     * <p>
     * Normally, when not testing, nothing will be connected. Therefore, this
     * method returns null. By spying on the factory, when using Mockito, can
     * the test can return an object that implements the interface. Typically,
     * a test helper object would be suited: it can receive the generated
     * metadata and compare it to the predefined data.
     *
     * @return an object implementing the OAI interface
     */
    public OAIInterface connectInterface() {

        return oaiInterface;
    }

    /**
     * <br> Create a list of metadata prefixes <br><br>
     *
     * @param endpointURI the endpoint URI
     * @return the OAI response
     */
    DocumentSource createListMetadataFormats(String endpointURI, int timeout) throws 
            IOException,
            ParserConfigurationException,
            SAXException,
            TransformerException,
            NoSuchFieldException {

        // the verb response
        DocumentSource response;

        oaiInterface = connectInterface();

        // check if the client connected an object the interface
        if (oaiInterface == null) {
            // no object connected
            try {
                HarvesterVerb verb = new ListMetadataFormats(endpointURI, timeout);
                response = verb.getDocumentSource();
            } catch (IOException
                    | ParserConfigurationException
                    | SAXException
                    | TransformerException e) {
                e.printStackTrace();
                throw(e);
            }
        } else {
            // let the object connected return the OAI response
            response = oaiInterface.newListMetadata(endpointURI);
        }

        return response;
    }

    /**
     * <br> Create a list records object <br><br>
     *
     * @param endpoint the endpoint URI
     * @param resumptionToken the resumption token
     * @return the OAI response
     */
    DocumentSource createListRecords(String endpoint, String resumptionToken, int timeout, Path temp) throws
        IOException,
        ParserConfigurationException,
        SAXException,
        TransformerException,
        NoSuchFieldException,
        XMLStreamException, SaxonApiException {

        // the verb response
        DocumentSource response;

        oaiInterface = connectInterface();

        // check if the client connected an object the interface
        if (oaiInterface == null) {
            // no object connected
            try {
                HarvesterVerb verb = new ListRecords(endpoint, resumptionToken, timeout, temp);
                response = verb.getDocumentSource();
                this.resumptionToken = ((ListRecords) verb).getResumptionToken();
            } catch (IOException | ParserConfigurationException | SAXException | TransformerException | NoSuchFieldException | XMLStreamException | SaxonApiException e) {
                e.printStackTrace();
                throw(e);
            }
        } else {
            // let the object connected return the OAI response

            response = oaiInterface.newListRecords(endpoint, resumptionToken);
            this.resumptionToken = oaiInterface.getResumptionToken();
        }

        return response;
    }

    /**
     * <br> Create a list records object <br><br>
     *
     * @param endpoint the endpoint URI
     * @param fromDate the start of the date window on the records
     * @param untilDate the end of the date window on the records
     * @param set the set the records should be in
     * @param metadataPrefix the metadata prefix the records should have
     * @return the OAI response
     */
    DocumentSource createListRecords(String endpoint, String fromDate, String untilDate, String set,
                               String metadataPrefix, int timeout, Path temp) throws
        IOException,
        ParserConfigurationException,
        SAXException,
        TransformerException,
        NoSuchFieldException,
        XMLStreamException, SaxonApiException {

        // the verb response
        DocumentSource response;

        oaiInterface = connectInterface();

        // check if the client connected an object the interface
        if (oaiInterface == null) {
            // no object connected
            try {
                HarvesterVerb verb = new ListRecords(endpoint, fromDate, untilDate, set, metadataPrefix, timeout, temp);
                response = verb.getDocumentSource();
                resumptionToken = ((ListRecords) verb).getResumptionToken();
            } catch (IOException | ParserConfigurationException | SAXException | TransformerException | NoSuchFieldException | XMLStreamException | SaxonApiException e) {
                e.printStackTrace();
                throw(e);
            }
        } else {
            // let the object connected return the OAI response

            response = oaiInterface.newListRecords(endpoint, fromDate, untilDate, set, metadataPrefix);
            resumptionToken = oaiInterface.getResumptionToken();
        }

        return response;
    }

    /**
     * <br> Create a get record object <br><br>
     *
     * @param endpoint the endpoint URI
     * @param recordIdentifier the record identifier
     * @param metadataPrefix the metadata prefix
     * @return the OAI response
     */
    DocumentSource createGetRecord(String endpoint, String recordIdentifier, String metadataPrefix) throws
            IOException,
            ParserConfigurationException,
            SAXException,
            TransformerException,
            NoSuchFieldException {

        // the verb response
        DocumentSource response;

        oaiInterface = connectInterface();

        // check if the client connected an object the interface
        if (oaiInterface == null) {
            // no object connected
            try {
                HarvesterVerb verb = new GetRecord(endpoint, recordIdentifier, metadataPrefix);
                response = verb.getDocumentSource();
            } catch (IOException
                    | ParserConfigurationException
                    | SAXException
                    | TransformerException e) {
                e.printStackTrace();
                throw(e);
            }
        } else {
            // let the object connected return the OAI response

            response = oaiInterface.newGetRecord(endpoint, recordIdentifier, metadataPrefix);
            resumptionToken = oaiInterface.getResumptionToken();
        }

        return response;
    }

    /**
     * <br> Create a list identifiers object <br><br>
     *
     * @param endpoint endpoint URI
     * @param resumptionToken resumption token
     * @return the OAI response
     */
    DocumentSource createListIdentifiers(String endpoint, String resumptionToken, int timeout) throws
        IOException,
        ParserConfigurationException,
        SAXException,
        TransformerException,
        NoSuchFieldException,
        XMLStreamException, SaxonApiException {

        // the verb response
        DocumentSource response;

        oaiInterface = connectInterface();

        // check if the client connected an object the interface
        if (oaiInterface == null) {
            // no object connected
            try {
                HarvesterVerb verb = new ListIdentifiers(endpoint, resumptionToken, timeout);
                response = verb.getDocumentSource();
                this.resumptionToken = ((ListIdentifiers) verb).getResumptionToken();
            } catch (IOException | ParserConfigurationException | SAXException | TransformerException | NoSuchFieldException | XMLStreamException | SaxonApiException e) {
                e.printStackTrace();
                throw(e);
            }
        } else {
            // let the object connected return the OAI response

            response = oaiInterface.newListIdentifiers(endpoint, resumptionToken);
            this.resumptionToken = oaiInterface.getResumptionToken();
        }

        return response;
    }

    /**
     * <br> Create a list identifiers object <br><br>
     *
     * @param endpoint the endpoint URI
     * @param fromDate the start of the date window on the records
     * @param untilDate the end of the date window on the records
     * @param set the set the records should be in
     * @param metadataPrefix the metadata prefix the records should have
     * @return the OAI response
     */
    DocumentSource createListIdentifiers(String endpoint, String fromDate, String untilDate,
                                   String set, String metadataPrefix, int timeout) throws
        IOException,
        ParserConfigurationException,
        SAXException,
        TransformerException,
        NoSuchFieldException,
        XMLStreamException, SaxonApiException {

        // the verb response
        DocumentSource response;

        oaiInterface = connectInterface();

        // check if the client connected an object the interface
        if (oaiInterface == null) {
            // no object connected
            try {
                HarvesterVerb verb = new ListIdentifiers(endpoint, fromDate, untilDate, set, metadataPrefix, timeout);
                response = verb.getDocumentSource();
                resumptionToken = ((ListIdentifiers) verb).getResumptionToken();
            } catch (IOException | ParserConfigurationException | SAXException | TransformerException | NoSuchFieldException | XMLStreamException | SaxonApiException e) {
                e.printStackTrace();
                throw(e);
            }
        } else {
            // let the object connected return the OAI response

            response = oaiInterface.newListIdentifiers(endpoint, fromDate, untilDate, set, metadataPrefix);
            resumptionToken = oaiInterface.getResumptionToken();
        }

        return response;
    }

    /**
     * <br> Get the resumption token
     *
     * @return the resumption token
     */
    public String getResumptionToken() {

        return resumptionToken;

    }
}
