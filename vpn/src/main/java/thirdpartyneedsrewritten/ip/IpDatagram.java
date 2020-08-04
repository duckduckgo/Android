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

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

/**
 * This class represents an IP Datagram Packet and supplies various of static methods to parse IP datagrams.
 * Note that this class only supports IPv4.
 * There is no guarantee that any of these methods will work with IPv6 datagrams.
 *
 * @author Simon Langhoff, Janus Varmarken
 */
public class IpDatagram {
    public static final int IPV4 = 4;
    public static final int IPV6 = 6;

    public static final byte ICMP = 0x01;
    public static final byte TCP = 0x06;
    public static final byte UDP = 0x11;

    public static final int IP_HEADER_PSEUDO_LENGTH = 12; // in bytes
    public static final int IP_HEADER_DEFAULT_LENGTH = 20; // in bytes
    public static final int TCP_HEADER_DEFAULT_LENGTH = 20;
    public static final int UDP_HEADER_DEFAULT_LENGTH = 8; // in bytes
    public static final int DNSPort = 53;


    // IP Datagram Header Fields.
    byte version;
    byte headerLength;
    short tos;
    int datagramLength;
    int identifier;
    byte flags;
    short fragmentationOffset;
    short ttl;
    short protocol;
    int checksum;
    InetAddress sourceIP = null;
    InetAddress destinationIP = null;
    byte[] options;

    UDPHeader udpHeader;
    TCPHeader tcpHeader;

