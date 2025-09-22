
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
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;

import net.sf.saxon.s9api.SaxonApiException;
import org.xml.sax.SAXException;

/**
 * This class represents an ListSets response on either the server or
 * on the client
 *
 * @author Jeffrey A. Young, OCLC Online Computer Library Center
 */
public class ListSets extends HarvesterVerb {
	/**
	 * Mock object constructor (for unit testing purposes)
	 */
	public ListSets() {
		super();
	}

	/**
	 * Client-side ListSets verb constructor
	 *
	 * @param baseURL the baseURL of the server to be queried
	 * @exception MalformedURLException the baseURL is bad
	 * @exception IOException an I/O error occurred
	 * @throws ParserConfigurationException 
	 * @throws SAXException 
	 * @throws TransformerException 
	 */
	public ListSets(String baseURL)
	throws IOException, ParserConfigurationException, SAXException,
	TransformerException {
		this(getRequestURL(baseURL), 0);
	}

	public ListSets(String baseURL, int timeout)
	throws IOException, ParserConfigurationException, SAXException,
	TransformerException {
		super(getRequestURL(baseURL), timeout);
	}

	/**
	 * @param baseURL
	 * @param resumptionToken
	 * @throws IOException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws TransformerException
	 */
	public ListSets(String baseURL, String resumptionToken)
	throws IOException, ParserConfigurationException, SAXException,
	TransformerException {
		this(baseURL, resumptionToken, 0);
	}

	public ListSets(String baseURL, String resumptionToken, int timeout)
	throws IOException, ParserConfigurationException, SAXException,
	TransformerException {
		super(getRequestURL(baseURL, resumptionToken), timeout);
	}

	/**
	 * Get the oai:resumptionToken from the response
	 * 
	 * @return the oai:resumptionToken as a String
	 * @throws TransformerException
	 * @throws NoSuchFieldException
	 */
	public String getResumptionToken()
		throws TransformerException, NoSuchFieldException, ParserConfigurationException, SAXException, IOException,
		XMLStreamException, SaxonApiException {
		if (SCHEMA_LOCATION_V2_0.equals(getSchemaLocation())||SCHEMA_LOCATION_V2_0_HTTPS.equals(getSchemaLocation())) {
			return getSingleString("/oai20:OAI-PMH/oai20:ListSets/oai20:resumptionToken");
		} else if (SCHEMA_LOCATION_V1_1_LIST_SETS.equals(getSchemaLocation())) {
			return getSingleString("/oai11_ListSets:ListSets/oai11_ListSets:resumptionToken");
		} else {
			throw new NoSuchFieldException(getSchemaLocation());
		}
	}

	/**
	 * Generate a ListSets request for the given baseURL and resumptionToken
	 * @param baseURL
	 * @param resumptionToken
	 * @return
	 * @throws UnsupportedEncodingException 
	 */
	private static String getRequestURL(String baseURL,
			String resumptionToken) throws UnsupportedEncodingException {
		StringBuffer requestURL =  new StringBuffer(baseURL);
		requestURL.append("?verb=ListSets");
		requestURL.append("&resumptionToken=").append(URLEncoder.encode(resumptionToken, "UTF-8"));
		return requestURL.toString();
	}
	
	/**
	 * Generate a ListSets request for the given baseURL
	 * 
	 * @param baseURL
	 * @return
	 */
	private static String getRequestURL(String baseURL) {
		StringBuffer requestURL =  new StringBuffer(baseURL);
		requestURL.append("?verb=ListSets");
		return requestURL.toString();
	}
}