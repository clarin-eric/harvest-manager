
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

import com.ctc.wstx.exc.WstxUnexpectedCharException;
import net.sf.saxon.s9api.SaxonApiException;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.evt.XMLEvent2;

/**
 * This class represents an ListRecords response on either the server or on the client
 *
 * @author Jeffrey A. Young, OCLC Online Computer Library Center
 */
public class ListRecords extends HarvesterVerb
{
	private static Logger logger = LogManager.getLogger( ListRecords.class );

	/**
	 * Mock object constructor (for unit testing purposes)
	 */
	public ListRecords()
	{
		super();
	}

	/**
	 * Client-side ListRecords verb constructor
	 *
	 * @param baseURL
	 *            the baseURL of the server to be queried
	 * @exception MalformedURLException
	 *                the baseURL is bad
	 * @exception SAXException
	 *                the xml response is bad
	 * @exception IOException
	 *                an I/O error occurred
	 */
	public ListRecords( String baseURL, String from, String until,
			String set, String metadataPrefix )
			throws IOException, ParserConfigurationException, SAXException,
			TransformerException
	{
		this( baseURL, from, until, set, metadataPrefix, 0 );
	}

	public ListRecords( String baseURL, String from, String until,
			String set, String metadataPrefix, int timeout )
			throws IOException, ParserConfigurationException, SAXException,
			TransformerException
	{
		super( getRequestURL( baseURL, from, until, set, metadataPrefix ), timeout );
	}

	public ListRecords( String baseURL, String from, String until,
			String set, String metadataPrefix, int timeout, Path temp )
			throws IOException, ParserConfigurationException, SAXException,
			TransformerException
	{
		super( getRequestURL( baseURL, from, until, set, metadataPrefix ), timeout, temp );
	}

	/**
	 * Client-side ListRecords verb constructor (resumptionToken version)
	 * 
	 * @param baseURL
	 * @param resumptionToken
	 * @throws IOException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws TransformerException
	 */
	public ListRecords( String baseURL, String resumptionToken )
			throws IOException, ParserConfigurationException, SAXException,
			TransformerException
	{
		this( baseURL, resumptionToken, 0 );
	}

	public ListRecords( String baseURL, String resumptionToken, int timeout )
			throws IOException, ParserConfigurationException, SAXException,
			TransformerException
	{
		super( getRequestURL( baseURL, resumptionToken ), timeout );
	}

	public ListRecords( String baseURL, String resumptionToken, int timeout, Path temp )
			throws IOException, ParserConfigurationException, SAXException,
			TransformerException
	{
		super( getRequestURL( baseURL, resumptionToken ), timeout, temp );
	}

	/**
	 * Get the oai:resumptionToken from the response
	 * 
	 * @return the oai:resumptionToken value
	 * @throws TransformerException
	 * @throws NoSuchFieldException
	 */
	public String getResumptionToken()
			throws TransformerException, NoSuchFieldException, ParserConfigurationException, SAXException, IOException,
			XMLStreamException, SaxonApiException
	{
		String schemaLocation = getSchemaLocation();
		if ( schemaLocation.indexOf( SCHEMA_LOCATION_V2_0 ) != -1 || schemaLocation.indexOf( SCHEMA_LOCATION_V2_0_HTTPS ) != -1 )
		{
			if ( hasDocument() )
				return getSingleString( "/oai20:OAI-PMH/oai20:ListRecords/oai20:resumptionToken" );
			String token = null;
			XMLInputFactory2 xmlif = (XMLInputFactory2) XMLInputFactory2.newInstance();
			xmlif.configureForConvenience();
			XMLStreamReader2 xmlr = (XMLStreamReader2) xmlif.createXMLStreamReader( getStream() );
			int state = 1; // 1:START 2:FOUND 0:STOP -1:ERROR
			while (state > 0)
			{
				int eventType = xmlr.getEventType();
				switch (state)
				{
				case 1:// START
					switch (eventType)
					{
					case XMLEvent2.START_ELEMENT:
						QName qn = xmlr.getName();
						// logger.debug("finding token in the XML stream:
						// node["+qn.getNamespaceURI()+"]["+qn.getLocalPart()+"]");
						if ( qn.getNamespaceURI().equals( "http://www.openarchives.org/OAI/2.0/" )
								&& qn.getLocalPart().equals( "resumptionToken" ) )
							state = 2;// FOUND
						break;
					}
					break;
				case 2:// FOUND
					switch (eventType)
					{
					case XMLEvent2.CHARACTERS:
						token = xmlr.getText();
						state = 0;// STOP
						break;
					default:
						state = -1;// ERROR
						break;
					}
					break;
				}

				if ( xmlr.hasNext() )
					try
					{
						xmlr.next();
					}
					catch (WstxUnexpectedCharException ex)
					{
						logger.info( String.format( "Invalid char found in XML, skipping the current one and look for next one: {%s}",
								xmlr.toString() ) );
					}
				else
					state = state == 1 ? 0 : -1;// if START then STOP else ERROR
			}
			if ( state < 0 || token == null )
			{
				logger.debug( "couldn't find token, done!" );
				return null;
			}
			logger.debug( "found token[" + token + "], resume!" );
			return token;
		}
		else if ( schemaLocation.indexOf( SCHEMA_LOCATION_V1_1_LIST_RECORDS ) != -1 )
		{
			return getSingleString( "/oai11_ListRecords:ListRecords/oai11_ListRecords:resumptionToken" );
		}
		else if ( schemaLocation.indexOf( SCHEMA_LOCATION_V2_0 ) != -1 | schemaLocation.indexOf( SCHEMA_LOCATION_V2_0_HTTPS ) != -1 )
		{
			return getSingleString( "/oai20_ListRecords:ListRecords/oai20_ListRecords:resumptionToken" );
		}
		else
		{
			throw new NoSuchFieldException( schemaLocation );
		}
	}

	/**
	 * Construct the query portion of the http request
	 *
	 * @return a String containing the query portion of the http request
	 */
	private static String getRequestURL(
			String baseURL,
			String from,
			String until,
			String set,
			String metadataPrefix )
	{
		StringBuffer requestURL = new StringBuffer( baseURL );
		requestURL.append( "?verb=ListRecords" );
		if ( from != null )
			requestURL.append( "&from=" ).append( from );
		if ( until != null )
			requestURL.append( "&until=" ).append( until );
		if ( set != null )
			requestURL.append( "&set=" ).append( set );
		requestURL.append( "&metadataPrefix=" ).append( metadataPrefix );
		return requestURL.toString();
	}

	/**
	 * Construct the query portion of the http request (resumptionToken version)
	 * 
	 * @param baseURL
	 * @param resumptionToken
	 * @return
	 */
	private static String getRequestURL(
			String baseURL,
			String resumptionToken )
	{
		StringBuffer requestURL = new StringBuffer( baseURL );
		requestURL.append( "?verb=ListRecords" );
		requestURL.append( "&resumptionToken=" ).append( URLEncoder.encode( resumptionToken ) );
		return requestURL.toString();
	}
}