    /**
     * Creates an instance by parsing the supplied datagram packet. Assumes that position of the {@link ByteBuffer} is at the start of the
     * datagram packet. Creating an instance of this class is useful when debugging datagram messages or can be used to
     * have a object representation of a datagram instead of an array of bytes.
     * @param datagram The datagram buffer.
     */
    public IpDatagram(ByteBuffer datagram) {
        // Simply try to read all the header fields sequentially from the ByteBuffer.
        byte firstByte = datagram.get();
        version = (byte) ((firstByte >> 4) & (byte) 0x0F);
        headerLength = (byte) (firstByte & 0x0F);

        tos = Unsigned.getUnsignedByte(datagram);
        datagramLength = Unsigned.getUnsignedShort(datagram);
        identifier = Unsigned.getUnsignedShort(datagram);
        int flagFragmentationBytes = Unsigned.getUnsignedShort(datagram); // TODO fragmentation bytes != a short, smaller.
        ttl = Unsigned.getUnsignedByte(datagram);
        protocol = Unsigned.getUnsignedByte(datagram);
        checksum = Unsigned.getUnsignedShort(datagram);

        byte[] sIP = new byte[4];
        byte[] dIP = new byte[4];
        datagram.get(sIP);
        datagram.get(dIP);
        try {
            sourceIP = Inet4Address.getByAddress(sIP);
            destinationIP = Inet4Address.getByAddress(dIP);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        // Read all Options
        if (headerLength - 5 == 0) { // Check if any options
            options = null;
        } else {  // Initialize array size to fit options
            options = new byte[(headerLength - 5) * 4];
            datagram.get(options);
        }

        switch (protocol) {
            case TCP:
                handleTCP(datagram);
                break;
            case UDP:
                handleUDP(datagram);
                break;
            default:
                System.out.println("NON TCP/UDP datagram");
                break;
        }
    }

    /// Getters for IP header fields
    public byte getVersion() {
        return version;
    }

    /**
     * Returns the number of 32-bit words in the IP datagram header.
     * Minimum length of a valid datagram is 5 words (20 bytes) (no options), maximum length is 15 words (60 bytes)
     * @return The number of 32-bit words.
     */
    public byte getHeaderLength() {
        return headerLength;
    }

    public short getTos() {
        return tos;
    }

    public int getDatagramLength() {
        return datagramLength;
    }

    public int getIdentifier() {
        return identifier;
    }

    public byte getFlags() {
        return flags;
    }

    public short getFragmentationOffset() {
        return fragmentationOffset;
    }

    public short getTtl() {
        return ttl;
    }

    public short getProtocol() {
        return protocol;
    }

    public int getChecksum() {
        return checksum;
    }

    public InetAddress getSourceIP() {
        return sourceIP;
    }

    public InetAddress getDestinationIP() {
        return destinationIP;
    }

    public TCPHeader getTcpHeader() { return tcpHeader; }

    public UDPHeader getUdpHeader() { return udpHeader; }

    private void handleTCP(ByteBuffer data) {
        tcpHeader = TCPPacket.readHeader(data);
    }

    private void handleUDP(ByteBuffer data) {
        // UDP
       udpHeader = UDPPacket.readHeader(data);

       // udpHeader = new UDPHeader(srcPort, destinationPort, length, checksum);
    }

    /**
     * Helper method to read only the Destination IP without inspecting other parts of the buffer.
     * @param buffer The buffer containing a IP Datagram
     * @return A String representing the IP address created by inspecting the Destination IP header field of the datagram
     */
    public static String readDestinationIP(ByteBuffer buffer) {
        return readDestinationIP(buffer, 0);
    }

    /**
     * Helper method to read only the Destination IP without inspecting other parts of the buffer.
     * @param buffer The buffer containing a IP Datagram
     * @param offset offset of the position of {@code datagram}from the beginning of the datagram packet.
     * @return A String representing the IP address.
     */
    public static String readDestinationIP(ByteBuffer buffer, int offset) {
//        int dIP = buffer.getInt(16 + offset);
//        return getIPString(dIP);

        byte[] addressBytes = new byte[4];
        for(int i = 0; i < addressBytes.length; i++) {
            addressBytes[i] = buffer.get(16 + offset + i);
        }
        return ipv4addressBytesToString(addressBytes);
    }

    /**
     * Helper method for reading only the destination IP without inspecting other parts of the packet.
     * @param packet A byte[] packet containing an IP Datagram.
     * @return An {@link String} representation of the destination IP header field of the IP datagram.
     */
    public static String readDestinationIP(byte[] packet) {
        return readDestinationIP(packet, 0);
    }

    /**
     * Helper method for reading only the destination IP without inspecting other parts of the packet.
     * @param packet A packet containing an IP Datagram (and possibly other prepended data).
     * @param offset If {@code packet} contains prepended data, this offset should specify at what index the actual IP datagram begins.
     * @return An {@link String} representation of the destination IP header field of the IP datagram.
     */
    public static String readDestinationIP(byte[] packet, int offset) {
        return ipv4addressBytesToString(IpDatagram.extractIPv4DestinationIPArray(packet, offset));
    }

    /**
     * Helper method for reading only the destination IP without inspecting other parts of the packet.
     * @param packet A packet containing an IP Datagram (and possibly other prepended data).
     * @param offset If {@code packet} contains prepended data, this offset should specify at what index the actual IP datagram begins.
     * @return An {@link byte[]} representation of the destination IP header field of the IP datagram.
     */
    public static byte[] extractIPv4DestinationIPArray(byte[] packet, int offset) {
        byte[] destinationIP = {packet[offset+16], packet[offset+17], packet[offset+18], packet[offset+19]};
        return destinationIP;
    }

    /**
     * Helper method for reading only the destination IP without inspecting other parts of the packet.
     * @param packet A packet containing an IP Datagram (and possibly other prepended data).
     * @return An {@link byte[]} representation of the destination IP header field of the IP datagram.
     */
    public static byte[] extractIPv4DestinationIPArray(byte[] packet) {
        return extractIPv4DestinationIPArray(packet, 0);
    }

    /**
     * Helper method to read only the Source IP without inspecting other parts of the buffer.
     * @param buffer The buffer containing an IP Datagram
     * @return A String representing the IP created by inspecting the Source IP header field of the datagram
     * @throws UnknownHostException in case the Source IP header field cannot be resolved to a valid host.
     */
    public static String readSourceIP(ByteBuffer buffer) throws UnknownHostException {
        return readSourceIP(buffer, 0);
    }

    /**
     * Helper method to read only the Source IP without inspecting other parts of the buffer.
     * @param buffer The buffer containing an IP Datagram
     * @param offset offset of the position of {@code datagram}from the beginning of the datagram packet.
     * @return A String representing the IP address created by inspecting the source IP header field of the datagram
     * @throws UnknownHostException in case the Source IP header field cannot be resolved to a valid host.
     */
    public static String readSourceIP(ByteBuffer buffer, int offset) throws UnknownHostException {
        byte[] addressBytes = new byte[4];
        for(int i = 0; i < addressBytes.length; i++) {
            addressBytes[i] = buffer.get(12 + offset + i);
        }
        return ipv4addressBytesToString(addressBytes);
    }

    /**
     * Helper method for reading only the Source IP without inspecting other parts of the packet.
     * @param packet A byte[] containing an IP Datagram.
     * @return A {@link String} representation of the source IP header field of the IP datagram.
     */
    public static String readSourceIP(byte[] packet) {
        return readSourceIP(packet, 0);
    }

    /**
     * Helper method for reading only the Source IP without inspecting other parts of the packet.
     * @param packet A packet containing an IP Datagram (and possibly other prepended data).
     * @param offset If {@code packet} contains prepended data, this offset should specify at what index the actual IP datagram begins.
     * @return A {@link String} representation of the source IP header field of the IP datagram.
     */
    public static String readSourceIP(byte[] packet, int offset) {
        return ipv4addressBytesToString(IpDatagram.extractIPv4SourceIPArray(packet, offset));
    }

    /**
     * Extract source IP (array of 4 bytes) of an IPv4 packet
     * @param packet
     * @param offset Offset into the packet
     * @return A {@link byte[]} representation of the source IP header field of the IP datagram.
     */
    public static byte[] extractIPv4SourceIPArray(byte[] packet, int offset) {
        // Destination IP is from byte 12 to byte 15
        byte[] sourceIP = {packet[offset+12], packet[offset+13], packet[offset+14], packet[offset+15]};
        return sourceIP;
    }

    /**
     * Extract source IP (array of 4 bytes) of an IPv4 packet without an offset
     * @param packet
     * @return A {@link byte[]} representation of the source IP header field of the IP datagram.
     */
    public static byte[] extractIPv4SourceIPArray(byte[] packet) {
        return extractIPv4SourceIPArray(packet, 0);
    }

    /**
     * Helper method for reading only the source port without inspecting other parts of the packet buffer.
     * @param buffer An IP Datagram wrapping a TCP/UDP packet.
     * @return The source port read from the TCP/UDP packet part of the IP datagram.
     */
    public static int readSourcePort(ByteBuffer buffer) {
        return readSourcePort(buffer, 0);
    }

    /**
     * Helper method for reading only the source port without inspecting other parts of the packet buffer.
     * @param buffer  An IP Datagram wrapping a TCP/UDP packet.
     * @param offset The index at which the actual IP datagram begins.
     * @return The source port read from the TCP/UDP packet part of the IP datagram.
     */
    public static int readSourcePort(ByteBuffer buffer, int offset){
        int len = readIPHeaderLength(buffer, offset) * 4;
        return Unsigned.getUnsignedShort(buffer, len + offset);
    }

    /**
     * Helper method for reading only the source port without inspecting other parts of the packet buffer.
     * @param packet An IP Datagram wrapping a TCP/UDP packet.
     * @return The source port read from the TCP/UDP packet part of the IP datagram.
     */
    public static int readSourcePort(byte[] packet) {
        return readSourcePort(packet, 0);
    }

    /**
     * Helper method for reading only the source port without inspecting other parts of the packet.
     * @param packet A packet containing an IP Datagram wrapping a TCP/UDP packet (prepended data, which is not part of the IP datagram, <em>is</em> allowed, see {@code offset}).
     * @param offset If {@code packet} contains prepended data, this offset should specify at what index the actual IP datagram begins.
     * @return The source port read from the TCP/UDP packet part of the IP datagram.
     */
    public static int readSourcePort(byte[] packet, int offset) {
        // Calculate the IHL in order to be able to later calculate the index where the TCP/UDP header begins.
        // Source port is the first two bytes after IP header
        int ipHeaderLen = IpDatagram.extractIPv4HeaderLength(packet, offset);
        int sourcePort = ((packet[offset+ipHeaderLen] << 8 & 0x0000FF00) | (packet[offset+ipHeaderLen+1] & 0x000000FF));
        return sourcePort;
    }

    /**
     * Helper method for reading only the destination port without inspecting other parts of the packet buffer.
     * @param buffer A buffer containing an IP Datagram wrapping a TCP/UDP packet.
     * @return The destination port read from the TCP/UDP packet part of the IP datagram.
     */
    public static int readDestinationPort(ByteBuffer buffer){
        return readDestinationPort(buffer, 0);
    }

    /**
     * Helper method for reading only the destination port without inspecting other parts of the packet buffer.
     * @param buffer A buffer containing an IP Datagram wrapping a TCP/UDP packet.
     * @param offset The index at which the actual IP datagram begins.
     * @return  The destination port read from the TCP/UDP packet part of the IP datagram.
     */
    public static int readDestinationPort(ByteBuffer buffer, int offset){
        int len = wordsToBytes(readIPHeaderLength(buffer, offset));
        return Unsigned.getUnsignedShort(buffer, len + 2 + offset);
        // TODO test this
    }

    /**
     * Helper method for reading only the destination port without inspecting other parts of the packet buffer.
     * @param packet A byte array containing an IP Datagram wrapping a TCP/UDP packet.
     * @return The destination port read from the TCP/UDP packet part of the IP datagram.
     */
    public static int readDestinationPort(byte[] packet) {
        return readDestinationPort(packet, 0);
    }

    /**
     * Helper method for reading only the destination port without inspecting other parts of the packet.
     * @param packet A packet containing an IP Datagram wrapping a TCP/UDP packet (prepended data, which is not part of the IP datagram, <em>is</em> allowed, see {@code offset}).
     * @param offset The index at which the actual IP datagram begins.
     * @return The destination port read from the TCP/UDP packet part of the IP datagram.
     */
    public static int readDestinationPort(byte[] packet, int offset) {
        // Destination port is the second two bytes after IP header
        int ipHeaderLen = extractIPv4HeaderLength(packet, offset);
        int destinationPort = ((packet[offset+ipHeaderLen+2] << 8 & 0x0000FF00) | (packet[offset+ipHeaderLen+3] & 0x000000FF));
        // Logg.e("Extractor", "DestPort: FirstByte=" + packet[ipHeaderLen+2] + " SecondByte=" + packet[ipHeaderLen+3] + " Port=" + destinationPort);
        return destinationPort;
    }

    /**
     * Reads the destination port from the provided {@code datagram}, without changing its position.
     * @param datagram  A {@link ByteBuffer} containing an IP datagram.
     * @param protocol The transport protocol byte from the IP datagram header.
     * @return The destination port read from the TCP/UDP packet part of the IP datagram.
     */
    public static int readDestinationPort(ByteBuffer datagram, byte protocol) {
        return readDestinationPort(datagram, protocol, 0);
    }

    /**
     * Reads the destination port from the provided {@code datagram}, without changing its position.
     * @param datagram  A {@link ByteBuffer} containing an IP datagram.
     * @param protocol The transport protocol byte from the IP datagram header.
     * @param offset offset of the position of {@code datagram}from the beginning of the datagram packet.
     * @return The destination port read from the TCP/UDP packet part of the IP datagram.
     */
    public static int readDestinationPort(ByteBuffer datagram, byte protocol, int offset) {
        // Convert Length header to byte length
        int ipHeaderLength = wordsToBytes(IpDatagram.readIPHeaderLength(datagram, offset)); // 4 bytes per 32 bit header section
        int port = -1;
        // Different protocols have different header length positions
        switch (protocol){
            case TCP :
                port = TCPPacket.readDestinationPort(datagram, offset, ipHeaderLength);
                break;
            case UDP :
                port = UDPPacket.readDestinationPort(datagram, offset, ipHeaderLength);
                break;
            default:
                System.out.print("Unexpected Protocol : " + protocol);
                throw new IllegalStateException();
        }
        return port;
    }

    /**
     * Calculates if the  {@code checksum}, and the {@code message} is compatible.
     * That is, if the {@code checksum} is valid for the specified {@code message}.
     * @param message The datagram to calculate the {@code checksum} off of
     * @param checksum The {@code checksum} of the supplied {@code message}
     * @return True if the {@code checksum} is valid for the supplied {@code message}
     */
    public static boolean isChecksumValid(byte[] message, long checksum){
        byte[] checksumBytes = new byte[8];
        checksumBytes[0] = (byte)(checksum >>> 56);
        checksumBytes[1] = (byte)(checksum >>> 48);
        checksumBytes[2] = (byte)(checksum >>> 40);
        checksumBytes[3] = (byte)(checksum >>> 32);
        checksumBytes[4] = (byte)(checksum >>> 24);
        checksumBytes[5] = (byte)(checksum >>> 16);
        checksumBytes[6] = (byte)(checksum >>>  8);
        checksumBytes[7] = (byte)(checksum >>>  0);

        byte[] combined = new byte[message.length + checksumBytes.length];

        System.arraycopy(message, 0, combined, 0, message.length);
        System.arraycopy(checksumBytes, 0, combined, message.length, checksumBytes.length);

        // TODO: HLE checksum changed from int to long. does this still work ?
        return calculateIPv4Checksum(combined) == 0x00000000;
    }

    /**
     * Calculates if the checksum of the supplied datagram is valid. This assumes an IP Datagram message
     * @param datagram a IP datagram.
     * @return true if the checksum is valid. false otherwise.
     */
    public static boolean isChecksumValid(ByteBuffer datagram){
        int checksum = readIPChecksum(datagram);
        byte[] checksumBytes = ByteBuffer.allocate(4).putInt(checksum).array();

        int msgLength = datagram.array().length;

        byte[] combined = new byte[msgLength + checksumBytes.length];

        System.arraycopy(datagram.array(), 0, combined, 0, msgLength);
        System.arraycopy(checksumBytes, 0, combined, msgLength, checksumBytes.length);

        // TODO: HLE checksum changed from int to long. does this still work ?
        return calculateIPv4Checksum(datagram.array()) == 0x00000000;
    }

    /**
     * Calculates the checksum of a IP datagram.
     * We assume there is no offset and the length is the datagram's length
     * @param datagram a IP datagram.
     * @return the calculated checksum for the datagram.
     */
    public static long calculateIPv4Checksum(byte[] datagram) {
        return calculateIPv4Checksum(datagram, 0, datagram.length);
    }

    /**
     * Calculates the checksum of a IP datagram.
     * @param datagram The message
     * @param offset Offset into the packet
     * @param length IP header len in bytes
     * @return The checksum
     */
    public static long calculateIPv4Checksum(byte[] datagram, int offset, int length) {

        return IpDatagram.calculateIPv4Checksum(datagram, offset, length, 0);
    }

    /**
     * Code from: http://stackoverflow.com/questions/4113890/how-to-calculate-the-internet-checksum-from-a-byte-in-java
     *
     * Calculate the Internet Checksum of a buffer (RFC 1071 - http://www.faqs.org/rfcs/rfc1071.html)
     * Algorithm is
     * 1) apply a 16-bit 1's complement sum over all octets (adjacent 8-bit pairs [A,B], final odd length is [A,0])
     * 2) apply 1's complement to this final sum
     *
     * Notes:
     * 1's complement is bitwise NOT of positive value.
     * Ensure that any carry bits are added back to avoid off-by-one errors
     *
     *
     * @param datagram The message
     * @param offset Offset into the packet
     * @param length IP header len in bytes
     * @param sum the sum start value
     * @return The checksum
     */
    public static long calculateIPv4Checksum(byte[] datagram, int offset, int length, long sum) {

        int i = 0;

        long data;

        if (datagram != null) {
            // Handle all pairs
            while (length > 1) {

                // Corrected to include @Andy's edits and various comments on Stack Overflow
                data = (((datagram[offset+i] << 8) & 0x0000FF00) | ((datagram[offset+i + 1]) & 0x000000FF));
                sum += data;
                // 1's complement carry bit correction in 16-bits (detecting sign extension)
                if ((sum & 0xFFFF0000) > 0) {
                    sum = sum & 0x0000FFFF;
                    sum += 1;
                }

                i += 2;
                length -= 2;
            }

            // Handle remaining byte in odd length buffers
            if (length > 0) {
                // Corrected to include @Andy's edits and various comments on Stack Overflow
                sum += (datagram[offset+i] << 8 & 0x0000FF00);
                // 1's complement carry bit correction in 16-bits (detecting sign extension)
                if ((sum & 0xFFFF0000) > 0) {
                    sum = sum & 0x0000FFFF;
                    sum += 1;
                }
            }
        }

        // Final 1's complement value correction to 16-bits
        sum = ~sum;
        sum = sum & 0x0000FFFF;
        return sum;
    }

    /*
     * TODO all read(Transport)Protocol methods should possibly be updated to return a short
     * (as IP use unsigned bytes and java uses signed)
     * See http://www.iana.org/assignments/protocol-numbers/protocol-numbers.xhtml
     */

    /**
     * Reads the protocol byte from the provided {@code datagram}, without changing its position.
     * @param datagram A {@link ByteBuffer} containing an IP datagram.
     * @return The protocol byte from the IP datagram.
     */
    public static byte readProtocol(ByteBuffer datagram) {
        return readProtocol(datagram, 0);
    }

    /**
     * Reads the protocol byte from the provided {@code datagram}, without changing its position.
     * @param datagram A {@link ByteBuffer} containing an IP datagram.
     * @param offset offset of the position of {@code datagram}from the beginning of the datagram packet.
     * @return The protocol byte from the IP datagram.
     */
    public static byte readProtocol(ByteBuffer datagram, int offset) {
        return datagram.get(9 + offset);
    }

    /**
     * Reads the protocol byte from the provided {@code datagram}.
     * @param datagram A {@code byte[]} containing an IP datagram.
     * @return The protocol byte from the IP datagram.
     */
    public static byte readProtocol(byte[] datagram) {
        return datagram[9];
    }

    /**
     * <p>
     *      Reads the protocol header field of an IP datagram. The protocol number identifies what transport layer
     *      protocol is used for the payload section of the IP datagram.
     * </p>
     * <p>
     *     This method is equivalent to calling {@code readTransportProtocol(packetBuffer, 0)}.
     * </p>
     * @param packet A packet containing an IP datagram (starting at index 0).
     * @return The protocol number of the transport protocol used for the payload section of the IP datagram.
     */
    public static short readTransportProtocol(byte[] packet) {
        return readTransportProtocol(packet, 0);
    }

    /**
     * <p>
     *      Reads the protocol header field of an IP datagram. The protocol number identifies what transport layer
     *      protocol is used for the payload section of the IP datagram.
     * </p>
     * @param packet A packet containing an IP datagram (starting at index {@code offset}).
     * @param offset An offset specifying the index in {@code packet} where the IP datagram begins.
     * @return The protocol number of the transport protocol used for the payload section of the IP datagram.
     */
    public static short readTransportProtocol(byte[] packet, int offset) {
        return Unsigned.getUnsignedByte(ByteBuffer.wrap(new byte[]{packet[9 + offset]}));
    }

    /**
     * <p>
     *      Reads the protocol header field of an IP datagram. The protocol number identifies what transport layer
     *      protocol is used for the payload section of the IP datagram.
     * </p>
     * @param packet A buffer containing an IP datagram
     * @return The protocol number of the transport protocol used for the payload section of the IP datagram.
     */
    public static short readTransportProtocol(ByteBuffer packet) {
        return  Unsigned.getUnsignedByte(packet, 9);
    }

    /**
     * <p>
     *      Utility method for inspecting an IP datagram, asking if the payload section is sent using a given
     *      transport layer protocol.
     * </p>
     * <p>
     *     This method is equivalent to calling {@code isTransportProtocolOfType(expectedProtocol, packetBuffer, 0)}
     * </p>
     * @param expectedProtocol The expected transport layer protocol.
     * @param packet A packet containing an IP datagram (starting at index 0).
     * @return {@code true} if the value of the protocol header field of the IP datagram matches the protocol number of
     *              of {@code expectedProtocol}, false otherwise.
     */
    public static boolean isTransportProtocolOfType(Protocol expectedProtocol, byte[] packet) {
        return isTransportProtocolOfType(expectedProtocol, packet, 0);
    }

    /**
     * Utility method for inspecting an IP datagram, asking if the payload section is sent using a given
     * transport layer protocol.
     * @param expectedProtocol The expected transport layer protocol.
     * @param packet A packet containing an IP datagram (starting at index {@code offset}).
     * @param offset An offset specifying the index in {@code packet} where the IP datagram begins.
     * @return {@code true} if the value of the protocol header field of the IP datagram matches the protocol number of
     *              of {@code expectedProtocol}, false otherwise.
     */
    public static boolean isTransportProtocolOfType(Protocol expectedProtocol, byte[] packet, int offset) {
        short protocolNumber = readTransportProtocol(packet, offset);
        return protocolNumber == expectedProtocol.getProtocolNumber();
    }

    /**
     * Reads the IP header length byte from the provided {@code datagram}, without changing its position.
     * The IP header length denotes the number of 32 bits words in the header. (IHL * 4 = bytes in header)
     * @param datagram A {@link ByteBuffer} containing an IP datagram.
     * @return The ip header length byte.
     */
    public static byte readIPHeaderLength(ByteBuffer datagram) {
        return readIPHeaderLength(datagram, 0);
    }

    /**
     * Reads the IP header length byte from the provided {@code datagram}, without changing its position.
     * The IP header length denotes the number of 32 bits words in the header. (IHL * 4 = bytes in header)
     * @param datagram A {@link ByteBuffer} containing an IP datagram.
     * @param offset offset of the position of {@code datagram}from the beginning of the datagram packet.
     * @return The ip header length byte.
     */
    public static byte readIPHeaderLength(ByteBuffer datagram, int offset) {
        byte firstByte = datagram.get(0 + offset);
        // Retrieve the original numbers
        return (byte) (firstByte & (byte) 0x0F);
    }

    /**
     * <p>
     *      Reads the IP header length (IHL) field from the provided datagram, without modifying the datagram.
     * </p>
     * <p>
     *      The IP header length denotes the number of 32 bits words in the header.
     *      As such, it is given that IHL * 4 equals the number of bytes making up the IP header.
     *      See <a href="http://en.wikipedia.org/wiki/IPv4#Header">Wikipedia</a> for additional details.
     * </p>
     * @param datagram A {@code byte[]} containing an IP datagram.
     * @return The ip header length field value stored in a {@code byte}, i.e. the number of 32-bit words in the IP datagram header.
     */
    public static byte readIPHeaderLength(byte[] datagram) {
        return readIPHeaderLength(datagram, 0);
    }

    /**
     * <p>
     *      Reads the IP header length (IHL) field from the provided datagram, without modifying the datagram.
     * </p>
     * <p>
     *      The IP header length denotes the number of 32-bit words in the header.
     *      As such, it is given that IHL * 4 equals the number of bytes making up the IP header.
     *      See <a href="http://en.wikipedia.org/wiki/IPv4#Header">Wikipedia</a> for additional details.
     * </p>
     * @param datagram A {@code byte[]} containing an IP datagram (and possibly other prepended data).
     * @param offset If {@code datagram} contains prepended data, this offset should specify at what index the actual IP datagram begins.
     * @return The ip header length field value stored in a {@code byte}, i.e. the number of 32-bit words in the IP datagram header.
     */
    public static byte readIPHeaderLength(byte[] datagram, int offset) {
        byte firstByte = datagram[0 + offset];
        byte mask = (byte) 0b00001111;
        byte ihl = (byte) (firstByte & mask);
        return ihl;
    }

    /**
     * Extract IP header length in byte of a given IP packet
     * @param packet
     * @param offset Offset into the packet
     * @return
     */
    public static int extractIPv4HeaderLength(byte[] packet, int offset) {
        return wordsToBytes(IpDatagram.readIPHeaderLength(packet, offset));
    }

    public static int extractIPv4HeaderLength(byte[] packet) {
        return extractIPv4HeaderLength(packet, 0);
    }

    /**
     * Reads the transport header length byte from the provided {@code datagram}, without changing its position.
     * @param datagram  A {@link ByteBuffer} containing an IP datagram.
     * @param ipHeaderLength The IP datagram header length byte.
     * @param protocol The transport protocol byte from the IP datagram header.
     * @return the transport header length byte.
     */
    public static short readTransportHeaderLength(ByteBuffer datagram, byte ipHeaderLength, byte protocol) {
        return readTransportHeaderLength(datagram, ipHeaderLength, protocol, 0);
    }

    /**
     * Reads the transport header length byte from the provided {@code datagram}, without changing its position.
     * @param datagram  A {@link ByteBuffer} containing an IP datagram.
     * @param ipHeaderLength The IP datagram header length byte.
     * @param protocol The Internet protocol used
     * @param offset offset of the position of {@code datagram}from the beginning of the datagram packet.
     * @return the transport header length byte.
     */
    public static short readTransportHeaderLength(ByteBuffer datagram, byte ipHeaderLength, byte protocol, int offset) {
        // Convert Length header to byte length
        int ipHeaderEndPosition = wordsToBytes(ipHeaderLength); // 4 bytes per 32 bit header section
        // Different protocols have different header length positions
        // TODO: cleanup casts / return value
        switch (protocol){
            case TCP :
                return TCPPacket.readTransportHeaderLength(datagram, offset, ipHeaderEndPosition);
            case UDP :
                return UDPPacket.readTransportHeaderLength(datagram, offset, ipHeaderEndPosition);
            default:
                System.out.print("Unexpected Protocol : " + protocol);
                throw new IllegalStateException();
        }
    }



    /**
     * Reads the datagram length header field of the supplied {@link ByteBuffer}, without advancing its position.
     * @param datagram The {@link ByteBuffer} containing the IP Datagram.
     * @return the length of the datagram packet.
     */
    public static int readDatagramLength(ByteBuffer datagram){
        return readDatagramLength(datagram, 0);
    }

    /**
     * Reads the datagram length header field of the supplied {@link ByteBuffer}, without advancing its position.
     * @param datagram The {@link ByteBuffer} containing the IP Datagram.
     * @param offset offset of the position of {@code datagram}from the beginning of the datagram packet.
     * @return the length of the datagram packet.
     */
    public static int readDatagramLength(ByteBuffer datagram, int offset){
        //return (bb.getShort(position) & 0xffff);
        return Unsigned.getUnsignedShort(datagram, 2 + offset);
    }

    /**
     * Reads the datagram length header field of the supplied {@code byte[]}.
     * @param datagram The {@code byte[]} containing the IP Datagram.
     * @return the length of the datagram packet.
     */
    public static int readDatagramLength(byte[] datagram){
        short len = (short)( ((datagram[2] & 0xFF) << 8) | (datagram[2+1] & 0xFF) );
        return len;
    }

    /**
     * Reads the IP header checksum header field of the supplied {@link ByteBuffer}, without advancing its position.
     * @param datagram The {@link ByteBuffer} containing the IP Datagram.
     * @return the checksum.
     */
    public static int readIPChecksum(ByteBuffer datagram){
        return readIPChecksum(datagram, 0);
    }

    /**
     * Reads the IP header checksum header field of the supplied {@link ByteBuffer}, without advancing its position.
     * @param datagram The {@link ByteBuffer} containing the IP Datagram.
     * @param offset offset of the position of {@code datagram}from the beginning of the datagram packet.
     * @return the checksum.
     */
    public static int readIPChecksum(ByteBuffer datagram, int offset){
        return Unsigned.getUnsignedShort(datagram, 10 + offset);
    }

    /**
     * Reads the datagram length header field of the supplied {@link ByteBuffer}, without advancing its position.
     * @param datagram The {@link ByteBuffer} containing the IP Datagram.
     * @return The IP Version this packet belongs to.
     */
    public static byte readIPVersion(ByteBuffer datagram) {
        return readIPVersion(datagram, 0);
    }

    /**
     * Reads the datagram length header field of the supplied {@link ByteBuffer}, without advancing its position.
     * @param datagram The {@link ByteBuffer} containing the IP Datagram.
     * @param offset offset of the position of {@code datagram}from the beginning of the datagram packet.
     * @return The IP Version this packet belongs to.
     */
    public static byte readIPVersion(ByteBuffer datagram, int offset) {
        byte firstByte = datagram.get(0 + offset);
        return (byte) ((firstByte >> 4) & (byte) 0x0F);
    }


    /**
     * Extract IP version of a given IP packet
     * @param packet
     * @param offset Offset into the packet
     * @return
     */
    public static int extractIPVersion(byte[] packet, int offset) {
        // IP version is contained in the first 4 bits
        // 0110 = IPv6
        // 0100 = IPv4
        //
        // Using mask = 11110000
        // 0110XXXX & 11110000 = 01100000 = 96 (IPv6)
        // 0100XXXX & 11110000 = 01100000 = 64 (IPv4)
        byte firstByte = packet[offset+0];
        byte mask = (byte) 0b11110000;
        if ( (int) ((int) firstByte & (int) mask) == 96) {
            return IPV6;
        } else if ( (int) ((int) firstByte & (int) mask) == 64) {
            return IPV4;
        } else {
            return -1; // Error
        }
    }

    public static int extractIPVersion(byte[] packet) {
        return extractIPVersion(packet, 0);
    }


    /**
     * Extract source IP of an IPv4 packet
     * @param packet
     * @param offset Offset into the packet
     * @return
     */
    public static int extractIPv4SourceIP(byte[] packet, int offset) {
        // Destination IP is from byte 12 to byte 15
        int sourceIP = (
                (packet[offset+12] << 24 & 0xFF000000) |
                        (packet[offset+13] << 16 & 0x00FF0000) |
                        (packet[offset+14] << 8 & 0x0000FF00) |
                        (packet[offset+15] & 0x000000FF));
        return sourceIP;
    }

    public static int extractIPv4SourceIP(byte[] packet) {
        return extractIPv4SourceIP(packet,0);
    }

    /**
     * Extract destination IP of an IPv4 packet
     * @param packet
     * @param offset Offset into the packet
     * @return
     */
    public static int extractIPv4DestinationIP(byte[] packet, int offset) {
        // Destination IP is from byte 16 to byte 19
        int destinationIP = (
                (packet[offset+16] << 24 & 0xFF000000) |
                        (packet[offset+17] << 16 & 0x00FF0000) |
                        (packet[offset+18] << 8 & 0x0000FF00) |
                        (packet[offset+19] & 0x000000FF));
        // Logg.e("Extractor", "DestPort: FirstByte=" + packet[ipHeaderLen+2] + " SecondByte=" + packet[ipHeaderLen+3] + " Port=" + destinationPort);
        return destinationIP;
    }

    public static int extractIPv4DestinationIP(byte[] packet) {
        return extractIPv4DestinationIP(packet,0);
    }

    /**
     * Convert a IPv4 byte array representation of an IP to int.
     * @param ipArray
     * @return
     */
    public static int convertIPv4IPArrayToInt(byte[] ipArray) {
        int ip = (
                (ipArray[0] << 24 & 0xFF000000) |
                        (ipArray[1] << 16 & 0x00FF0000) |
                        (ipArray[2] << 8 & 0x0000FF00) |
                        (ipArray[3] & 0x000000FF));
        return ip;
    }

    /**
     * Calculates a byte count from a given word count.
     * @param wordCount The number of words for which the corresponding number of bytes is to be calculated.
     * @return The byte count corresponding to the given word count.
     */
    public static int wordsToBytes(int wordCount) {
        return wordCount * 4;
    }

    /**
     * Converts a {@code byte[]} representation of an IPv4 address into its string representation.
     * @param ipBytes The {@code byte[]} representation of an IPv4 address. Should contain exactly four bytes.
     * @return The human-readable string representation of {@code ipBytes}.
     */
    public static String ipv4addressBytesToString(byte[] ipBytes) {
        assert ipBytes.length == 4;

        return ipv4addressBytesToString(ipBytes[0], ipBytes[1], ipBytes[2], ipBytes[3]);
    }

    /**
     * Converts the provided bytes of an IPv4 address into a string representation.
     * @param first first byte of the IP address
     * @param second second byte of the IP address
     * @param third third byte of the IP address
     * @param fourth fourth byte of the IP address
     * @return The human-readable string representation of the IPv4 address.
     */
    public static String ipv4addressBytesToString(byte first, byte second, byte third, byte fourth) {
        Integer firstInt = first & 0xFF;
        Integer secondInt = second & 0xFF;
        Integer thirdInt = third & 0xFF;
        Integer fourthInt = fourth & 0xFF;

        return firstInt.toString() + "." + secondInt.toString()
                + "." + thirdInt.toString() + "." + fourthInt.toString();
    }


}

