
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

import net.sf.saxon.s9api.SaxonApiException;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.net.MalformedURLException;

/**
 * This class represents an Identify response on either the server or
 * on the client
 *
 * @author Jeffrey A. Young, OCLC Online Computer Library Center
 */
public class Identify extends HarvesterVerb {
    /**
     * Mock object constructor (for unit testing purposes)
     */
    public Identify() {
        super();
    }
    
    /**
     * Client-side Identify verb constructor
     *
     * @param baseURL the baseURL of the server to be queried
     * @exception MalformedURLException the baseURL is bad
     * @exception IOException an I/O error occurred
     */
    public Identify(String baseURL)
    throws IOException, ParserConfigurationException, SAXException,
    TransformerException {
        this(baseURL, 0);
    }

    public Identify(String baseURL, int timeout)
    throws IOException, ParserConfigurationException, SAXException,
    TransformerException {
        super(getRequestURL(baseURL), timeout);
    }
    
    /**
     * Get the oai:protocolVersion value from the Identify response
     * 
     * @return the oai:protocolVersion value
     * @throws TransformerException
     * @throws NoSuchFieldException
     */
    public String getProtocolVersion()
        throws TransformerException, NoSuchFieldException, ParserConfigurationException, SAXException, IOException,
        XMLStreamException, SaxonApiException {
        if (SCHEMA_LOCATION_V2_0.equals(getSchemaLocation())|| SCHEMA_LOCATION_V2_0_HTTPS.equals(getSchemaLocation())) {
            return getSingleString("/oai20:OAI-PMH/oai20:Identify/oai20:protocolVersion");
        } else if (SCHEMA_LOCATION_V1_1_IDENTIFY.equals(getSchemaLocation())) {
            return getSingleString("/oai11_Identify:Identify/oai11_Identify:protocolVersion");
        } else {
            throw new NoSuchFieldException(getSchemaLocation());
        }
    }
    
    /**
     * generate the Identify request URL for the specified baseURL
     * @param baseURL
     * @return the requestURL
     */
    private static String getRequestURL(String baseURL) {
        StringBuffer requestURL =  new StringBuffer();
        if(baseURL !=  null) {
            requestURL.append(baseURL);
            requestURL.append("?verb=Identify");
        }
        return requestURL.toString();
    }
}
