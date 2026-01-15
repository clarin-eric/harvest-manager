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

import java.io.*;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//import org.apache.xpath.XPathAPI;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLStreamException;
import nl.mpi.oai.harvester.utils.DocumentSource;
import nl.mpi.oai.harvester.utils.MarkableFileInputStream;
import nl.mpi.tla.util.Saxon;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmItem;

import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.evt.XMLEvent2;

/**
 * HarvesterVerb is the parent class for each of the OAI verbs.
 * 
 * @author Jefffrey A. Young, OCLC Online Computer Library Center
 */
public abstract class HarvesterVerb {
    private static Logger logger = LogManager.getLogger(HarvesterVerb.class);

    /* Primary OAI namespaces */
    public static final String SCHEMA_LOCATION_V2_0 = "http://www.openarchives.org/OAI/2.0/ http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd";
    public static final String SCHEMA_LOCATION_V2_0_HTTPS = "http://www.openarchives.org/OAI/2.0/ https://www.openarchives.org/OAI/2.0/OAI-PMH.xsd";
    public static final String SCHEMA_LOCATION_V1_1_GET_RECORD = "http://www.openarchives.org/OAI/1.1/OAI_GetRecord http://www.openarchives.org/OAI/1.1/OAI_GetRecord.xsd";
    public static final String SCHEMA_LOCATION_V1_1_IDENTIFY = "http://www.openarchives.org/OAI/1.1/OAI_Identify http://www.openarchives.org/OAI/1.1/OAI_Identify.xsd";
    public static final String SCHEMA_LOCATION_V1_1_LIST_IDENTIFIERS = "http://www.openarchives.org/OAI/1.1/OAI_ListIdentifiers http://www.openarchives.org/OAI/1.1/OAI_ListIdentifiers.xsd";
    public static final String SCHEMA_LOCATION_V1_1_LIST_METADATA_FORMATS = "http://www.openarchives.org/OAI/1.1/OAI_ListMetadataFormats http://www.openarchives.org/OAI/1.1/OAI_ListMetadataFormats.xsd";
    public static final String SCHEMA_LOCATION_V1_1_LIST_RECORDS = "http://www.openarchives.org/OAI/1.1/OAI_ListRecords http://www.openarchives.org/OAI/1.1/OAI_ListRecords.xsd";
    public static final String SCHEMA_LOCATION_V1_1_LIST_SETS = "http://www.openarchives.org/OAI/1.1/OAI_ListSets http://www.openarchives.org/OAI/1.1/OAI_ListSets.xsd";
    private InputStream str = null;
    private Document doc = null;
    private String schemaLocation = null;
    private String requestURL = null;
    private static HashMap builderMap = new HashMap();
    private static Element namespaceElement = null;
    private static Map<String,String> NAMESPACES = new LinkedHashMap<>();
    private static DocumentBuilderFactory factory = null;
    private static TransformerFactory xformFactory = TransformerFactory.newInstance();
    
