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
 * @author Hieu Le
 */
public class TCPPacket {

    /**
     * Constructs a TCPHeader object, assumes position of ByteBuffer is at beginning of header.
     * This will change the ByteBuffer's position
     * @param buffer
     * @return TCPHeader
     */
    public static TCPHeader readHeader(ByteBuffer buffer) {
        return new TCPHeader(buffer);
    }

    /**
     * Reads the destination port from the provided {@code datagram}, without changing its position.
     * @param datagram  A {@link ByteBuffer} containing an IP datagram.
     * @param offset offset of the position of {@code datagram} from the beginning of the datagram packet.
     * @param ipHeaderLength The length of the ip header
     * @return The destination port read from the TCP packet part of the IP datagram.
     */
    public static int readDestinationPort(ByteBuffer datagram, int offset, int ipHeaderLength) {
        return Unsigned.getUnsignedShort(datagram, ipHeaderLength + 2 + offset);
    }

    /**
     * Reads the transport header length byte from the provided {@code datagram}, without changing its position.
     * @param datagram  A {@link ByteBuffer} containing an IP datagram.
     * @param offset offset of the position of {@code datagram}from the beginning of the datagram packet.
     * @param ipHeaderLength The length of the ip header
     * @return the transport header length byte for TCP
     */
    public static short readTransportHeaderLength(ByteBuffer datagram, int offset, int ipHeaderLength) {
        short headerByte = Unsigned.getUnsignedByte(datagram, 12 + offset + ipHeaderLength);
        return (short) ((headerByte >> 4) & (byte) 0x0F);
    }

    /**
     * Reads the TCP header length byte from the provided {@code byte[]}.
     * @param datagram  A {@code byte[]} containing an IP datagram.
     * @param ipHeaderLength The IP datagram header length byte.
     * @return the transport header length byte.
     */
    public static short readHeaderLength(byte[] datagram, byte ipHeaderLength) {
        // Convert Length header to byte length
        int ipHeaderEndPosition = IpDatagram.wordsToBytes(ipHeaderLength); // 4 bytes per 32 bit header section

        short headerByte = (short) (datagram[12 + ipHeaderEndPosition] & (short) 0xff);
        return (short) ((headerByte >> 4) & (byte) 0x0F);
    }

    /**
     * Extract sequence number of a TCPv4 packet
     * @param packet
     * @param offset Offset into the packet
     * @return
     */
    public static long extractTCPv4SequenceNumber(byte[] packet, int offset) {
        // Destination port is bytes 5,6,7,8 after IP header
        int ipHeaderLen = IpDatagram.extractIPv4HeaderLength(packet, offset);
        long sequenceNumber = (    (packet[offset+ipHeaderLen+4] << 24 & 0x00000000FF000000L)
                | (packet[offset+ipHeaderLen+5] << 16 & 0x0000000000FF0000L)
                | (packet[offset+ipHeaderLen+6] <<  8 & 0x000000000000FF00L)
                | (packet[offset+ipHeaderLen+7]       & 0x00000000000000FFL)   );

        return sequenceNumber;
    }

    public static long extractTCPv4SequenceNumber(byte[] packet) {
        return extractTCPv4SequenceNumber(packet, 0);
    }

    /**
     * Check if a TCPv4 packet is a SYN packet (SYN flag set)
     * @param packet Byte array containing the IP datagram
     * @param offset Offset into the byte array to start the datagram
     */
    public static boolean isSynPacket(byte[] packet, int offset) {
        // First 20 bytes are IP header: packet[0]-packet[19]
        // Next 20 bytes are TCP header: packet[20]-packet[39]
        // Control bits is the last 6 bits of byte 34: packet[33]
        // Bits: Urgent, Ack, Push, Reset, Syn, Fin
        // Mask to use: Ob00000010
        int ipHeaderLen = IpDatagram.extractIPv4HeaderLength(packet, offset);
        byte mask = 0b00000010;
        byte control = packet[offset+ipHeaderLen+13];
        int and = ((int) mask) & ((int) control);
        return (and == 2);
    }

    public static boolean isSynPacket(byte[] packet) {
        return isSynPacket(packet, 0);
    }

    /**
     * Check if a TCPv4 packet is a RES packet (RES flag set)
     * @param packet Byte array containing the IP datagram
     * @param offset Offset into the byte array to start the datagram
     */
    public static boolean isResetPacket(byte[] packet, int offset) {
        // First 20 bytes are IP header: packet[0]-packet[19]
        // Next 20 bytes are TCP header: packet[20]-packet[39]
        // Control bits is the last 6 bits of byte 34: packet[33]
        // Bits: Urgent, Ack, Push, Reset, Syn, Fin
        // Mask to use: Ob00000100
        int ipHeaderLen = IpDatagram.extractIPv4HeaderLength(packet, offset);
        byte mask = 0b00000100;
        byte control = packet[offset+ipHeaderLen+13];
        int and = ((int) mask) & ((int) control);
        return (and == 4);
    }

    public static boolean isResetPacket(byte[] packet) {
        return isResetPacket(packet, 0);
    }

