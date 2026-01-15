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

package nl.mpi.oai.harvester;

import ORG.oclc.oai.harvester2.verb.GetRecord;
import ORG.oclc.oai.harvester2.verb.Identify;
import ORG.oclc.oai.harvester2.verb.ListIdentifiers;
import ORG.oclc.oai.harvester2.verb.ListMetadataFormats;
import net.sf.saxon.s9api.SaxonApiException;
import nl.mpi.oai.harvester.action.ActionSequence;
import nl.mpi.oai.harvester.control.Util;
import nl.mpi.oai.harvester.harvesting.Harvesting;
import nl.mpi.oai.harvester.metadata.Metadata;
import nl.mpi.oai.harvester.metadata.MetadataFormat;
import nl.mpi.oai.harvester.metadata.NSContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import nl.mpi.oai.harvester.metadata.Record;

/**
 * This class represents a single OAI-PMH provider.
 *
 * @author Lari Lampen (MPI-PL)
 */
public class Provider {
    private static final Logger logger = LogManager.getLogger(Provider.class);

    /** Name of the provider. */
    public String name;
    
    /** Scenario used for this provider. */
    public String scenario;

    /** Incremental used for this provider. */
    public boolean incremental;

    /** Address through which the OAI repository is accessed. */
    public final String oaiUrl;

    /** List of OAI sets to harvest (optional). */
    public String[] sets = null;

    /** Maximum number of retries to use when a connection fails. */
    public int maxRetryCount = 0;

    /** Maximum number of retries to use when a connection fails. */
    public int[] retryDelays = {0};

    /** Maximum timeout for a connection */
    public int timeout = 0;

    /** DElay between requests */
    public int niceDelay = 0;

    /** Do I need some time on my own? */
    public boolean exclusive = false;
    
    /** Type of prefix harvesting that applies to the provider */
    public Harvesting prefixHarvesting;

    /** Type of list harvesting that applies to the provider */
    public Harvesting listHarvesting;

    /**
     * We make so many XPath queries we could just as well keep one XPath
     * object to hand for them.
     */
    public final XPath xpath;
    
    // document builder factory
    public final DocumentBuilder db;
    
    public Path temp;

	/**
	 * Provider deletion mode
	 */
	public DeletionMode deletionMode;

    /**
     * Provider constructor
     * <br><br>
     * 
     * Note the constructor might throw the ParserConfigurationException. This 
     * checked exception occurs when the factory class cannot create a document
     * builder. This condition can arise when the factory cannot find the 
     * necessary class, does not have access to it, or can for some reason 
     * not instantiate the builder.
     *
     * @param url OAI-PMH URL (endpoint) of the provider
     * @param maxRetryCount maximum number of retries
     * @param retryDelays how long to wait between tries
     * @throws ParserConfigurationException configuration problem
     */
    public Provider(String url, int maxRetryCount, int[] retryDelays)
            throws ParserConfigurationException {

	// If the base URL is given with parameters (most often
	// ?verb=Identify), strip them off to get a uniform
	// representation.
	if (url != null && url.contains("?"))
	    url = url.substring(0, url.indexOf("?"));
	this.oaiUrl = url;

	this.maxRetryCount = maxRetryCount;
        
        this.retryDelays = retryDelays;

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        // note: the dbf might throw the checked ParserConfigurationException
        db = dbf.newDocumentBuilder();

	XPathFactory xpf = XPathFactory.newInstance();
	xpath = xpf.newXPath();
	NSContext nsContext = new NSContext();
	nsContext.add("oai", "http://www.openarchives.org/OAI/2.0/");
	nsContext.add("os", "http://www.openarchives.org/OAI/2.0/static-repository");
	xpath.setNamespaceContext(nsContext);
        
        try {
            temp = Files.createTempFile("oai-",null);
        } catch (IOException ex) {
            temp = null;
        }

    }

    /**
     * Prepare this object for use.
     */
    public void init() {
        this.init(null);
//		if (name == null) fetchName();
//		if(deletionMode == null) fetchDeletionMode();
    }
    /**
     * Prepare this object for use.
     */
    public void init(String protocolName) {
		if (name == null) fetchName();
        if (!Objects.equals(protocolName, "nde")) {
            if(deletionMode == null) fetchDeletionMode();
        }
    }

    public void close() {
	if (temp != null) {
	    try {
                Files.deleteIfExists(temp);
            } catch (IOException ex) {
            }
        }
    }

