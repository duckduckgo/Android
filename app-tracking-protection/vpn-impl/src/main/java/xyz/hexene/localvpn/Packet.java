/*
 * Copyright (c) 2020 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.hexene.localvpn;

import static xyz.hexene.localvpn.ByteBufferPool.BUFFER_SIZE;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

/** Representation of an IP Packet */
// TODO: Reduce public mutability
public class Packet {

    //    static final int IP4_HEADER_SIZE = 20;
    public static final int TCP_HEADER_SIZE = 20;
    public static final int UDP_HEADER_SIZE = 8;
    static final int TCP_OPTION_TYPE_MSS = 0x02;
    static final int TCP_OPTION_MSS_SIZE = 0x04;
    static final int TCP_MAX_SEGMENT_SIZE = BUFFER_SIZE - 40;

    private IP4Header ip4Header;
    private final IP6Header ip6Header;
    public TCPHeader tcpHeader;
    public UDPHeader udpHeader;
    public ByteBuffer backingBuffer;

    private boolean isTCP;
    private boolean isUDP;

    public Packet(ByteBuffer buffer) throws UnknownHostException {
        buffer.mark();

        this.ip4Header = new IP4Header(buffer);
        TransportProtocol protocol;
        if (ip4Header.getVersion() == 4) {
            this.ip6Header = null;
            protocol = ip4Header.getProtocol();
        } else if (this.ip4Header.getVersion() == 6) {
            this.ip4Header = null;
            buffer.reset();
            this.ip6Header = new IP6Header(buffer);
            protocol = ip6Header.getProtocol();
        } else {
            // There's no proper net exception
            throw new UnknownHostException("Wrong IP version");
        }

        if (protocol == TransportProtocol.TCP) {
            this.tcpHeader = new TCPHeader(buffer);
            this.isTCP = true;
        } else if (protocol == TransportProtocol.UDP) {
            this.udpHeader = new UDPHeader(buffer);
            this.isUDP = true;
        }
        this.backingBuffer = buffer;
    }

