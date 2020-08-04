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
 * This class represents a TCP Header.
 * Can be used to parse a TCP Header.
 *
 * Note that as of now the header flags are simply parsed as a whole and are not distinguished individually.
 *
 * @author Simon Langhoff, Janus Varmarken
 */
class TCPHeader {
    int sourcePort;
    int destinationPort;
    long sequenceNum;
    long acknowledgmentNum;
    byte offset;
    byte flags;
    int windowSize;
    int checksum;
    int urgentPointer;
    byte[] options;

    TCPHeader(ByteBuffer datagram){
        sourcePort = Unsigned.getUnsignedShort(datagram);
        destinationPort = Unsigned.getUnsignedShort(datagram);

        sequenceNum = Unsigned.getUnsignedInt(datagram);
        acknowledgmentNum = Unsigned.getUnsignedInt(datagram);
        byte tcpHeaderByte = datagram.get();
        // Retrieve the original numbers
        offset = (byte) ((tcpHeaderByte >> 4) & (byte) 0x0F);

        // TODO this is not correct, reserved is only 3 bits. However, for now this shouldn't be an issue.
        // byte reserved = (byte) (tcpHeaderByte & 0x0F);

        flags = datagram.get();
        windowSize = Unsigned.getUnsignedShort(datagram);
        checksum = Unsigned.getUnsignedShort(datagram);
        urgentPointer = Unsigned.getUnsignedShort(datagram);

        // Read all Options
        if (offset - 5 == 0) { // Check if any options
            options = null;
        } else { // Initialize array size to fit options
            options = new byte[(offset - 5) * 4];
            datagram.get(options);
        }
    }

    TCPHeader(int srcPort, int dstPort, long seqNo, long ackNo, byte flagFields, int recWindow, int checksum, int urgentPointer, byte[] options){
        this.sourcePort = srcPort;
        this.destinationPort = dstPort;
        this.sequenceNum = seqNo;
        this.acknowledgmentNum = ackNo;
        this.flags = flagFields;
        this.windowSize = recWindow;
        this.checksum = checksum;
        this.urgentPointer = urgentPointer;
        this.options = options;
    }

    public int getDestinationPort() {
        return destinationPort;
    }

    public int getSourcePort() {
        return sourcePort;
    }

    public long getSequenceNum() {
        return sequenceNum;
    }

    public long getAcknowledgmentNum() {
        return acknowledgmentNum;
    }

    public byte getFlags() {
        return flags;
    }

    public int getWindowSize() {
        return windowSize;
    }

    public int getChecksum() {
        return checksum;
    }

    public int getUrgentPointer() {
        return urgentPointer;
    }

    public byte[] getOptions() {
        return options;
    }
}