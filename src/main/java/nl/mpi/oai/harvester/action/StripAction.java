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
 * This action corresponds to stripping off the OAI-PMH envelope surrounding
 * a harvested metadata record.
 * 
 * @author Lari Lampen (MPI-PL)
 */
public class StripAction implements Action {
    private static final Logger logger = LogManager.getLogger(StripAction.class);

    private final XPath xpath;
    private final DocumentBuilder db;

    private enum State {
        START,RECORD,HEADER,ID,METADATA,STOP,ERROR
    }

    public StripAction() throws ParserConfigurationException {
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
                // Get the child nodes of the "metadata" tag;
                // that's the content of the response without the
                // OAI-PMH envelope.

                NodeList content = null;
                try {
                    content = (NodeList) xpath.evaluate("//*[local-name()=" +
                                    "'metadata' and parent::*[local-name()=" +
                                    "'record']]/*",
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
                                "parent::*[local-name()='metadata']/preceding-sibling::*[local-name()='header']/*[local-name()='identifier']",
                                content.item(i),XPathConstants.STRING);
                        } catch (XPathExpressionException ex) {
                            logger.error(ex);
                        }
                        newRecords.add(new Metadata(
                                    id, record.getPrefix(),
                                    doc, record.getOrigin(), false, false)
                        );
                    }
                } else
                    logger.warn("No content was found in this envelope["+record.getId()+"], it might contain only deleted records");
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

                        State state = State.START;
                        int depth = 0;
                        String status = null;
                        String id = null;
                        while (!state.equals(State.STOP) && !state.equals(State.ERROR)) {
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
                                                depth = 1;
                                                status = null;
                                                id = null;
                                            }
                                            break;
                                    }
                                    break;
                                case RECORD:
                                    //logger.debug("state[RECORD] depth["+depth+"]");
                                    switch (eventType) {
                                        case XMLEvent2.START_ELEMENT:
                                            depth++;
                                            //logger.debug("start["+event.asStartElement().getName()+"] depth["+depth+"]");
                                            if (depth==2 && event.asStartElement().getName().getLocalPart().equals("header")) { //record/header
                                                Attribute attr = event.asStartElement().getAttributeByName(new QName("status"));
                                                if (attr!=null) {
                                                    status = attr.getValue();//record/header/@status
                                                    //logger.debug("status["+status+"]");
                                                }
                                                state = State.HEADER;
                                            } else if (depth==2 && event.asStartElement().getName().getLocalPart().equals("metadata")) { //record/metadata
                                                state = State.METADATA;
                                                baos = new ByteArrayOutputStream();
                                                writer = xmlOutputFactory.createXMLEventWriter(baos);
                                            }
                                            break;
                                        case XMLEvent2.END_ELEMENT:
                                            //logger.debug("end["+event.asEndElement().getName()+"] depth["+depth+"]");
                                            if (depth==1) { 
                                                if (event.asEndElement().getName().getLocalPart().equals("record")) {
                                                    state = State.START;
                                                    status = null;
                                                    id = null;
                                                } else {
                                                    logger.error("record XML element out of sync! Expected record got ["+event.asEndElement().getName()+"]");
                                                    state = State.ERROR;
                                                }
                                            }
                                            depth--;
                                            break;
                                    }
                                    break;
                                case HEADER:
                                    //logger.debug("state[HEADER] depth["+depth+"]");
                                    switch (eventType) {
                                        case XMLEvent2.START_ELEMENT:
                                            depth++;
                                            //logger.debug("start["+event.asStartElement().getName()+"] depth["+depth+"]");
                                            if (event.asStartElement().getName().getLocalPart().equals("identifier")) {//record/header/identifier
                                                state = State.ID;
                                            }
                                            break;
                                        case XMLEvent2.END_ELEMENT:
                                            //logger.debug("end["+event.asEndElement().getName()+"] depth["+depth+"]");
                                            if (depth==2) { 
                                                if (event.asEndElement().getName().getLocalPart().equals("header")) {
                                                    state = State.RECORD;
                                                } else {
                                                    logger.error("header XML element out of sync! Expected header got ["+event.asEndElement().getName()+"]");
                                                    state = State.ERROR;
                                                }
                                            }
                                            depth--;
                                            break;
                                    }
                                    break;
                                case ID://ID
                                    //logger.debug("state[ID] depth["+depth+"]");
                                    switch (eventType) {
                                        case XMLEvent2.CHARACTERS:
                                            id = event.asCharacters().getData();//record/header/identifier/text()
                                            //logger.debug("id["+id+"]");
                                            state = State.HEADER;
                                            break;
                                        default:
                                            state = State.ERROR;
                                            logger.error("identifier XML element out of sync!");
                                            break;
                                    }
                                    break;
                                case METADATA:
                                    //logger.debug("state[METADATA] depth["+depth+"]");
                                    switch (eventType) {
                                        case XMLEvent2.START_ELEMENT:
                                            depth++;
                                            break;
                                        case XMLEvent2.END_ELEMENT:
                                            //logger.debug("end["+event.asEndElement().getName()+"] depth["+depth+"]");
                                            if (depth==2) { 
                                                if (event.asEndElement().getName().getLocalPart().equals("metadata")) {
                                                    state = State.RECORD;
                                                } else {
                                                    logger.error("metadata XML element out of sync! Expected metadata got ["+event.asEndElement().getName()+"]");
                                                    state = State.ERROR;
                                                }
                                            }
                                            depth--;
                                            break;
                                    }
                                    if (state==State.METADATA) {
                                        writer.add(event);
                                    } else {
                                        writer.close();
                                        if (status == null || !status.equals("deleted")) {
                                            logger.debug("stripped XML stream["+i+"]["+id+"] to ["+baos.size()+"] bytes");
                                            newRecords.add(new Metadata(
                                                id, record.getPrefix(),
                                                new ByteArrayInputStream(baos.toByteArray()),
                                                record.getOrigin(),
                                                false, false)
                                            );
                                        }else {
                                            System.out.println(Util.toFileFormat(id) + ".xml");
                                        }
                                        writer = null;
                                        baos = null;
                                    }
                                    break;
                            }
                            if (reader.hasNext())
                                event = reader.nextEvent();
                            else
                                state = state == State.START? State.STOP: State.ERROR;
                            //logger.debug("END loop: state["+state+"] event["+event+"]["+event.getEventType()+"]");
                        }
                        if (state == State.ERROR)
                            logger.error("the XML was not properly processed!");
                    }
                    if (i==0)
                        logger.error("No content was found in this envelope["+record.getId()+"]");
                                       
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
	return "strip";
    }

    // All strip actions are equal.
    @Override
    public int hashCode() {
	return 1;
    }
    @Override
    public boolean equals(Object o) {
        return o instanceof StripAction;
    }

    @Override
    public Action clone() {
	try {
	    // All strip actions are the same. This is effectively a "deep"
	    // copy since it has its own XPath object.
	    return new StripAction();
	} catch (ParserConfigurationException ex) {
	    logger.error(ex);
	}
	return null;
    }

}
