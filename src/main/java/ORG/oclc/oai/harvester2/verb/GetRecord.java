
/**
 Copyright 2006 OCLC, Online Computer Library Center
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at
 
 http://www.apache.org/licenses/LICENSE-2.0
 
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 * 
 * 20151104 - maw - added connection timeout
 */

package ORG.oclc.oai.harvester2.verb;

import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;

import net.sf.saxon.s9api.SaxonApiException;
import org.xml.sax.SAXException;

/**
 * This class represents an GetRecord response on either the server or
 * on the client
 *
 * @author Jeffrey A. Young, OCLC Online Computer Library Center
 */
public class GetRecord extends HarvesterVerb {
    /**
     * Mock object constructor (for unit testing purposes)
     */
    public GetRecord() {
        super();
    }
    
    /**
     * Client-side GetRecord verb constructor
     *
     * @param baseURL the baseURL of the server to be queried
     * @exception MalformedURLException the baseURL is bad
     * @exception SAXException the xml response is bad
     * @exception IOException an I/O error occurred
     */
    public GetRecord(String baseURL, String identifier, String metadataPrefix)
    throws IOException, ParserConfigurationException, SAXException,
    TransformerException {
        this(baseURL, identifier, metadataPrefix, 0);
    }

    public GetRecord(String baseURL, String identifier, String metadataPrefix, int timeout)
    throws IOException, ParserConfigurationException, SAXException,
    TransformerException {
        super(getRequestURL(baseURL, identifier, metadataPrefix), timeout);
    }
    
    /**
     * Get the oai:identifier from the oai:header
     * @return the oai:identifier as a String
     * @throws TransformerException
     * @throws NoSuchFieldException
     */
    public String getIdentifier()
        throws TransformerException, NoSuchFieldException, ParserConfigurationException, SAXException, IOException,
        XMLStreamException, SaxonApiException {
        if (SCHEMA_LOCATION_V2_0.equals(getSchemaLocation()) || SCHEMA_LOCATION_V2_0_HTTPS.equals(getSchemaLocation())) {
            return getSingleString("/oai20:OAI-PMH/oai20:GetRecord/oai20:record/oai20:header/oai20:identifier");
        } else if (SCHEMA_LOCATION_V1_1_GET_RECORD.equals(getSchemaLocation())) {
            return getSingleString("/oai11_GetRecord:GetRecord/oai11_GetRecord:record/oai11_GetRecord:header/oai11_GetRecord:identifier");
        } else {
            throw new NoSuchFieldException(getSchemaLocation());
        }
    }
    
    /**
     * Construct the query portion of the http request
     *
     * @return a String containing the query portion of the http request
     */
    private static String getRequestURL(String baseURL, String identifier, String metadataPrefix) {
        StringBuffer requestURL =  new StringBuffer(baseURL);
        requestURL.append("?verb=GetRecord");
        requestURL.append("&identifier=").append(identifier);
        requestURL.append("&metadataPrefix=").append(metadataPrefix);
        return requestURL.toString();
    }
}
