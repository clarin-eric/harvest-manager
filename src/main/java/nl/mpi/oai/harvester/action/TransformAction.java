/*
 * Copyright (C) 2014, The Max Planck Institute for
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

package nl.mpi.oai.harvester.action;

import net.sf.saxon.s9api.DOMDestination;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XsltTransformer;
import nl.mpi.oai.harvester.metadata.Metadata;
import nl.mpi.oai.harvester.metadata.Record;
import nl.mpi.tla.util.Saxon;
import nl.mpi.tla.util.SaxonListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Semaphore;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.SourceLocator;
import javax.xml.xpath.XPathException;
import net.sf.saxon.lib.DirectResourceResolver;
import net.sf.saxon.s9api.MessageListener2;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.lib.ResourceResolver;

/**
 * This class represents the application of an XSL transformation to the
 * XML content of a metadata record.
 * 
 * @author Lari Lampen (MPI-PL)
 */
import net.sf.saxon.lib.ResourceRequest;
public class TransformAction implements Action {
    private static final Logger logger = LogManager.getLogger(TransformAction.class);
    
    /** The XSL executable. */
    private final XsltExecutable executable;

    /** The file containing the XSL transformation. */
    private String xsltFile;

    /** The directory containing cached resources. */
    private Path cacheDir;
    
    /** A standard semaphore is used to track the number of running transforms. */
    private Semaphore semaphore;
    
    /** The configuration */
    private Node config;

    /** 
     * Create a new transform action using the specified XSLT. 
     * 
     * @param xsltFile the XSL stylesheet
     * @param cacheDir the directory to cache results of resource requests
     * @param maxJobs the maximum number of concurrent transforms
     * @throws FileNotFoundException stylesheet couldn't be found
     * @throws TransformerConfigurationException there is a problem with the stylesheet
     * @throws java.net.MalformedURLException
     * @throws net.sf.saxon.s9api.SaxonApiException
     */
    public TransformAction(Node conf, String xsltFile,Path cacheDir,int maxJobs)
      throws FileNotFoundException, TransformerConfigurationException, MalformedURLException, SaxonApiException {
        this(conf, xsltFile,cacheDir,(maxJobs>0?new Semaphore(maxJobs):null));
    }
    
    /** 
     * Create a new transform action using the specified XSLT. 
     * 
     * @param xsltFile the XSL stylesheet
     * @param cacheDir the directory to cache results of resource requests
     * @param semaphore a semaphore to control the concurrent number of transforms
     * @throws FileNotFoundException stylesheet couldn't be found
     * @throws TransformerConfigurationException there is a problem with the stylesheet
     * @throws java.net.MalformedURLException
     * @throws net.sf.saxon.s9api.SaxonApiException
     */
    public TransformAction(Node conf, String xsltFile,Path cacheDir,Semaphore semaphore)
      throws FileNotFoundException, TransformerConfigurationException, MalformedURLException, SaxonApiException {
        this.config = conf;
	      this.xsltFile = xsltFile;
        this.cacheDir = cacheDir;
        this.semaphore = semaphore;
        Source xslSource = null;
        if (xsltFile.startsWith("http:") || xsltFile.startsWith("https:"))
            xslSource = new StreamSource(xsltFile);
        else
            xslSource = new StreamSource(new FileInputStream(xsltFile),xsltFile);

        executable = Saxon.buildTransformer(Saxon.buildDocument(xslSource));
        
    }