    /**
     * Query the provider for its name and store it in this object.
     */
    void fetchName() {
	name = getProviderName();

	// If we simply can't find a name, make one up.
	if (name == null || name.isEmpty()) {
	    String domain = (oaiUrl!=null?oaiUrl.replaceAll(".*//([^/]+)/.*", "$1"):"LOCALHOST");
	    name = "Unnamed provider at " + domain;
	}
    }

    void fetchDeletionMode(){
        deletionMode = getProviderDeletionMode();
    }

    public DeletionMode getDeletionMode() {
        return deletionMode;
    }

    public void setDeletionMode(DeletionMode deletionMode) {
        this.deletionMode = deletionMode;
    }

    /**
     * Set the name of this provider
     *
     * @param name name of provider
     */
    public void setName(String name) {
	this.name = name;
    }

    public void setSets(String[] sets) {
	this.sets = sets;
    }

    /** 
     * Get name with all characters intact. 
     * @return name
     */
    public String getName() {
        if (name==null)
            fetchName();
	return name;
    }

    public String getOaiUrl() {
	return oaiUrl;
    }

    public boolean hasSets() {
        return (sets!=null && 0<=sets.length);
    }
    
    public String[] getSets() {
        return sets;
    }

    /**
     * Get the name declared by an OAI-PMH provider by making an
     * Identify request. Returns null if no name can be found.
     * @return provider name
     */
    public String getProviderName() {
        try {
            Identify ident = new Identify(oaiUrl, timeout);
            return parseProviderName(ident.getDocument());
        } catch (IOException | ParserConfigurationException | SAXException
                    | TransformerException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    public DeletionMode getProviderDeletionMode() {
        try {
            Identify ident = new Identify(oaiUrl, timeout);
            return parseDeletionMode(ident.getDocument());
        } catch (IOException | ParserConfigurationException | SAXException
                | TransformerException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Parse provider's name from an Identify response.
     *
     * @param response DOM tree representing an Identify response.
     * @return name, or null if one cannot be ascertained
     */
    public String parseProviderName(Document response) {
	try {
	    NodeList name = (NodeList)xpath.evaluate("//*[local-name() = 'repositoryName']/text()",
		    response, XPathConstants.NODESET);
	    if (name != null && name.getLength() > 0) {
		String provName = name.item(0).getNodeValue();
		logger.info("Contacted " + oaiUrl + " to get its name, received: \"" + provName + "\"");
		return provName;
	    }
	} catch (XPathExpressionException e) {
	    logger.error(e.getMessage(), e);
	}
	return null;
    }

    public DeletionMode parseDeletionMode(Document response) {
        try {
            NodeList name = (NodeList) xpath.evaluate("//*[local-name() = 'deletedRecord']/text()",
                            response, XPathConstants.NODESET);
            if (name != null && name.getLength() > 0) {
                String deletionMode = name.item(0).getNodeValue();
                logger.info("Contacted " + oaiUrl + " to get its deletionMode, received: \"" + deletionMode + "\"");
                return DeletionMode.valueOf(deletionMode.toUpperCase());
            }
        } catch (XPathExpressionException e) {
            logger.error(e.getMessage(), e);
        }
        return DeletionMode.NO;
    }

    public void setScenario(String scenario) {
        this.scenario = scenario;
    }
    
    public String getScenario() {
        return this.scenario;
    }

    public void setIncremental(boolean incremental) {
        this.incremental = incremental;
    }
    
    public boolean getIncremental() {
        return this.incremental;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getTimeout() {
        return this.timeout;
    }

    public void setNiceDelay(int delay) {
        this.niceDelay = delay;
    }

    public int getNiceDelay() {
        return this.niceDelay;
    }

    public void setMaxRetryCount(int maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }
    
    public int getMaxRetryCount() {
        return this.maxRetryCount;
    }

    public void setRetryDelays(int[] retryDelays) {
        this.retryDelays = retryDelays;
    }
    
    public int[] getRetryDelays() {
        return this.retryDelays;
    }

    public int getRetryDelay(int retry) {
        retry = (this.retryDelays.length<=retry?this.retryDelays.length-1:retry);
        return this.retryDelays[retry];
    }

    public void setExclusive(boolean exclusive) {
        this.exclusive = exclusive;
    }

    public boolean isExclusive() {
        return this.exclusive;
    }

    /**
     * Attempt to perform the specified sequence of actions on metadata from
     * this provider (which, of course, is only possible if this provider
     * supports the specified input format(s)).
     *
     * If the sequence can be performed, this method will start a new thread
     * to do so and return true. Otherwise no action will be taken and false
     * will be returned.
     *
     * @param ap the sequence of actions
     * @return success or failure
     */
    public boolean performActions(ActionSequence ap) throws XMLStreamException {
	List<String> prefixes = getPrefixes(ap.getInputFormat());
	if (prefixes.isEmpty()) {
	    logger.info("No matching prefixes for format "
		    + ap.getInputFormat());
	    return false;
	}

	// Fetch lr of record identifiers separately for each metadata
	// prefix corresponding to the metadata format, then collate all in
	// a single hash (id as key, metadata prefix as value).
	Map<String, String> identifiers = new HashMap<>();
	for (String prefix : prefixes) {
	    List<String> ids;
	    try {
		ids = getIdentifiers(prefix);
	    } catch (ParserConfigurationException | IOException | SAXException | TransformerException | XPathExpressionException | NoSuchFieldException | SaxonApiException ex) {
		logger.error("Error fetching ids from " + name, ex);
		return false;
	    }
	    for (String id : ids) {
		identifiers.put(id, prefix);
	    }
	}

	for (Map.Entry<String, String> me : identifiers.entrySet()) {
	    Metadata rec = getRecord(me.getKey(), me.getValue());
	    if (rec != null) {
		logger.info("Fetched record " + me.getKey()
			+ " (format " + me.getValue() + ")");
		ap.runActions(rec);
	    }
	}

        return true;
    }

    /**
     * Fetch a single record from this provider.
     *
     * @param id OAI-PMH identifier of the record
     * @param mdPrefix metadata prefix
     * @return the record, or null if it cannot be fetched
     */
	public Metadata getRecord(String id, String mdPrefix) {
	for (int i=0; i<maxRetryCount; i++) {
	    try {
		GetRecord gr = new GetRecord(oaiUrl, id, mdPrefix, timeout);
                return new Metadata(id, mdPrefix, gr.getDocumentSource(), this, true, false);
	    } catch (IOException | SAXException | ParserConfigurationException
		    | TransformerException e) {
                logger.error("Provider["+this+"] getRecord["+oaiUrl+"]["+id+"]["+mdPrefix+"] try["+(i+1)+"/"+maxRetryCount+"] failed!");
		logger.error(e);
	    }
            // retry the request once more
            int retryDelay = getRetryDelay(i);
            if (retryDelay > 0) {
                try {
                    Thread.sleep(retryDelay*1000);
                } catch(InterruptedException e) {
                    logger.error(e.getMessage(), e);
                }
            }                
	}
	return null;
    }

    /**
     * Make an OAI-PMH GetIdentifiers call to collect all identifiers available
     * with the given metadata prefix from this provider. In case a list of
     * sets is defined for this provider, a separate call will be made for
     * each set.
     *
     * @param mdPrefix metadata prefix
     * @return list of identifiers, which may be empty
     * @throws IOException IO problem
     * @throws ParserConfigurationException configuration problem
     * @throws SAXException XML problem
     * @throws TransformerException XSL problem
     * @throws XPathExpressionException XPath problem
     * @throws NoSuchFieldException introspection problem
     */
	public List<String> getIdentifiers(String mdPrefix) throws IOException,
        ParserConfigurationException, SAXException, TransformerException,
        XPathExpressionException, NoSuchFieldException, XMLStreamException, SaxonApiException {
	List<String> ids = new ArrayList<>();

	if (sets == null) {
	    addIdentifiers(mdPrefix, null, ids);
	} else {
	    for (String set : sets) {
		addIdentifiers(mdPrefix, set, ids);
	    }
	}

	return ids;
    }

    /**
     * Make an OAI-PMH GetIdentifiers call to collect all identifiers available
     * with the given Metadata prefix and set from this provider and add them
     * to the given list.
     *
     * @param mdPrefix Metadata prefix
     * @param set OAI-PMH set, or null for none
     * @param ids existing list to which identifiers will be added
     * @throws IOException IO problem
     * @throws ParserConfigurationException configuration problem
     * @throws SAXException XML problem
     * @throws TransformerException XSL problem
     * @throws XPathExpressionException XPath problem
     * @throws NoSuchFieldException introspection problem
     */
    public void addIdentifiers(String mdPrefix, String set, List<String> ids)
        throws IOException, ParserConfigurationException, SAXException,
        TransformerException, XPathExpressionException,
        NoSuchFieldException, XMLStreamException, SaxonApiException {
            ListIdentifiers li = new ListIdentifiers(oaiUrl, null, null, set, mdPrefix, timeout);
            for (;;) {
                addIdentifiers(li.getDocument(), ids);
                String resumption = li.getResumptionToken();
                if (resumption == null || resumption.isEmpty()) {
                    break;
	    }
	    li = new ListIdentifiers(oaiUrl, resumption, timeout);
	}
    }

    /**
     * Parse list of identifiers from an OAI provider's GetIdentifiers response
     * and add them to the given list.
     *
     * @param doc DOM tree representing OAI-PMH response
     * @param ids a list, already created, that identifiers will be added to
     * @throws XPathExpressionException XPath problem
     */
    public void addIdentifiers(Document doc, List<String> ids) throws
	    XPathExpressionException {
	NodeList nl = (NodeList)xpath.evaluate("//*[starts-with(local-name(),'identifier') and parent::*[local-name()='header' and not(@status='deleted')]]/text()",
		doc, XPathConstants.NODESET);
	if (nl == null)
	    return;

	for (int j = 0; j < nl.getLength(); j++) {
	    String currId = nl.item(j).getNodeValue();
	    ids.add(currId);
	}
    }

    /**
     * Get the list of Metadata prefixes corresponding to the specified format
     * that are supported by this provider.
     * @param format format
     * @return list of metadata prefixes
     */
    public List<String> getPrefixes(MetadataFormat format) {
	logger.debug("Checking format " + format);
	try {
	    ListMetadataFormats lmf = new ListMetadataFormats(oaiUrl, timeout);
	    return parsePrefixes(lmf.getDocument(), format);
	} catch (TransformerException | XPathExpressionException
		| ParserConfigurationException | SAXException | IOException e) {
	    logger.error(e.getMessage(), e);
	}
	return Collections.emptyList();
    }

    /**
     * Parse list of Metadata formats and find prefixes matching the given
     * format specification
     *
     * @param doc DOM tree of OAI provider's response
     * @param format desired Metadata format
     * @return list of prefixes
     * @throws XPathExpressionException XPath problem
     */
	public List<String> parsePrefixes(Document doc, MetadataFormat format)
	    throws XPathExpressionException {
	List<String> prefs = new ArrayList<>();

	NodeList formats = (NodeList)xpath.evaluate("//*[local-name() = 'metadataFormat']",
		doc, XPathConstants.NODESET);

	if (formats == null) {
	    logger.warn("Tne ListMetadataFormats response of this provider ("
		    + this + ") looks empty");
	    return Collections.emptyList();
	}

	for (int i=0; i<formats.getLength(); i++) {
	    Node s = formats.item(i);
	    String prefix = Util.getNodeText(xpath, "./*[local-name() = 'metadataPrefix']/text()", s);
	    String schema = Util.getNodeText(xpath, "./*[local-name() = 'schema']/text()", s);
	    String ns = Util.getNodeText(xpath, "./*[local-name() = 'metadataNamespace']/text()", s);
	    String comp;
	    if ("prefix".equals(format.getType())) {
		comp = prefix;
	    } else if ("schema".equals(format.getType())) {
		comp = schema;
	    } else if ("namespace".equals(format.getType())) {
		comp = ns;
	    } else {
		logger.error("Unknown match type " + format.getType());
		return null;
	    }
	    if (format.getValue().equals(comp)) {
		logger.debug("Found suitable prefix: " + prefix);
		prefs.add(prefix);
	    }
	}
	return prefs;
    }

    /**
     * Check if name matches given string (whether in filesystem
     * format or not).
     * @param name name to match
     * @return match or not
     */
    public boolean matches(String name) {
	return this.name.equals(Util.toFileFormat(name));
    }

	@Override
    public String toString() {
	StringBuilder sb = new StringBuilder(name == null ? "provider" : name);
	if (sets != null) {
	    sb.append(" (only set(s):");
	    for (String s : sets) {
		sb.append(" ").append(s);
	    }
	    sb.append(")");
	}
	sb.append(" @ ").append(oaiUrl);
	return sb.toString();
    }

	public enum DeletionMode {
		NO, PERSISTENT, TRANSIENT
	}
}
