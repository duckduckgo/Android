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
import java.util.Map;

/**
 * @author Hieu Le
 */
public class UDPPacket {

    public static final int UDP_HEADER_DEFAULT_LENGTH = 8; // in bytes

    /**
     * Constructs a UDPHeader object, assumes position of ByteBuffer is at beginning of header.
     * This will change the ByteBuffer's position
     * @param buffer
     * @return UDPHeader
     */
    public static UDPHeader readHeader(ByteBuffer buffer) {
        return new UDPHeader(buffer);
    }

    /**
     * Reads the destination port from the provided {@code datagram}, without changing its position.
     * @param datagram  A {@link ByteBuffer} containing an IP datagram.
     * @param offset offset of the position of {@code datagram} from the beginning of the datagram packet.
     * @param ipHeaderLength The length of the ip header in bytes
     * @return The destination port read from the UDP packet part of the IP datagram.
     */
    public static int readDestinationPort(ByteBuffer datagram, int offset, int ipHeaderLength) {
        return Unsigned.getUnsignedShort(datagram, ipHeaderLength + 2 + offset);
    }

    /**
     * Reads the transport header length byte from the provided {@code datagram}, without changing its position.
     * @param datagram  A {@link ByteBuffer} containing an IP datagram.
     * @param offset offset of the position of {@code datagram}from the beginning of the datagram packet.
     * @param ipHeaderLength The length of the ip header
     * @return the transport header length byte for UDP
     */
    public static short readTransportHeaderLength(ByteBuffer datagram, int offset, int ipHeaderLength) {
        return datagram.getShort(4 + offset + ipHeaderLength);

    }

    /**
     * Extract UDPv4 data length in byte of a given UDP packet
     * @param packet
     * @param offset Offset into the packet
     * @return
     */
    public static int extractUDPv4DataLength(byte[] packet, int offset) {
        // UDP header + data length is contained in bytes 5th and 6th after IP header
        // UDP header length is 8 bytes

        int ipHeaderLen = IpDatagram.extractIPv4HeaderLength(packet, offset);

        byte byte5th = packet[offset+ipHeaderLen+4];
        byte byte6th = packet[offset+ipHeaderLen+5];

        int totalLength =  (byte5th << 4 & 0x0000FF00) | (byte6th & 0x000000FF);

        return totalLength - UDP_HEADER_DEFAULT_LENGTH;
    }

    public static int extractUDPv4DataLength(byte[] packet) {
        return extractUDPv4DataLength(packet, 0);
    }

    /**
     * Extracts the transaction ID (TXID) of the given DNS packet
     * @param packet containing a DNS request/response
     * @return transaction ID (TXID) of the given DNS packet
     */
    public static int extractUDPv4Txid(byte[] packet) {
        // TXID resides in the first 2 bytes of data
        return ((packet[0] << 8 & 0x0000FF00) | (packet[1] & 0x000000FF));
    }

    /**
     * Parses the provided DNS data packet and fills out provided map with mappings of IP
     * address to host names. E.g., 216.58.193.196 -> www.google.com
     * @param ipToHostMap the map to add entries to
     * @param packet packet containing DNS data
     */
    public static void mapIPtoHostName(Map<String, String> ipToHostMap, byte[] packet) {
        // See RFC 1035 for a reference on how this parsing works:
        // https://tools.ietf.org/html/rfc1035#section-4.1.3
        //Logg.d("NS", "flags " + packet[2] + " " + packet[3]);

        // TODO: check flags for a correct standard response

        int numQuestions = ((packet[4] << 8 & 0x0000FF00) | (packet[5] & 0x000000FF));
        //Logg.d("NS", "qs: " + numQuestions);

        int numAnswers = ((packet[6] << 8 & 0x0000FF00) | (packet[7] & 0x000000FF));
        //Logg.d("NS", "an: " + numAnswers);

        if (numQuestions != 1 || numAnswers == 0)
            return;

        // 8, 9, 10, 11 are additional info we skip
        // TODO: assumng 1 question for now
        StringBuilder name = new StringBuilder();
        int i = 12;
        while (packet[i] != 0 && i < packet.length) {
            int labelLen = packet[i] & 0x000000FF;
            int position = i;
            i++;
            while (i <= position + labelLen) {
                name.append((char) (packet[i] & 0xFF));
                i++;
            }
            name.append(".");
        }
        // Get rid of trailing "."
        String finalName = name.length() != 0 ? name.substring(0, name.length() - 1) : null;

        // Skip name and type, +1 for being AT start of answer
        i += 5;

        // Now parse the answers:
        for (int answer = 0; answer < numAnswers; answer++) {
            byte mask = (byte) 0b11000000; // 192 as an unsigned int, -64 as signed int
            int and = ((int) mask) & ((int) packet[i]);
            if (and != -64) {
                return; //TODO: deal with non-compressed names
            }

            // Skip the offset of compressed name
            i += 2;

            // get Answer type
            int answerTypeCode = ((packet[i++] << 8 & 0x0000FF00) | (packet[i++] & 0x000000FF));

            // Skip answer CLASS (2) and TTL (4)
            i += 6;

            // get the rdata length
            int answerRDataLength = ((packet[i++] << 8 & 0x0000FF00) | (packet[i++] & 0x000000FF));

            if (answerTypeCode != 1 || answerRDataLength != 4) {
                /* Skip past answers that:
                 *      - are not of type is A (value 1, host address) Section-3.2.2 of RFC 1035
                 *      - do not have data of length 4 since we only deal with IPv4 for now
                 */

                // Skip past this answer's RDATA for the next iteration:
                i += answerRDataLength;
                continue;
            }

            // read in IP!!
            String ipAddr = IpDatagram.ipv4addressBytesToString(packet[i++], packet[i++],
                                                                packet[i++], packet[i++]);
            //Logg.d("NS", "adding: " + ipAddr + " -> " + finalName);
            ipToHostMap.put(ipAddr, finalName);
        }
    }

    /**
     * Extract IP header length in byte of a given IP packet
     * @param packet
     * @param offset Offset into the packet
     * @return
     */
    public static byte[] extractUDPv4Data(byte[] packet, int offset) {
        // Data is after ipHeader and udpHeader
        int ipHeaderLen = IpDatagram.extractIPv4HeaderLength(packet, offset);
        int udpHeaderLen = 8;

        byte[] data = new byte[packet.length - ipHeaderLen - udpHeaderLen];
        System.arraycopy(packet, offset + ipHeaderLen + udpHeaderLen, data, 0, data.length);

        return data;
    }

    public static byte[] extractUDPv4Data(byte[] packet) {
        return extractUDPv4Data(packet, 0);
    }

}
