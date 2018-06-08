/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * The aion network project leverages useful source code from other
 * open source projects. We greatly appreciate the effort that was
 * invested in these projects and we thank the individual contributors
 * for their work. For provenance information and contributors
 * please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 * Aion foundation.
 *
 */

package org.aion.zero.impl.config;

import org.aion.base.type.Address;
import org.aion.mcf.config.Cfg;
import org.aion.mcf.config.CfgConsensus;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

public final class CfgConsensusPow extends CfgConsensus implements ICfgConsensus {

    private final CfgEnergyStrategy cfgEnergyStrategy;

    CfgConsensusPow() {
        this.mining = false;
        this.minerAddress = Address.ZERO_ADDRESS().toString();
        this.cpuMineThreads = (byte) (Runtime.getRuntime().availableProcessors() >> 1); // half the available processors
        this.extraData = "AION";
        this.cfgEnergyStrategy = new CfgEnergyStrategy();
        this.seed = false;
    }

    private boolean mining;

    private boolean seed;

    private String minerAddress;

    private byte cpuMineThreads;

    protected String extraData;

    public void fromXML(final XMLStreamReader sr) throws XMLStreamException {
        loop: while (sr.hasNext()) {
            int eventType = sr.next();
            switch (eventType) {
                case XMLStreamReader.START_ELEMENT:
                    String elementName = sr.getLocalName().toLowerCase();
                    switch (elementName) {
                        case "mining":
                            this.mining = Boolean.parseBoolean(Cfg.readValue(sr));
                            break;
                        case "seed":
                            this.seed = Boolean.parseBoolean(Cfg.readValue(sr));
                                break;
                        case "miner-address":
                            this.minerAddress = Cfg.readValue(sr);
                            break;
                        case "cpu-mine-threads":
                            this.cpuMineThreads = Byte.valueOf(Cfg.readValue(sr));
                            break;
                        case "extra-data":
                            this.extraData = Cfg.readValue(sr);
                            break;
                        case "nrg-strategy":
                            this.cfgEnergyStrategy.fromXML(sr);
                            break;
                        default:
                            Cfg.skipElement(sr);
                            break;
                    }
                    break;
                case XMLStreamReader.END_ELEMENT:
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

            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeStartElement("mining");
            xmlWriter.writeCharacters(this.isActorActive() + "");
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeStartElement("miner-address");
            xmlWriter.writeCharacters(this.getActorAddress());
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeStartElement("cpu-mine-threads");
            xmlWriter.writeCharacters(this.getNumCpuThreads() + "");
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeStartElement("extra-data");
            xmlWriter.writeCharacters(this.getExtraData());
            xmlWriter.writeEndElement();

            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeStartElement("nrg-strategy");
            xmlWriter.writeCharacters(this.cfgEnergyStrategy.toXML());
            xmlWriter.writeCharacters("\r\n\t\t\t");
            xmlWriter.writeEndElement();

//            xmlWriter.writeCharacters("\r\n\t");
//            xmlWriter.writeEndElement();
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

    public void setExtraData(final String _extraData) {
        this.extraData = _extraData;
    }

    @Override
    public boolean isActorActive() {
        return this.mining;
    }

    @Override
    public String getActorAddress() {
        return this.minerAddress;
    }

    @Override
    public byte getNumCpuThreads() {
        int procs = Runtime.getRuntime().availableProcessors();
        return (byte) Math.min(procs, this.cpuMineThreads);
    }

    @Override
    public CfgEnergyStrategy getEnergyStrategy() {
        return this.cfgEnergyStrategy;
    }

    @Override
    public String getExtraData() {
        return this.extraData;
    }

    @Override
    public boolean isSeed() {
        return seed;
    }

    @Override
    public void setIsActorActive(boolean isActive) {
        this.mining = isActive;
    }
}