    static {
    	try {
	        /* Load DOM Document */
	        factory = DocumentBuilderFactory
	        .newInstance();
	        factory.setNamespaceAware(true);
	        Thread t = Thread.currentThread();
	        DocumentBuilder builder = factory.newDocumentBuilder();
	        builderMap.put(t, builder);
	        
	        DOMImplementation impl = builder.getDOMImplementation();

            NAMESPACES.put("harvester", "http://www.oclc.org/research/software/oai/harvester");
            NAMESPACES.put("xsi","http://www.w3.org/2001/XMLSchema-instance");
            NAMESPACES.put("oai20","http://www.openarchives.org/OAI/2.0/");
            NAMESPACES.put("oai11_GetRecord","http://www.openarchives.org/OAI/1.1/OAI_GetRecord");
            NAMESPACES.put("oai11_Identify","http://www.openarchives.org/OAI/1.1/OAI_Identify");
            NAMESPACES.put("oai11_ListIdentifiers","http://www.openarchives.org/OAI/1.1/OAI_ListIdentifiers");
            NAMESPACES.put("oai11_ListMetadataFormats","http://www.openarchives.org/OAI/1.1/OAI_ListMetadataFormats");
            NAMESPACES.put("oai11_ListRecords","http://www.openarchives.org/OAI/1.1/OAI_ListRecords");
            NAMESPACES.put("oai11_ListSets","http://www.openarchives.org/OAI/1.1/OAI_ListSets");
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    }
    
    public boolean hasStream() {
        return (str!=null);
    }
    
    public boolean hasDocument() {
        return (doc!=null);
    }
    
    public DocumentSource getDocumentSource() {
        if (hasDocument())
            return new DocumentSource(requestURL,doc);
        return new DocumentSource(requestURL,getStream());
    }
    
    /**
     * Get the OAI response as a stream
     * 
     * @return the InputStream for the OAI response
     */
    public InputStream getStream() {
        if (hasStream()) {
            try {
                str.reset();
            } catch (IOException ex) {
                logger.warn("stream for '"+requestURL+"' could not be reset!");
            }
        }
        return str;
    }
    
    public InputSource getSource() {
        return new InputSource(getStream());
    }
    
    /**
     * Get the OAI response as a DOM object
     * 
     * @return the DOM for the OAI response
     */
    public Document getDocument() throws ParserConfigurationException, SAXException, IOException {
        if (doc == null) {
            Thread t = Thread.currentThread();
            DocumentBuilder builder = (DocumentBuilder) builderMap.get(t);
            if (builder == null) {
                builder = factory.newDocumentBuilder();
                builderMap.put(t, builder);
            }
            URL dtdFilePath = getClass().getResource("/xhtml1.dtd");
            assert dtdFilePath != null;
            String xmlText = IOUtils.toString(getStream(), StandardCharsets.UTF_8);
            logger.debug("dtdFilePath is: " + dtdFilePath);

            if (xmlText.startsWith("<html")) {
                try {
                    xmlText = "<!DOCTYPE html PUBLIC '-//W3C//DTD HTML//EN' '"  + dtdFilePath.toURI() + "'>" + xmlText;
                    logger.debug("after xmlText is set " + dtdFilePath.toURI());
                } catch (URISyntaxException e) {
                    logger.error(e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }

            doc = builder.parse(new InputSource(new StringReader(xmlText)));
//            doc = builder.parse(getSource());
            str = null;
            logger.debug("switched from stream to tree for request["+requestURL+"]",new Throwable());
        }
        return doc;
    }

//    private void addDocType(String rootTag, String docType, InputSource originalInputSource) {
//        originalInputSource.
//    }
    
    /**
     * Get the xsi:schemaLocation for the OAI response
     * 
     * @return the xsi:schemaLocation value
     */
    public String getSchemaLocation()
        throws TransformerException, ParserConfigurationException, SAXException, IOException, XMLStreamException,
        SaxonApiException {
        if (this.schemaLocation == null) {
            if (hasDocument()) {
                this.schemaLocation = getSingleString("/*/@xsi:schemaLocation");
                logger.debug("found schemaLocation["+schemaLocation+"] in the XML tree");
            } else {
                XMLInputFactory2 xmlif = (XMLInputFactory2) XMLInputFactory2.newInstance();
                xmlif.configureForConvenience();
                XMLStreamReader2 xmlr = (XMLStreamReader2) xmlif.createXMLStreamReader(getStream());
                int state = 1; // 1:START 0:STOP -1:ERROR
                while (state > 0) {
                    int eventType = xmlr.getEventType();
                    switch (eventType) {
                        case XMLEvent2.START_ELEMENT:
                            schemaLocation = xmlr.getAttributeValue("http://www.w3.org/2001/XMLSchema-instance","schemaLocation");
                            if (schemaLocation != null)
                                state = 0;
                            break;
                    }
                    if (xmlr.hasNext())
                        xmlr.next();
                    else
                        state = state == 1? 0: -1;// if START then STOP else ERROR
                }
                xmlr.close();
                logger.debug("found schemaLocation["+schemaLocation+"] in the XML stream");
            }

            // The URIs in xsi:schemaLocation are separated by (any kind
            // of) white space. Normalize it to a single space.
            this.schemaLocation = schemaLocation.trim().replaceAll("\\s+", " ");
        }
        return schemaLocation;
    }
    
    /**
     * Get the OAI errors
     * @return a NodeList of /oai:OAI-PMH/oai:error elements
     * @throws TransformerException
     */
    public List<Node> getErrors() throws TransformerException, ParserConfigurationException, SaxonApiException, SAXException, IOException, XMLStreamException {
        if (SCHEMA_LOCATION_V2_0.equals(getSchemaLocation())||SCHEMA_LOCATION_V2_0_HTTPS.equals(getSchemaLocation())) {
            return getNodeList("/oai20:OAI-PMH/oai20:error");
        } else {
            return null;
        }
    }
    
    /**
     * Get the OAI request URL for this response
     * @return the OAI request URL as a String
     */
    public String getRequestURL() {
        return requestURL;
    }
    
    /**
     * Mock object creator (for unit testing purposes)
     */
    public HarvesterVerb() {
    }
    
    /**
     * Performs the OAI request
     * 
     * @param requestURL
     * @throws IOException
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws TransformerException
     */
    public HarvesterVerb(String requestURL) throws IOException,
    ParserConfigurationException, SAXException, TransformerException {
        harvest(requestURL,0,null);
    }
    
    public HarvesterVerb(String requestURL,int timeout) throws IOException,
    ParserConfigurationException, SAXException, TransformerException {
        harvest(requestURL,timeout,null);
    }
    
    public HarvesterVerb(String requestURL,int timeout,Path temp) throws IOException,
    ParserConfigurationException, SAXException, TransformerException {
        harvest(requestURL,timeout,temp);
    }

    /**
     * Preforms the OAI request
     * 
     * @param requestURL
     * @param timeout
     * @param temp
     * @throws IOException
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws TransformerException
     */
    public void harvest(String requestURL, int timeout, Path temp) throws MalformedURLException, IOException {
        str = DocumentSource.fetch(requestURL,null,null,null,timeout,temp).getStream();
    }
    
    /**
     * Get the String value for the given XPath location in the response DOM
     * 
     * @param xpath
     * @return a String containing the value of the XPath location.
     * @throws TransformerException
     */
    public String getSingleString(String xpath)
        throws TransformerException, ParserConfigurationException, SAXException, IOException, SaxonApiException {
        return getSingleString(getDocument(), xpath);
//        return XPathAPI.eval(getDocument(), xpath, namespaceElement).str();
//      String str = null;
//      Node node = XPathAPI.selectSingleNode(getDocument(), xpath,
//      namespaceElement);
//      if (node != null) {
//      XObject xObject = XPathAPI.eval(node, "string()");
//      str = xObject.str();
//      }
//      return str;
    }
    
    public String getSingleString(Node node, String xpath)
    throws SaxonApiException {
        return Saxon.xpath2string(Saxon.wrapNode(node), xpath, null, NAMESPACES);
//        return XPathAPI.eval(node, xpath, namespaceElement).str();
    }
    
    /**
     * Get a NodeList containing the nodes in the response DOM for the specified
     * xpath
     * @param xpath
     * @return the NodeList for the xpath into the response DOM
     * @throws TransformerException
     */
    public List<Node> getNodeList(String xpath) throws SaxonApiException, ParserConfigurationException, SAXException, IOException {
//        return XPathAPI.selectNodeList(getDocument(), xpath, namespaceElement);

        List<Node> res = new ArrayList<>();
        for (XdmItem item : Saxon.xpathList(Saxon.wrapNode(getDocument()), xpath, null, NAMESPACES)) {
            res.add((Node) Saxon.wrapNode((Node) item));
        }

        return res;
    }
    
    public String toString() {
        try {
            // Element docEl = getDocument().getDocumentElement();
            // return docEl.toString();
            Source input = new DOMSource(getDocument());
            StringWriter sw = new StringWriter();
            Result output = new StreamResult(sw);
            try {
                Transformer idTransformer = xformFactory.newTransformer();
                idTransformer.setOutputProperty(
                        OutputKeys.OMIT_XML_DECLARATION, "yes");
                idTransformer.transform(input, output);
                return sw.toString();
            } catch (TransformerException e) {
                return e.getMessage();
            }
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            logger.error("document is invalid: " + ex);
        }
        return null;
    }
}
