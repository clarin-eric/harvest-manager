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
 *
 * <http://www.gnu.org/licenses/>.
 */

package nl.mpi.oai.harvester.harvesting;

import net.sf.saxon.s9api.SaxonApiException;
import nl.mpi.oai.harvester.Provider;
import nl.mpi.oai.harvester.cycle.Endpoint;
import nl.mpi.oai.harvester.metadata.MetadataFactory;
import nl.mpi.oai.harvester.utils.DocumentSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * <br> List based record harvesting <br><br>
 *
 * This class provides list based harvesting with a concrete verb to base
 * requests on. Because supplies a specific verb, ListRecords, the response
 * processing needed is specific also. Hence the class also implements this
 * processing. <br><br>
 *
 * Since an endpoint might provide a metadata element in different sets, and
 * record harvesting might involve more than one set, a metadata record could
 * be presented to the client more than once. This class provides every record
 * only once. It uses the list provided by the superclass to remove duplicate
 * identifier and prefix pairs.
 *
 * Note: originally, this class was declared 'final'. With the addition of
 * tests based on Mockito, this qualifier was removed.
 *
 * @author Kees Jan van de Looij (Max Planck Institute for Psycholinguistics)
 * @author Lari Lampen (MPI-PL, xpath parsing)
 */
public class RecordListHarvesting extends ListHarvesting
        implements Harvesting {
    
    private static final Logger logger = LogManager.getLogger(RecordListHarvesting.class);

    /**
     * Associate endpoint data and desired prefix
     *  @param oaiFactory the OAI factory
     * @param provider the endpoint to address in the request
     * @param prefixes the prefixes returned by the endpoint
     * @param metadataFactory the metadata factory
     * @param endpoint
     */
    public RecordListHarvesting(OAIFactory oaiFactory, Provider provider,
                                List<String> prefixes, MetadataFactory metadataFactory, Endpoint endpoint) {

        super (oaiFactory, provider, prefixes, metadataFactory, endpoint);
        // supply the superclass with messages specific to requesting records
        message [0] = "Requesting more records with prefix ";
        message [1] = "Requesting records with prefix ";
        message [2] = "Cannot get ";

     }
   
    /**
     * <br> Create a request based on the two parameter ListRecords verb <br><br>
     *
     * This method creates a request following a resumption token: a command
     * based on the ListRecords verb and two parameters. It returns a ListRecord
     * object from the OCLC library.
     *
     * @param metadataPrefix metadata prefix
     * @param resumptionToken resumption token
     * @return the response to the request
     * @throws IOException IO problem
     * @throws ParserConfigurationException configuration problem
     * @throws SAXException XML problem
     * @throws TransformerException XSL problem
     * @throws NoSuchFieldException introspection problem
     */
    @Override
    public DocumentSource verb2(String metadataPrefix, String resumptionToken, int timeout) throws
        IOException,
        ParserConfigurationException,
        SAXException,
        TransformerException,
        NoSuchFieldException,
        XMLStreamException, SaxonApiException {

        document = oaiFactory.createListRecords(metadataPrefix, resumptionToken, timeout, provider.temp);

        // implement by returning ListRecords with the two parameters supplied
        return document;
    }

    /**
     * Create a request based on the five parameter ListRecords verb <br><br>
     *
     * This method creates the initial ListRecords request: a command based
     * on the ListRecords verb and five parameters. It returns a ListRecord
     * object from the OCLC library.
     *
     * @param endpoint endpoint URL
     * @param fromDate from date, for selective harvesting
     * @param untilDate until date, for selective harvesting
     * @param metadataPrefix metadata prefix
     * @param set set
     * @return the response to the request
     * @throws IOException IO problem
     * @throws ParserConfigurationException configuration problem
     * @throws SAXException XML problem
     * @throws TransformerException XSL problem
     * @throws NoSuchFieldException introspection problem
     */
    @Override
    public DocumentSource verb5(String endpoint, String fromDate, String untilDate, String metadataPrefix,
            String set, int timeout, Path temp) throws
        IOException,
        ParserConfigurationException,
        SAXException,
        TransformerException,
        NoSuchFieldException,
        XMLStreamException, SaxonApiException {

        document = oaiFactory.createListRecords(endpoint, fromDate, untilDate, metadataPrefix, set, timeout, temp);

        // implement by returning ListRecords with the five parameters supplied
        return document;
    }
    
    /**
     * <br> Get the resumption token associated with a specific response <br><br>
     *
     * This method implements a resumption token request by invoking the
     * getResumptionToken OCLC library method.
     *
     * @return the token
     */
    @Override
    public String getToken (){

        // check for protocol error
        if (document == null){
            throw new HarvestingException();
        }

        return oaiFactory.getResumptionToken();
    }

    /**
     * <br> Create a list of metadata elements from the response <br><br>
     *
     * This method filters a list of nodes from the response. The filter is
     * an XPath expression build around the ListRecords element, the element
     * that holds the metadata records. The parseResponse method takes the
     * list of nodes as input.
     *
     * Note: when listing records by listing identifiers first, the parsing
     * method does not act on the list of metadata elements gathered from a
     * single request. Instead, it parses list of all identifiers of records
     * available from the endpoint.
     *
     * @return true if the list was successfully created, false otherwise
     */
    @Override
    public boolean processResponse(DocumentSource document){

        // check for protocol error
        if (document == null){
            throw new HarvestingException();
        }

        // the response is in place
        try {
            /* Try to create a list of records from the response. On failure,
               stop the work on the current prefix.
             */
            nodeList = (NodeList)provider.xpath.evaluate(
                    "//*[parent::*[local-name()='ListRecords']]",
                    document.getDocument(), XPathConstants.NODESET);
            logger.debug("found ["+nodeList.getLength()+"] records in the ListRecords response");
        } catch (XPathExpressionException e) {
            // something went wrong when creating the list, try another prefix
            logger.error(e.getMessage(), e);
            logger.info("Cannot create list of " + prefixes.get(pIndex) +
                    " records for endpoint " + provider.oaiUrl);
            return false;
        }

        return true;
    }

    /**
     * <br> Return the next metadata element in the list <br><br>
     *
     * This method returns the next metadata element from the list of nodes
     * created by the processResponse method. It applies XPath filtering to
     * the header and record elements.
     *
     * Note: the method will skip records the endpoint has flagged as 'deleted'
     * 
     * @return null if an error occurred, otherwise the next record in the list
     */
    @Override
    public Object parseResponse() {
        
        // check for protocol errors
        if (nodeList == null){
            throw new HarvestingException();
        }
        if (nIndex >= nodeList.getLength()) {
            throw new HarvestingException();
        }

        /* The list of nodes is in place, and the index points to an element
           in the list. Turn the next node into a document.
         */
        logger.debug("process ["+nIndex+"/"+nodeList.getLength()+"] record from the ListRecords response");
        Node node = nodeList.item(nIndex).cloneNode(true);
        nIndex++;
        Document doc = provider.db.newDocument();
        Node copy = doc.importNode(node, true);
        doc.appendChild(copy);

        // evaluate the document, find the identifier
        Node idNode;
        try {
            idNode = (Node) provider.xpath.evaluate("//*[starts-with(local-name(),"
                    + "'identifier') and parent::*[local-name()='header'"
                    + "and not(@status='deleted')]]/text()",
                    doc, XPathConstants.NODE);
        } catch (XPathExpressionException e) {
            // something went wrong parsing, try another record
            logger.error(e.getMessage(), e);
            logger.info("error parsing header, skipping record");
            return null;
        }

        if (idNode == null) {
            /* The OAI header does not contain an identifier or the record has
               been marked as deleted. In any case: skip it.
            */
            return null;
        }

        // evaluate the document, find the Metadata record
        Node dataNode;
        try {
            dataNode = (Node) provider.xpath.evaluate("//*[local-name()="
                    + "'metadata'"
                    + "and parent::*[local-name()='record']]/*[1]",
                    doc, XPathConstants.NODE);
        } catch (XPathExpressionException e) {
            // something went wrong parsing, try another record
            logger.error(e.getMessage(), e);
            logger.info("cannot find Metadata, skipping record");
            return null;
        }

        if (dataNode == null) {
            // the node does not contain metadata
            return null;
        }
        
        // create a document to store the metadata in
        dataNode = dataNode.cloneNode(true);
        doc = provider.db.newDocument();
        copy = doc.importNode(dataNode, true);
        doc.appendChild(copy);

        String id = idNode.getTextContent();
        String prefix = prefixes.get(pIndex);
        
        // check if the record has already been released by trying to add it to
        IdPrefix idPrefix = new IdPrefix (id, prefix);
        if (targets.checkAndInsertSorted(idPrefix)){

            /* Inserted the metadata in the targets table. Release the metadata
               to the client by submitting the details to the metadata factory.
             */
            return metadataFactory.create(id, prefix, new DocumentSource(id,doc), provider, false, false);
        } else {
            // not inserted, the record has already been released to the client
            return null;
        }
    }

    /**response
     * <br> Check if the list is fully parsed <br><br>
     *
     * This method checks if, as a consequence of repeatedly invoking
     * processResponse the end of the list nodes created by parseResponse
     * has been reached.
     *
     * Note: since the parsing does not apply to the targets, but to the nodes
     * in the list, override the AbstractListHarvesting fullyParsed method.
     *
     * @return  true if there are more, false otherwise
     */
    @Override
    public boolean fullyParsed() {

        // check for protocol error
        if (nodeList == null){
            throw new HarvestingException();
        }

        return nIndex == nodeList.getLength();
    }
}