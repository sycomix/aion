package org.aion.zero.impl.config;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.aion.mcf.config.Cfg;

public final class CfgConEngine {
    private CfgConsensusPow conPoW;
    private String engine;

    CfgConEngine() {
        this.conPoW = new CfgConsensusPow();
        this.engine = "PoW";
    }

    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        loop: while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    String elementName = sr.getLocalName().toLowerCase();
                    switch (elementName) {
                        case "engine":
                            this.engine = Cfg.readValue(sr);
                            break;
                        case "engine-specs":
                            switch (this.engine.toLowerCase()) {
                                case "pow":
                                    this.conPoW.fromXML(sr);
                                    break;
                                default:
                                    throw new XMLStreamException("unsupported consensus engine.");
                            }
                            break;
                        default:
                            Cfg.skipElement(sr);
                            break;
                    }
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    break loop;
            }
        }
    }

    String toXML() {
        final XMLOutputFactory output = XMLOutputFactory.newInstance();
        output.setProperty("escapeCharacters", false);
        XMLStreamWriter xmlWriter;
        String xml;
        try {
            Writer strWriter = new StringWriter();
            xmlWriter = output.createXMLStreamWriter(strWriter);
            xmlWriter.writeCharacters("\r\n\t");
            xmlWriter.writeStartElement("consensus");

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("engine");
            xmlWriter.writeCharacters(this.engine);
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeStartElement("engine-specs");
            xmlWriter.writeCharacters(this.conPoW.toXML()); // sets up PoW by default.
            xmlWriter.writeCharacters("\r\n\t\t");
            xmlWriter.writeEndElement();
            xmlWriter.writeCharacters("\r\n\t");
            xmlWriter.writeEndElement();

            xml = strWriter.toString();
            strWriter.flush();
            strWriter.close();
            xmlWriter.flush();
            xmlWriter.close();
            return xml;
        } catch (IOException | XMLStreamException e) {
            e.printStackTrace();
            return "";
        }
    }


    /**
     * Returns the specified consensus Cfg class as a ICfgConsensus object for easy manipulation.
     *
     * @return the specified consensus Cfg class.
     */
    public ICfgConsensus getCfgConsensus() {
        switch (this.engine.toLowerCase()) {
            case "pow": return this.conPoW;
            default: return null;
        }
    }

    /**
     * Returns the consensus engine to use as a lower-case String.
     *
     * @return the engine to use.
     */
    public String getEngineLowercase() {
        return this.engine.toLowerCase();
    }

}
