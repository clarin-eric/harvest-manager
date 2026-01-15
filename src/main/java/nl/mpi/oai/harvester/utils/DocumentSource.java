/*
 * Copyright (C) 2016, CLARIN ERIC.
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

package nl.mpi.oai.harvester.utils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Date;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author menzowi
 */
public class DocumentSource {
    
    private static final Logger logger = LogManager.getLogger(DocumentSource.class);
    
    private String id = null;
    
    private Document doc = null;
    private InputStream str = null;
    
    public DocumentSource(Document doc) {
        this("",doc);
    }
    
    public DocumentSource(InputStream str) {
        this("",str);
    }
    
    public DocumentSource(String id, Document doc) {
        this.id = id;
        this.doc = doc;
    }
    
    public DocumentSource(String id, InputStream str) {
        this.id = id;
        this.str = str;
    }
    
    public boolean hasDocument() {
        return (doc!=null);
    }
    
    public boolean hasStream() {
        return (str!=null);
    }

    public boolean hasSource() {
        return (str!=null);
    }
    
    public Document getDocument() {
        if (doc==null) {
            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                doc = db.parse(getSource());         
                str = null;
                logger.debug("switched from stream to tree for DocumentSource["+id+"]",new Throwable());
            } catch (ParserConfigurationException | SAXException | IOException ex) {
                logger.error(ex.getMessage(),ex);
                logger.debug("failed to switch from stream to tree for DocumentSource["+id+"]");
            }
        }
        return doc;
    }
    
    public InputSource getSource() {
        return new InputSource(getStream());
    }
    
    public InputStream getStream() {
        if (str==null)
            return null;
        try {
            str.reset();
        } catch (IOException ex) {
            logger.error(ex.getMessage(),ex);
            logger.debug("failed to reset stream for DocumentSource["+id+"]");
        }
        return str;
    }
    
    public void setDocument(Document doc) {
        if (str!=null)
                logger.debug("switched from stream to tree for DocumentSource["+id+"]",new Throwable());
        this.doc = doc;
        this.str = null;
    }
    
    public void setStream(InputStream str) {
        if (doc!=null)
                logger.debug("switched from tree to stream for DocumentSource["+id+"]",new Throwable());
        this.str = str;
        this.doc = null;
    }
    
    public void close() {
        if (str!=null) {
            try {
                str.close();
            } catch (IOException ex) {
                logger.error(ex.getMessage(),ex);
                logger.debug("failed to close stream for DocumentSource["+id+"]");
            }
        }
    }

    public static DocumentSource fetch(String requestURL, byte[] body, String contenttype, String accept, int timeout, Path temp) throws MalformedURLException, IOException {
        logger.debug("requestURL=" + requestURL);
        InputStream in;
        URL url = new URL(requestURL);
        HttpURLConnection con = null;
        int responseCode = 0;
        do {
            con = (HttpURLConnection) url.openConnection();
            con.setRequestProperty("User-Agent", "OAIHarvester/2.0");
            con.setRequestProperty("Accept-Encoding",
                    "compress, gzip, identify");
            if ((accept != null) && !accept.trim().equals("")) {
                con.setRequestProperty("accept", accept);
            }
            if ((body != null)) {
                if ((contenttype != null) && !contenttype.trim().equals("")) {
                    con.setRequestProperty("content-type", contenttype);
                }
                con.setRequestMethod("POST");
                con.setDoOutput(true);
                try (OutputStream os = con.getOutputStream()) {
                    os.write(body, 0, body.length);
                }
            }
            if (timeout > 0) {
                logger.debug("timeout=" + timeout);
                con.setConnectTimeout(timeout*1000);
                con.setReadTimeout(timeout*1000);
            }
            try {
                responseCode = con.getResponseCode();
                logger.debug("responseCode=" + responseCode);
            } catch (FileNotFoundException e) {
                // assume it's a 503 response
                logger.info(requestURL, e);
                responseCode = HttpURLConnection.HTTP_UNAVAILABLE;
            } catch(Exception e) {
                logger.error("couldn't connect to '"+requestURL+"': "+e.getMessage());
                throw e;
            }
            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                requestURL = con.getHeaderField("Location");
                logger.debug("redirect to requestURL=" + requestURL);
                url = new URL(requestURL);
                responseCode = HttpURLConnection.HTTP_UNAVAILABLE;
            } else if (responseCode == HttpURLConnection.HTTP_UNAVAILABLE) {
                long retrySeconds = con.getHeaderFieldInt("Retry-After", -1);
                if (retrySeconds == -1) {
                    long now = (new Date()).getTime();
                    long retryDate = con.getHeaderFieldDate("Retry-After", now);
                    retrySeconds = retryDate - now;
                }
                if (retrySeconds == 0) { // Apparently, it's a bad URL
                    throw new FileNotFoundException("Bad URL["+requestURL+"]?");
                }
                logger.debug("Retry-After=" + retrySeconds);
                if (retrySeconds > 0) {
                    try {
                        Thread.sleep(retrySeconds * 1000);
                    } catch (InterruptedException ex) {
                        logger.error(ex);
                    }
                }
            }
        } while (responseCode == HttpURLConnection.HTTP_UNAVAILABLE);
        String contentEncoding = con.getHeaderField("Content-Encoding");
        logger.debug("Content-Encoding=" + contentEncoding);
        if ("compress".equals(contentEncoding)) {
            ZipInputStream zis = new ZipInputStream(con.getInputStream());
            zis.getNextEntry();
            in = zis;
        } else if ("gzip".equals(contentEncoding)) {
            in = new GZIPInputStream(con.getInputStream());
        } else if ("deflate".equals(contentEncoding)) {
            in = new InflaterInputStream(con.getInputStream());
        } else {
            in = con.getInputStream();
        }

        if (temp!=null) {
            FileOutputStream out = new FileOutputStream(temp.toFile());
            org.apache.commons.io.IOUtils.copy(in,out,1000000);
            out.close();
            logger.debug("temp["+temp+"] for URL["+requestURL+"]");
            in = new MarkableFileInputStream(new FileInputStream(temp.toFile()));
            //if (requestURL.contains("https://ssh.datastations.nl/oai")) {
            //    in = iconv(temp, in);
            //}
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int size = org.apache.commons.io.IOUtils.copy(in, baos);
            logger.debug("buffered ["+size+"] bytes for URL["+requestURL+"]");
            in = new ByteArrayInputStream(baos.toByteArray());
        }
        return new DocumentSource(in);
    }
    
    public static InputStream iconv(Path input, InputStream in) throws IOException {
        Process p = Runtime.getRuntime().exec("iconv -f utf-8 -t utf-8 -c");
        BufferedWriter bwo = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));
        BufferedReader bri = new BufferedReader(new InputStreamReader(p.getInputStream()));
    
        BufferedReader fbri = new BufferedReader(new InputStreamReader(in));
        String line  = null;
        while( ( line = fbri.readLine() ) != null ) {
            bwo.append(line);
            bwo.newLine();
        }
        bwo.flush();
        bwo.close();
        fbri.close();
    
        BufferedWriter fbwo = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(input.toFile())));
        while( ( line = bri.readLine() ) != null ) {
            fbwo.append(line);
            fbwo.newLine();
        }
        bri.close();
        fbwo.flush();
        fbwo.close();
        try {
            p.waitFor();
        } catch ( InterruptedException e ) {
        }
    
        return new MarkableFileInputStream(new FileInputStream(input.toFile()));
    }

}
