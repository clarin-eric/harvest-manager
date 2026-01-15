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
 * <br> List based identifier harvesting <br><br>
 *
 * This class provides list based harvesting with a concrete verb to base
 * requests on. Because supplies a specific verb, ListIdentifiers, the response
 * processing needed is specific also. Hence the class also implements this
 * processing. <br><br>
 *
 * Since an endpoint might provide a metadata element in different sets, and
 * record harvesting might involve more than one set, a metadata record could
 * be presented to the client more than once. This class provides every record
 * only once. It uses the list provided by the superclass to remove duplicate
 * identifier and prefix pairs.
 *
 *  Note: originally, this class was declared 'final'. With the addition of
 * tests based on Mockito, this qualifier was removed.
 *
 * @author Kees Jan van de Looij (Max Planck Institute for Psycholinguistics)
 */
public class IdentifierListHarvesting extends ListHarvesting
        implements Harvesting {
    
    private static final Logger logger = LogManager.getLogger(
            IdentifierListHarvesting.class);
    
    /**
     * Associate endpoint and prefixes with the protocol
     * 
     * @param oaiFactory the OAI factory
     * @param provider the endpoint to address in the request
     * @param prefixes the prefixes returned by the endpoint
     * @param metadataFactory the metadata factory
     */
    public IdentifierListHarvesting(OAIFactory oaiFactory,
                                    Provider provider, List<String> prefixes,
                                    MetadataFactory metadataFactory, Endpoint endpoint){

        super(oaiFactory, provider, prefixes, metadataFactory, endpoint);
        // supply the superclass with messages specific to requesting identifiers
        message [0] = "Requesting more identifiers of records with prefix ";
        message [1] = "Requesting identifiers of records with prefix ";
        message [2] = "Cannot get identifiers of ";
    }
    
    /**
     * <br> Create a request based on the two parameter ListIdentifiers verb <br><br>
     *
     * This implementation supplies the form of the verb used in a request
     * based on a resumption token. <br><br>
     *
     * @param p1 metadata prefix
     * @param p2 resumption token
     * @return the response to the request
     */
    @Override
    public DocumentSource verb2(String p1, String p2, int timeout) throws
        IOException,
        ParserConfigurationException,
        SAXException,
        TransformerException,
        NoSuchFieldException,
        XMLStreamException, SaxonApiException {
        return oaiFactory.createListIdentifiers(p1, p2, timeout);
    }

    /**
     * <br> Create a request based on the two parameter ListIdentifiers verb <br><br>
     *
     * This implementation supplies the form of the verb used in the initial
     * request. <br><br>
     *
     * @param p1 endpoint URL
     * @param p2 from date, for selective harvesting
     * @param p3 until date, for selective harvesting
     * @param p4 metadata prefix
     * @param p5 set
     */
    @Override
    public DocumentSource verb5(String p1, String p2, String p3, String p4,
            String p5, int timeout, Path temp) throws
        IOException,
        ParserConfigurationException,
        SAXException,
        TransformerException,
        NoSuchFieldException,
        XMLStreamException, SaxonApiException {
        return oaiFactory.createListIdentifiers(p1, p2, p3, p4, p5, timeout);
    }
    
    /**
     * <br> Get the resumption token associated with a specific response <br><br>
     *
     * This method implements a resumption token request by invoking the
     * getResumptionToken OCLC library method. <br><br>
     *
     * @return the token
     */
    @Override
    public String getToken (){

        // check for a protocol error
        if (document == null){
            throw new HarvestingException();
        }

        return oaiFactory.getResumptionToken();
    }

    /**
     * <br> Create a list of metadata elements from the response <br><br>
     *
     * This method filters identifiers from the response. The filter is
     * an XPath expression build around the ListIdentifiers element, the
     * element that holds metadata record headers. The identifiers end up
     * in a target list as input to the processResponse method.
     *
     * Note: when listing records without first retrieving their identifiers,
     * the target list keeps track of duplicate records only. In that case, the
     * parseResponse method returns the metadata from a response directly.
     *
     * @return true if the response was processed successfully, false otherwise
     */
    @Override
    public boolean processResponse(DocumentSource document) {
        
        // check for a protocol error
        if (document == null){
            throw new HarvestingException();
        }

        /* The response is in place, and pIndex <= prefixes.size because of
           the invariant established in the AbstractListHarvesting class.
         */
        try {
            /* Try to add the targets in the response to the list. On 
               failure, stop the work on the current prefix.
             */
            nodeList = (NodeList)provider.xpath.evaluate(
                    "//*[starts-with(local-name(),'identifier') "
                            + "and parent::*[local-name()='header' "
                            + "and not(@status='deleted')]]/text()",
                    document.getDocument(), XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            // something went wrong when creating the list, try another prefix
            logger.error(e.getMessage(), e);
            logger.info("Cannot create list of identifiers of " +
                    prefixes.get(pIndex) +
                    " records for endpoint " + provider.oaiUrl);
            return false;
        }
        
        // add the identifier and prefix targets into the array
        for (int j = 0; j < nodeList.getLength(); j++) {
            String identifier = nodeList.item(j).getNodeValue();
            IdPrefix pair = new IdPrefix (identifier, 
                    prefixes.get(pIndex));

            /* Try to insert the pair in the list. No problem if it is already
               there.
             */
            targets.checkAndInsertSorted(pair);
        }
        
        return true;
    }

    /**
     * Return the next metadata element in the list of targets
     *
     * This method returns the next metadata element from the list of targets
     * created by the processResponse method.
     *
     * @return true if the list was parsed successfully, false otherwise
     */
    @Override
    public Object parseResponse() {
        
        // check for protocol errors
        if (targets == null){
            throw new HarvestingException();
        }
        if (tIndex >= targets.size()) {
            throw new HarvestingException();
        }

        // the targets are in place and tIndex points to an element in the list
        IdPrefix pair = targets.get(tIndex);
        tIndex++;
        // get the record for the identifier and prefix
        RecordHarvesting p = new RecordHarvesting(oaiFactory, provider,
                pair.prefix, pair.identifier, metadataFactory);

        if (! p.request()) {
            // something went wrong
            return null;
        } else {
            DocumentSource document = p.getResponse();
            if (document == null) {
                return null;
            } else {
                if (!p.processResponse(document)) {
                    return null;
                } else {
                    return p.parseResponse();
                }
            }
        }
    }
}