    @Override
    public boolean perform(List<Record> records) {
        for (Record rec : records) {
            Metadata record = (Metadata)rec;
            try {
                if (semaphore!=null) {
                    for (;;) {
                        try {
                            logger.debug("request transform action");
                            semaphore.acquire();
                            logger.debug("acquired transform action");
                            break;
                        } catch (InterruptedException e) { }
                    }
                }
                Source source = null;
                Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
                DOMDestination output = new DOMDestination(doc);
                if (record.hasStream()) {
                    source = new SAXSource(record.getSource());
                } else {
                    source = new DOMSource(record.getDoc());
                }
                XdmNode old = Saxon.buildDocument(source);
                XsltTransformer transformer = executable.load();
                
                TransformActionListener listener = new TransformActionListener();
                transformer.setErrorListener(listener);
                transformer.setMessageListener(listener);

                if (cacheDir != null) {
                    logger.debug("Setting the URLResolve to cache in "+cacheDir);
                    transformer.setResourceResolver(new TransformActionResourceResolver(transformer.getResourceResolver()));
                }
                
                transformer.setSource(old.asSource());
                transformer.setDestination(output);

                transformer.setParameter(new QName("config"), Saxon.wrapNode(this.config.getOwnerDocument()));
                transformer.setParameter(new QName("provider_name"), new XdmAtomicValue(record.getOrigin().getName()));
                transformer.setParameter(new QName("provider_uri"), new XdmAtomicValue(record.getOrigin().getOaiUrl()));
                transformer.setParameter(new QName("record_identifier"), new XdmAtomicValue(record.getId()));

                transformer.transform();
                record.setDoc(doc);
                logger.debug("transformed to XML doc with ["+XPathFactory.newInstance().newXPath().evaluate("count(//*)", record.getDoc())+"] nodes");
            } catch (XPathExpressionException | SaxonApiException | ParserConfigurationException ex) {
                logger.error("Transformation error: ",ex);
                return false;
            } finally {
                if (semaphore!=null) {
                    semaphore.release();
                    logger.debug("released transform action");
                }
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "transform using " + xsltFile;
    }

    // Transform actions differ if and only if the XSLT files differ.
    @Override
    public int hashCode() {
	      return xsltFile.hashCode();
    }
    @Override
    public boolean equals(Object o) {
	      if (o instanceof TransformAction) {
	          TransformAction t = (TransformAction)o;
        	  return xsltFile.equals(t.xsltFile);
	      }
	      return false;
    }

    @Override
    public Action clone() {
	      try {
	          // This is a deep copy. The new object has its own Transform object.
	          return new TransformAction(config, xsltFile,cacheDir,semaphore);
	      } catch (FileNotFoundException | TransformerConfigurationException | MalformedURLException | SaxonApiException ex) {
	          logger.error(ex);
	      }
	      return null;
    }
    
    class TransformActionResourceResolver implements ResourceResolver {
        private ResourceResolver resolver;
        
        public TransformActionResourceResolver(ResourceResolver resolver) {
            this.resolver = resolver;
        }

        public Source resolve (ResourceRequest req) throws net.sf.saxon.trans.XPathException {
            logger.debug("Transformer resolver: resolve("+req.relativeUri+","+req.baseUri+")");
            String base = req.baseUri;
            String uri = req.relativeUri;
            if (base != null && !base.equals("")) {
                try {
                    uri = (new URI(base)).resolve(uri).toString();
                } catch (URISyntaxException ex) {
                    logger.error("Transformer resolver: couldn't resolve("+uri+","+base+") continuing with just "+uri,ex);
                }
            }
            logger.debug("Transformer resolver: uri["+uri+"]");
            String cacheFile = "./" + uri.replaceAll("[^a-zA-Z0-9]", "_");
            logger.debug("Transformer resolver: check cache for ["+cacheDir.resolve(cacheFile).toFile().toString()+"]");
            Source res = null;
            if (Files.exists(cacheDir.resolve(cacheFile))) {
                res = new StreamSource(cacheDir.resolve(cacheFile).toFile());
                logger.debug("Transformer resolver: loaded ["+cacheDir.resolve(cacheFile).toFile().toString()+"] from cache");
            } else {
                try {
                    if (resolver == null) 
                        resolver = new DirectResourceResolver(Saxon.getProcessor().getUnderlyingConfiguration());
                    //ResourceRequest request = new ResourceRequest();
                    //request.relativeUri = uri;
                    //request.baseUri = base;
                    //request.uri = uri;
                    //request.nature = ResourceRequest.XML_NATURE;
                    //request.purpose = ResourceRequest.ANY_PURPOSE;
                    //res = request.resolve(resolver);
                    //res = resolver.resolve(request);
                    res = req.resolve(resolver);
                    Saxon.save(res, cacheDir.resolve(cacheFile).toFile());
                    logger.debug("Transformer resolver: stored ["+cacheFile+"] in cache ["+cacheDir.resolve(cacheFile).toFile().toString()+"]");
                } catch (Exception ex) {
                    throw new net.sf.saxon.trans.XPathException(ex);
                }
            }
            return res;        
        }
    }

    class TransformActionListener implements MessageListener2, ErrorListener {

        protected boolean handleMessage(String msg, String loc, Exception e) {
            if (msg.startsWith("INF: "))
                logger.info(msg.replace("INF: ", ""));
            else if (msg.startsWith("WRN: "))
                logger.warn("["+loc+"]: "+msg.replace("WRN: ", ""), e);
            else if (msg.startsWith("ERR: "))
                logger.error("["+loc+"]: "+msg.replace("ERR: ", ""), e);
            else if (msg.startsWith("DBG: "))
                logger.debug("["+loc+"]: "+msg.replace("DBG: ", ""), e);
            else
                return false;
            return true;
        }

        protected boolean handleException(TransformerException te) {
            return handleMessage(te.getMessage(), te.getLocationAsString(), te);
        }

        @Override
        public void warning(TransformerException te) throws TransformerException {
            if (!handleException(te))
                logger.warn(te.getMessageAndLocation(), te);
        }

        @Override
        public void error(TransformerException te) throws TransformerException {
            if (!handleException(te))
                logger.error(te.getMessageAndLocation(), te);
        }

        @Override
        public void fatalError(TransformerException te) throws TransformerException {
            if (!handleException(te))
                logger.error(te.getMessageAndLocation(), te);
        }

        protected String getLocation(SourceLocator sl) {
            if (sl.getColumnNumber()<0)
                return "-1";
            return sl.getSystemId()+":"+sl.getLineNumber()+":"+sl.getColumnNumber();
        }

        @Override
        public void message(XdmNode xn, QName errorCode, boolean bln, SourceLocator sl) {
            if (!handleMessage(xn.getStringValue(),getLocation(sl),null)) {
                if (bln)
                    logger.error("["+getLocation(sl)+"]["+errorCode+"]: "+xn.getStringValue());
                else
                    logger.info("["+getLocation(sl)+"]["+errorCode+"]: "+xn.getStringValue());
            }
        }
    }   
}
