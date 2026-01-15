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

package nl.mpi.oai.harvester.action;

import nl.mpi.oai.harvester.control.FileSynchronization;
import nl.mpi.oai.harvester.control.Util;
import nl.mpi.oai.harvester.metadata.Metadata;
import nl.mpi.oai.harvester.metadata.Record;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.evt.XMLEvent2;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.XMLEvent;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;


/**
 * This action corresponds to splitting the OAI-PMH envelope with multiple records
 * into multiple ones with each one harvested metadata record.
 * 
 * @author Menzo Windhouwer (CLARIN-ERIC)
 */
public class XMLSplitAction implements Action {

    private final Logger logger = LogManager.getLogger(XMLSplitAction.class);

    private final XPath xpath;
    private final DocumentBuilder db;

    private enum State {
        START,RECORD,HEADER,ID,METADATA,STOP,ERROR
    }

    public XMLSplitAction() throws ParserConfigurationException {
	XPathFactory xpf = XPathFactory.newInstance();
	xpath = xpf.newXPath();	
	DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	db = dbf.newDocumentBuilder();
    }

    @Override
    public boolean perform(List<Record> records) {
        List<Metadata> newRecords = new ArrayList<>();

        for (Record rec : records) {
            Metadata record = (Metadata)rec;

            if (record.hasDoc()) {
                logger.debug("Found record and has doc, record id: ["+record.getId()+"]");
                NodeList content = null;
                try {
                    content = (NodeList) xpath.evaluate("//*[local-name()='record']",
                            record.getDoc(), XPathConstants.NODESET);
                } catch (XPathExpressionException ex) {
                    logger.error(ex);
                }
                if ((content != null) && (content.getLength()>0)) {
                    for (int i=0;i<content.getLength();i++) {
                        Document doc = db.newDocument();
                        Node copy = doc.importNode(content.item(i), true);
                        doc.appendChild(copy);
                        String id = "";
                        try {
                            id = (String) xpath.evaluate(
                                    "./*[local-name()='record']/@id",
                                    content.item(i),XPathConstants.STRING);

                            if (id == null || id.equals("")) id = "rec-"+i;
                            logger.debug("split off XML doc["+i+"]["+id+"]");
                                newRecords.add( new Metadata(
                                        id, record.getPrefix(),
                                        doc, record.getOrigin(), false, false));
                        } catch (XPathExpressionException ex) {
                            logger.error(ex);
                        }
                    }
                } else
                    logger.warn("No content was found in this envelope["+record.getId()+"]");
            } else {
                XMLEventReader reader = null;
                XMLEventWriter writer = null;
                try {
                    XMLInputFactory2 xmlInputFactory = (XMLInputFactory2) XMLInputFactory2.newInstance();
                    xmlInputFactory.configureForConvenience();
                    XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
                    xmlOutputFactory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
                    
                    ByteArrayOutputStream baos = null;
                    int i = 0;
                    
                    reader = xmlInputFactory.createXMLEventReader(record.getStream());
                    writer = null;
                    
                    if (reader.hasNext()) {
                        XMLEvent event = reader.nextEvent();
                        
                        State state = State.START; // 1:START 2:RECORD 3:HEADER 4:ID 0:STOP -1:ERROR
                        int depth = 0;
                        String status = null;
                        String id = null;
                        while (!state.equals(state.STOP) && !state.equals(state.ERROR)) {
                            //logger.debug("BEGIN loop: state["+state+"] event["+event+"]["+event.getEventType()+"]");
                            int eventType = event.getEventType();
                            switch (state) {
                                case START:
                                    //logger.debug("state[START]");
                                    switch (eventType) {
                                        case XMLEvent2.START_ELEMENT:
                                            QName qn = event.asStartElement().getName();
                                            if (qn.getLocalPart().equals("record")) {
                                                state = State.RECORD;
                                                i++;
                                                baos = new ByteArrayOutputStream();
                                                writer = xmlOutputFactory.createXMLEventWriter(baos);
                                                writer.add(event);
                                                status = null;
                                                id = null;
                                                depth = 1;
                                                Attribute attr = event.asStartElement().getAttributeByName(new QName("id"));
                                                if (attr!=null) {
                                                    id = attr.getValue();                                                }
                                            }
                                            break;
                                    }
                                    break;
                                case RECORD://RECORD
                                    //logger.debug("state[RECORD] depth["+depth+"]");
                                    switch (eventType) {
                                        case XMLEvent2.START_ELEMENT:
                                            depth++;
                                            break;
                                        case XMLEvent2.END_ELEMENT:
                                            //logger.debug("end["+event.asEndElement().getName()+"] depth["+depth+"]");
                                            if (depth==1) { 
                                                if (event.asEndElement().getName().getLocalPart().equals("record")) {
                                                    state = State.START;
                                                } else {
                                                    logger.error("record XML element out of sync! Expected record got ["+event.asEndElement().getName()+"]");
                                                    state = State.ERROR;
                                                }
                                            }
                                            depth--;
                                            break;
                                    }
                                    writer.add(event);
                                    if (state==State.START) {
                                        writer.close();
                                        logger.debug("split off XML stream["+i+"]["+id+"] with ["+baos.size()+"] bytes");
                                        newRecords.add(new Metadata(
                                            id, record.getPrefix(),
                                            new ByteArrayInputStream(baos.toByteArray()),
                                            record.getOrigin(),
                                            false, false)
                                        );

                                        writer = null;
                                        baos = null;
                                        status = null;
                                        id = null;
                                    }
                                    break;
                            }
                            if (reader.hasNext())
                                event = reader.nextEvent();
                            else
                                state = state == State.START? State.STOP: State.ERROR;// if START then STOP else ERROR
                            //logger.debug("END loop: state["+state+"] event["+event+"]["+event.getEventType()+"]");
                        }
                        if (state.equals(State.ERROR))
                            logger.error("the XML was not properly processed!");
                    }
                    if (i==0) {
                        logger.error("No content was found in this envelope["+record.getId()+"]");
                    }                                       
                } catch (XMLStreamException ex) {
                    logger.error("",ex);
                } finally {
                    try {
                        if (reader != null)
                            reader.close();
                        if (writer != null)
                            writer.close();
                    } catch (XMLStreamException ex) {
                    }
                }
            }
        }
        records.clear();
        records.addAll(newRecords);
        return true;
    }

    @Override
    public String toString() {
	return "xml-split";
    }

    // All split actions are equal.
    @Override
    public int hashCode() {
	return 1;
    }
    @Override
    public boolean equals(Object o) {
        return o instanceof XMLSplitAction;
    }

    @Override
    public Action clone() {
	try {
	    // All split actions are the same. This is effectively a "deep"
	    // copy since it has its own XPath object.
	    return new XMLSplitAction();
	} catch (ParserConfigurationException ex) {
	    logger.error(ex);
	}
	return null;
    }
}