    /**
     * Check if a TCPv4 packet is a FIN packet (RES flag set)
     * @param packet Byte array containing the IP datagram
     * @param offset Offset into the byte array to start the datagram
     */
    public static boolean isFinPacket(byte[] packet, int offset) {
        // First 20 bytes are IP header: packet[0]-packet[19]
        // Next 20 bytes are TCP header: packet[20]-packet[39]
        // Control bits is the last 6 bits of byte 34: packet[33]
        // Bits: Urgent, Ack, Push, Reset, Syn, Fin
        // Mask to use: Ob0000001
        int ipHeaderLen = IpDatagram.extractIPv4HeaderLength(packet, offset);
        byte mask = 0b0000001;
        byte control = packet[offset+ipHeaderLen+13];
        int and = ((int) mask) & ((int) control);
        return (and == 1);
    }

    public static boolean isFinPacket(byte[] packet) {
        return isFinPacket(packet, 0);
    }
    /**
     * Check if a TCPv4 packet is an ACK packet (ACK flag set)
     * @param packet Byte array containing the IP datagram
     * @param offset Offset into the byte array to start the datagram
     */
    public static boolean isAckPacket(byte[] packet, int offset) {
        // First 20 bytes are IP header: packet[0]-packet[19]
        // Next 20 bytes are TCP header: packet[20]-packet[39]
        // Control bits is the last 6 bits of byte 34: packet[33]
        // Bits: Urgent, Ack, Push, Reset, Syn, Fin
        // Mask to use: Ob00010000
        int ipHeaderLen = IpDatagram.extractIPv4HeaderLength(packet, offset);
        byte mask = 0b00010000;
        byte control = packet[offset+ipHeaderLen+13];
        int and = ((int) mask) & ((int) control);
        return (and == 16);
    }

    public static boolean isAckPacket(byte[] packet) {
        return isAckPacket(packet, 0);
    }

    /**
     * Check if a TCPv4 packet contains data
     * @param packet Byte array containing the IP datagram
     * @param offset Offset into the byte array to start the datagram
     */
    public static boolean hasData(byte[] packet, int offset) {
        int ipHeaderLen = IpDatagram.extractIPv4HeaderLength(packet, offset);
        int tcpHeaderLen = TCPPacket.extractTCPv4HeaderLength(packet, offset);
        int dataLen = packet.length - offset - ipHeaderLen - tcpHeaderLen;
        return (dataLen > 0);
    }

    public static boolean hasData(byte[] packet) {
        return hasData(packet, 0);
    }

    /**
     * Extract ACK number of a TCPv4 packet
     * @param packet
     * @param offset Offset into the packet
     * @return
     */
    public static long extractTCPv4AckNumber(byte[] packet, int offset) {
        // Destination port is bytes 9,10,11,12 after IP header
        int ipHeaderLen = IpDatagram.extractIPv4HeaderLength(packet, offset);
        long ackNumber = (    (packet[offset+ipHeaderLen+8] << 24 & 0x00000000FF000000L)
                | (packet[offset+ipHeaderLen+9] << 16 & 0x0000000000FF0000L)
                | (packet[offset+ipHeaderLen+10] <<  8 & 0x000000000000FF00L)
                | (packet[offset+ipHeaderLen+11]       & 0x00000000000000FFL)   );

        return ackNumber;
    }

    public static long extractTCPv4AckNumber(byte[] packet) {
        return extractTCPv4AckNumber(packet, 0);
    }

    /**
     * Extract TCPv4 header length in byte of a given TCP packet
     * @param packet
     * @param offset Offset into the packet
     * @return
     */
    public static int extractTCPv4HeaderLength(byte[] packet, int offset) {
        // TCP header length is contained in the first 4 bits of bytes 13 after IP header
        //
        // Using mask = 00001111
        // 0101XXXX >> 4 & 00001111 = 00000101 = 5 (32-bit words, or 20 bytes)

        int ipHeaderLen = IpDatagram.extractIPv4HeaderLength(packet, offset);

        byte theByte = packet[offset+ipHeaderLen+12];
        byte mask = (byte) 0b00001111;
        int numWords = (int) ((theByte >> 4) & (int) mask);
        return numWords * 4;
    }

    public static int extractTCPv4HeaderLength(byte[] packet) {
        return extractTCPv4HeaderLength(packet, 0);
    }