    public IPHeader getIpHeader() {
        if (this.ip4Header == null) return this.ip6Header;

        return this.ip4Header;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Packet{");
        sb.append("ip4Header=").append(ip4Header);
        if (isTCP) sb.append(", tcpHeader=").append(tcpHeader);
        else if (isUDP) sb.append(", udpHeader=").append(udpHeader);
        sb.append(", payloadSize=").append(backingBuffer.limit() - backingBuffer.position());
        sb.append('}');
        return sb.toString();
    }

    public boolean isTCP() {
        return isTCP;
    }

    public boolean isUDP() {
        return isUDP;
    }

    public void swapSourceAndDestination() {
        InetAddress newSourceAddress = getIpHeader().getDestinationAddress();
        getIpHeader().setDestinationAddress(getIpHeader().getSourceAddress());
        getIpHeader().setSourceAddress(newSourceAddress);

        if (isUDP) {
            int newSourcePort = udpHeader.destinationPort;
            udpHeader.destinationPort = udpHeader.sourcePort;
            udpHeader.sourcePort = newSourcePort;
        } else if (isTCP) {
            int newSourcePort = tcpHeader.destinationPort;
            tcpHeader.destinationPort = tcpHeader.sourcePort;
            tcpHeader.sourcePort = newSourcePort;
        }
    }

    public int tcpPayloadSize(boolean containsIpHeader) {
        if (isUDP) return 0;

        int totalLength = getIpHeader().getTotalLength();
        int tcpHeaderLength = tcpHeader.headerLength;
        int ipHeaderLength = containsIpHeader ? getIpHeader().getHeaderLength() : 0;

        return totalLength - (tcpHeaderLength + ipHeaderLength);
    }

    public void updateTcpBuffer(
            ByteBuffer buffer, byte flags, long sequenceNum, long ackNum, int payloadSize) {
        buffer.position(0);
        fillHeader(buffer);
        backingBuffer = buffer;

        tcpHeader.flags = flags;
        backingBuffer.put(getIpHeader().getDefaultHeaderSize() + 13, flags);

        tcpHeader.sequenceNumber = sequenceNum;
        backingBuffer.putInt(getIpHeader().getDefaultHeaderSize() + 4, (int) sequenceNum);

        tcpHeader.acknowledgementNumber = ackNum;
        backingBuffer.putInt(getIpHeader().getDefaultHeaderSize() + 8, (int) ackNum);

        // Reset header size, since we don't need options
        byte dataOffset = (byte) (TCP_HEADER_SIZE << 2);
        int headerLength = (dataOffset >> 4) * 4;
        if (tcpHeader.isSYN()) {
            dataOffset = (byte) (24 << 2);
            headerLength = (dataOffset >> 4) * 4;
            // at this point the buffer is at position 40. We use put(byte[]) so that the position
            // advances. Else the only 40 bytes will later to written in the TUN
            buffer.put((byte) TCP_OPTION_TYPE_MSS);
            buffer.put((byte) TCP_OPTION_MSS_SIZE);
            buffer.putShort((short) TCP_MAX_SEGMENT_SIZE);
        }
        tcpHeader.headerLength = headerLength;
        tcpHeader.dataOffsetAndReserved = dataOffset;
        backingBuffer.put(getIpHeader().getDefaultHeaderSize() + 12, dataOffset);

        updateTCPChecksum(payloadSize);

        int ip4TotalLength = getIpHeader().getDefaultHeaderSize() + headerLength + payloadSize;
        backingBuffer.putShort(2, (short) ip4TotalLength);
        getIpHeader().setTotalLength(ip4TotalLength);

        updateIPChecksum();
    }

    public void updateUdpBuffer(ByteBuffer buffer, int payloadSize) {
        buffer.position(0);
        fillHeader(buffer);
        backingBuffer = buffer;

        int udpTotalLength = UDP_HEADER_SIZE + payloadSize;
        backingBuffer.putShort(getIpHeader().getDefaultHeaderSize() + 4, (short) udpTotalLength);
        udpHeader.length = udpTotalLength;

        // Disable UDP checksum validation
        backingBuffer.putShort(getIpHeader().getDefaultHeaderSize() + 6, (short) 0);
        udpHeader.checksum = 0;

        int ip4TotalLength = getIpHeader().getDefaultHeaderSize() + udpTotalLength;
        backingBuffer.putShort(2, (short) ip4TotalLength);
        getIpHeader().setTotalLength(ip4TotalLength);

        updateIPChecksum();
    }

    private void updateIPChecksum() {
        // IPv6 has no checksum
        if (getIpHeader().getVersion() == 6) {
            return;
        }
        IPHeader.computeChecksum(backingBuffer, 10, getIpHeader().getHeaderLength(), true);
    }

    private void updateTCPChecksum(int payloadSize) {
        int sum = 0;
        int optionsSize = tcpHeader.headerLength - TCP_HEADER_SIZE;
        int tcpLength = TCP_HEADER_SIZE + optionsSize + payloadSize;

        // Calculate pseudo-header checksum
        ByteBuffer buffer = ByteBuffer.wrap(getIpHeader().getSourceAddress().getAddress());
        sum =
                BitUtils.getUnsignedShort(buffer.getShort())
                        + BitUtils.getUnsignedShort(buffer.getShort());

        buffer = ByteBuffer.wrap(getIpHeader().getDestinationAddress().getAddress());
        sum +=
                BitUtils.getUnsignedShort(buffer.getShort())
                        + BitUtils.getUnsignedShort(buffer.getShort());

        sum += TransportProtocol.TCP.getNumber() + tcpLength;

        buffer = backingBuffer.duplicate();
        // Clear previous checksum
        buffer.putShort(getIpHeader().getDefaultHeaderSize() + 16, (short) 0);

        // Calculate TCP segment checksum
        buffer.position(getIpHeader().getDefaultHeaderSize());
        while (tcpLength > 1) {
            sum += BitUtils.getUnsignedShort(buffer.getShort());
            tcpLength -= 2;
        }
        if (tcpLength > 0) sum += BitUtils.getUnsignedByte(buffer.get()) << 8;

        while (sum >> 16 > 0) sum = (sum & 0xFFFF) + (sum >> 16);

        sum = ~sum;
        tcpHeader.checksum = sum;
        backingBuffer.putShort(getIpHeader().getDefaultHeaderSize() + 16, (short) sum);
    }

    private void fillHeader(ByteBuffer buffer) {
        getIpHeader().fillHeader(buffer);
        if (isUDP) udpHeader.fillHeader(buffer);
        else if (isTCP) tcpHeader.fillHeader(buffer);
    }

    public static class TCPHeader {
        public static final int FIN = 0x01;
        public static final int SYN = 0x02;
        public static final int RST = 0x04;
        public static final int PSH = 0x08;
        public static final int ACK = 0x10;
        public static final int URG = 0x20;

        public int sourcePort;
        public int destinationPort;

        public long sequenceNumber;
        public long acknowledgementNumber;

        public byte dataOffsetAndReserved;
        public int headerLength;
        public byte flags;
        public int window;

        public int checksum;
        public int urgentPointer;

        public byte[] optionsAndPadding;

        private TCPHeader(ByteBuffer buffer) {
            this.sourcePort = BitUtils.getUnsignedShort(buffer.getShort());
            this.destinationPort = BitUtils.getUnsignedShort(buffer.getShort());

            this.sequenceNumber = BitUtils.getUnsignedInt(buffer.getInt());
            this.acknowledgementNumber = BitUtils.getUnsignedInt(buffer.getInt());

            this.dataOffsetAndReserved = buffer.get();
            this.headerLength = (this.dataOffsetAndReserved & 0xF0) >> 2;
            this.flags = buffer.get();
            this.window = BitUtils.getUnsignedShort(buffer.getShort());

            this.checksum = BitUtils.getUnsignedShort(buffer.getShort());
            this.urgentPointer = BitUtils.getUnsignedShort(buffer.getShort());

            int optionsLength = this.headerLength - TCP_HEADER_SIZE;
            if (optionsLength > 0) {
                optionsAndPadding = new byte[optionsLength];
                buffer.get(optionsAndPadding, 0, optionsLength);
            }
        }

        public boolean isFIN() {
            return (flags & FIN) == FIN;
        }

        public boolean isSYN() {
            return (flags & SYN) == SYN;
        }

        public boolean isRST() {
            return (flags & RST) == RST;
        }

        public boolean isPSH() {
            return (flags & PSH) == PSH;
        }

        public boolean isACK() {
            return (flags & ACK) == ACK;
        }

        public boolean isURG() {
            return (flags & URG) == URG;
        }

        private void fillHeader(ByteBuffer buffer) {
            buffer.putShort((short) sourcePort);
            buffer.putShort((short) destinationPort);

            buffer.putInt((int) sequenceNumber);
            buffer.putInt((int) acknowledgementNumber);

            buffer.put(dataOffsetAndReserved);
            buffer.put(flags);
            buffer.putShort((short) window);

            buffer.putShort((short) checksum);
            buffer.putShort((short) urgentPointer);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("TCPHeader{");
            sb.append("sourcePort=").append(sourcePort);
            sb.append(", destinationPort=").append(destinationPort);
            sb.append(", sequenceNumber=").append(sequenceNumber);
            sb.append(", acknowledgementNumber=").append(acknowledgementNumber);
            sb.append(", headerLength=").append(headerLength);
            sb.append(", window=").append(window);
            sb.append(", checksum=").append(checksum);
            sb.append(", flags=");
            if (isFIN()) sb.append(" FIN");
            if (isSYN()) sb.append(" SYN");
            if (isRST()) sb.append(" RST");
            if (isPSH()) sb.append(" PSH");
            if (isACK()) sb.append(" ACK");
            if (isURG()) sb.append(" URG");
            sb.append('}');
            return sb.toString();
        }
    }

    public static class UDPHeader {
        public int sourcePort;
        public int destinationPort;

        public int length;
        public int checksum;

        private UDPHeader(ByteBuffer buffer) {
            this.sourcePort = BitUtils.getUnsignedShort(buffer.getShort());
            this.destinationPort = BitUtils.getUnsignedShort(buffer.getShort());

            this.length = BitUtils.getUnsignedShort(buffer.getShort());
            this.checksum = BitUtils.getUnsignedShort(buffer.getShort());
        }

        private void fillHeader(ByteBuffer buffer) {
            buffer.putShort((short) this.sourcePort);
            buffer.putShort((short) this.destinationPort);

            buffer.putShort((short) this.length);
            buffer.putShort((short) this.checksum);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("UDPHeader{");
            sb.append("sourcePort=").append(sourcePort);
            sb.append(", destinationPort=").append(destinationPort);
            sb.append(", length=").append(length);
            sb.append(", checksum=").append(checksum);
            sb.append('}');
            return sb.toString();
        }
    }

    static class BitUtils {
        static short getUnsignedByte(byte value) {
            return (short) (value & 0xFF);
        }

        static int getUnsignedShort(short value) {
            return value & 0xFFFF;
        }

        static long getUnsignedInt(int value) {
            return value & 0xFFFFFFFFL;
        }
    }
}
