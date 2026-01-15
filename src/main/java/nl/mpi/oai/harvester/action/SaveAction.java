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
import nl.mpi.oai.harvester.control.OutputDirectory;
import nl.mpi.oai.harvester.control.Util;
import nl.mpi.oai.harvester.metadata.Metadata;
import nl.mpi.oai.harvester.utils.MarkableFileInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.stax2.XMLInputFactory2;
import org.w3c.dom.Document;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import nl.mpi.oai.harvester.metadata.Record;

/**
 * This class represents the action of saving a record onto the file system.
 *
 * @author Lari Lampen (MPI-PL)
 */
public class SaveAction implements Action {
    private static final Logger logger = LogManager.getLogger(SaveAction.class);

    protected OutputDirectory dir;
    protected String suffix;
    protected boolean offload;
    protected boolean history;

    /**
     * Create a new save action.
     *
     * @param dir    output directory to save to
     * @param suffix suffix to be added to identifier to generate filename
     */
    public SaveAction(OutputDirectory dir, String suffix, boolean offload, boolean history) {
        this.dir = dir;
        this.suffix = (suffix == null) ? "" : suffix;
        this.offload = offload;
        this.history = history;
    }

    public Document getDocument(Metadata metadata) {
        return metadata.getDoc();
    }

    @Override
    public boolean perform(List<Record> records) {

        for (Record rec : records) {
            
            if (!(rec instanceof Metadata)) {
                logger.error("Save action doesn't support this type["+rec.getClass()+"] of records!");
                return false;
            }
            
            Metadata record = (Metadata)rec;
            
            OutputStream os = null;
            XMLEventReader reader = null;
            XMLEventWriter writer = null;
            try {
                Path path = chooseLocation(record);
                if(history){
                    FileSynchronization.saveToHistoryFile(record.getOrigin(), path, FileSynchronization.Operation.INSERT);
                    FileSynchronization.getProviderStatistic(record.getOrigin()).incRecordCount();
                }
                os = Files.newOutputStream(path);
                if (record.hasDoc()) {
                    TransformerFactory transformerFactory = TransformerFactory.newInstance();
                    Transformer transformer = transformerFactory.newTransformer();
                    transformer.setOutputProperty(OutputKeys.INDENT, "yes");

                    DOMSource source = new DOMSource(record.getDoc());
                    StreamResult result = new StreamResult(os);

                    transformer.transform(source, result);

                    logger.debug("saved XML doc[" + path + "] with [" + XPathFactory.newInstance().newXPath().evaluate("count(//*)", record.getDoc()) + "] nodes");
                } else {
                    XMLInputFactory2 xmlInputFactory = (XMLInputFactory2) XMLInputFactory2.newInstance();
                    xmlInputFactory.configureForConvenience();
                    XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
                    xmlOutputFactory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);

                    reader = xmlInputFactory.createXMLEventReader(record.getStream());
                    writer = xmlOutputFactory.createXMLEventWriter(os);

                    writer.add(reader);
                    writer.close();
                    if (offload) {
                        record.setStream(new MarkableFileInputStream(new FileInputStream(path.toFile())));
                        logger.debug("offloaded XML stream[" + path + "]");
                    }

                    logger.debug("saved XML stream[" + path + "]");
                }
            } catch (TransformerException | IOException | XPathExpressionException | XMLStreamException ex) {
                logger.error(ex);
                return false;
            } finally {
                try {
                    if (os != null)
                        os.close();
                    if (reader != null)
                        reader.close();
                    if (writer != null)
                        writer.close();
                } catch (IOException | XMLStreamException e) {
                }
            }
        }

        return true;
    }

    /**
     * Simply choose location to save in.
     *
     * @param metadata metadata record
     * @return path to new file in suitable location
     * @throws IOException something went wrong when creating the new file
     */
    protected Path chooseLocation(Metadata metadata) throws IOException {
        return dir.placeNewFile(Util.toFileFormat(metadata.getId(),suffix));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("save to ");
        sb.append(dir);
        if (!suffix.isEmpty())
            sb.append(" using suffix ").append(suffix);
        return sb.toString();
    }

    // Save actions are equal iff the directories are the same.
    @Override
    public int hashCode() {
        return dir.hashCode() + 29 * suffix.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof SaveAction && !(o instanceof SaveGroupedAction)) {
            SaveAction a = (SaveAction) o;
            // OK, this is pretty stupid, but works...
            return this.toString().equals(a.toString());
        }
        return false;
    }

    @Override
    public Action clone() {
        // This is a shallow copy, resulting in multiple references to a single
        // OutputDirectory, which is as intended.
        return new SaveAction(dir, suffix, offload, history);
    }
}
