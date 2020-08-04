/*
 *  This file is part of AntMonitor <https://athinagroup.eng.uci.edu/projects/antmonitor/>.
 *  Copyright (C) 2018 Anastasia Shuba and the UCI Networking Group
 *  <https://athinagroup.eng.uci.edu>, University of California, Irvine.
 *
 *  AntMonitor is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  AntMonitor is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with AntMonitor. If not, see <http://www.gnu.org/licenses/>.
 */
package thirdpartyneedsrewritten.ip;

import java.nio.ByteBuffer;

/**
 * This class represents a UDP Header.
 * Can be used to parse a UDP Header.
 *
 * @author Simon Langhoff, Janus Varmarken
 */
class UDPHeader {
    int sourcePort;
    int destinationPort;
    int length;
    int checksum;

    /**
     * Constructs a UDPHeader object, assumes position of bytebuffer is at beginning of header.
     *
     * @param datagram
     */
    UDPHeader(ByteBuffer datagram) {
        sourcePort = Unsigned.getUnsignedShort(datagram);
        destinationPort = Unsigned.getUnsignedShort(datagram);
        length = Unsigned.getUnsignedShort(datagram);
        checksum = Unsigned.getUnsignedShort(datagram);
    }

    UDPHeader(int sourcePort, int destinationPort, int length, int checksum) {
        this.sourcePort = sourcePort;
        this.destinationPort = destinationPort;
        this.length = length;
        this.checksum = checksum;
    }

    public int getSourcePort() {
        return sourcePort;
    }

    public int getDestinationPort() {
        return destinationPort;
    }

    public int getLength() {
        return length;
    }

    public int getChecksum() {
        return checksum;
    }
}