    public static boolean isClientHello(byte[] packet, int dataOffset) {
        /* Following guide from https://github.com/dlundquist/sniproxy/blob/master/src/tls.c */

        final int TLS_HEADER_LEN = 5;
        int index = dataOffset;
        final byte CONTENT_TYPE__HANDSHAKE = 0x16;
        final byte HANDSHAKE_TYPE__CLIENT_HELLO = 1;
        final byte TLS_VERSION_MAJOR = 0x03;

        final int dataLen = packet.length - dataOffset;

        if (dataLen < TLS_HEADER_LEN) {
            //Logg.e(TAG, "Invalid TLS Packet. Less than TLS Header Length");
            // incomplete request
            return false;
        }

        if (index < packet.length) {
            byte contentType = packet[index];
            byte tlsVersionMajor = packet[index+1];
            byte tlsMinorMajor = packet[index+2];

            if (tlsVersionMajor < TLS_VERSION_MAJOR) {
                //Logg.e(TAG, "TLS Version under 1.0. Cannot support SNI");
                return false;
            }

            final int tlsDataLength = ((packet[index+3] << 8 & 0x0000FF00) | (packet[index+4] & 0x000000FF)) + TLS_HEADER_LEN;
            if (dataLen < tlsDataLength) {
                //Logg.e(TAG, "Invalid TLS Packet. Data length reported " + tlsDataLength + " is more than packet data length - " + dataLen);
                return false;
            }

            if (contentType ==  CONTENT_TYPE__HANDSHAKE) {
                // skip content type, version, length (TLS HEADER)
                index += TLS_HEADER_LEN;

                // start of handshake
                if (index < packet.length) {
                    byte handshakeType = packet[index];
                    return handshakeType == HANDSHAKE_TYPE__CLIENT_HELLO;
                }
            }
        }

        return false;
    }

    public static boolean isClientHello(byte[] packet) {
        return isClientHello(packet, 0);
    }

    public static String extractServerNameFromClientHello(byte[] packet, int dataOffset) {
        String serverName = null;
        int index = dataOffset;
        // skip content type, version, length
        index += 5;
        // skip handshake type;
        index += 1;

        // skip length
        index += 3;

        // skip version
        index += 2;

        // skip time (4) and random bytes (28)
        index += 32;

        if (index >= packet.length) return serverName;
        //skip session id length:
        int sessionIdLength = (packet[index] & 0x000000FF);
        index += sessionIdLength + 1;

        if (index+1 >= packet.length) return serverName;
        int cyberSuitesLength = ((packet[index] << 8 & 0x0000FF00) | (packet[index+1] & 0x000000FF));
        index += cyberSuitesLength + 2;

        if (index >= packet.length) return serverName;
        int compressionMethodLength = (packet[index] & 0x000000FF);
        index += compressionMethodLength + 1;

        // sometimes, a client hello can have no extensions
        if (index+1 >= packet.length) return serverName;
        // read in extensions length that will tell how big the entire extensions list is
        int extensionsLength = ((packet[index] << 8 & 0x0000FF00) | (packet[index+1] & 0x000000FF));

        // skip extension length
        index += 2;

        int extensionsCounter = 0;
        final int SERVER_NAME = 0;

        // loop through every extension and find the server name
        while(extensionsCounter < extensionsLength && index < packet.length) {

            if (index+1 >= packet.length) break;

            int extensionType = ((packet[index] << 8 & 0x0000FF00) | (packet[index+1] & 0x000000FF));
            index+= 2;

            if (index+1 >= packet.length) break;

            int extLength = ((packet[index] << 8 & 0x0000FF00) | (packet[index+1] & 0x000000FF));
            index+= 2;

            if (index+extLength >= packet.length) break;

            if (extensionType == SERVER_NAME) {
                // read in server name and return
                //skip servername list length
                index += 2;

                int serverNameType = (packet[index] & 0x000000FF);
                index += 1;

                if (serverNameType == 0) {
                    int serverNameLength = ((packet[index] << 8 & 0x0000FF00) | (packet[index+1] & 0x000000FF));
                    index += 2;

                    // build servername
                    StringBuilder name = new StringBuilder();
                    int position = index;
                    while (index < position + serverNameLength) {
                        name.append((char) (packet[index] & 0xFF));
                        index++;
                    }
                    return name.toString();
                } else {
                    //Logg.e(TAG, "Server extension found but no server name");
                    return serverName;
                }
            }

            index += extLength;
            extensionsCounter = 4 + extLength;
        }

        //Logg.e(TAG, "extractServerNameFromClientHello: no server name found");

        return serverName;
    }

    /**
     * Extension of the original checksum calculation to compute TCP Checksum of: Pseudo header + Data
     * @param header
     * @param headerOffset
     * @param headerLen
     * @param data
     * @param dataOffset
     * @param dataLen
     * @return
     */
    public static long calculateChecksum(byte[] header, int headerOffset, int headerLen, byte[] data, int dataOffset, int dataLen) {

        long sum = 0;

        // Process pseudo-header:
        // Handle all pairs
        int i = 0;
        long current = 0;

        while (headerLen > 1) {

            // Corrected to include @Andy's edits and various comments on Stack Overflow
            current = (((header[headerOffset+i] << 8) & 0x0000FF00) | ((header[headerOffset+i + 1]) & 0x000000FF));
            sum += current;
            // 1's complement carry bit correction in 16-bits (detecting sign extension)
            if ((sum & 0xFFFF0000) > 0) {
                sum = sum & 0x0000FFFF;
                sum += 1;
            }

            i += 2;
            headerLen -= 2;
        }

        return IpDatagram.calculateIPv4Checksum(data, dataOffset, dataLen, sum);
    }
}
