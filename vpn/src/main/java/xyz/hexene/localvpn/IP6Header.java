/*
 * Copyright (c) 2022 DuckDuckGo
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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class IP6Header implements IPHeader {
    /** Size of the IPv6 header in bytes. */
    private static final int HEADER_SIZE_BYTES = 40;

    // 4-bit long
    private final int version;
    // 8-bit long
    private final int trafficClass;
    // 20-bit long
    private final int flowLabel;
    // 16-bit long
    private short payloadLength;
    // 8-bit long
    private final short nextHeader;
    // 8-bit long
    private final short hopLimit;
    // 16 byte long
    private InetAddress sourceAddress;
    // 16 byte long
    private InetAddress destinationAddress;

    public IP6Header(ByteBuffer buffer) throws UnknownHostException {
        byte versionAndHighTrafficClass = buffer.get();
        this.version = versionAndHighTrafficClass >> 4;
        byte lowTrafficClassAndFlowLabelHighNibble = buffer.get();
        this.trafficClass =
                versionAndHighTrafficClass & 0x0f
                        | ((lowTrafficClassAndFlowLabelHighNibble & 0xf0) >> 4);
        short lowFlowLabel = buffer.getShort();
        this.flowLabel = (lowTrafficClassAndFlowLabelHighNibble & 0x0f) << 20 | lowFlowLabel;

        this.payloadLength = buffer.getShort();
        this.nextHeader = buffer.get();
        this.hopLimit = buffer.get();

        sourceAddress = getAddress(buffer);
        destinationAddress = getAddress(buffer);
    }

    @Override
    public InetAddress getSourceAddress() {
        return sourceAddress;
    }

    @Override
    public InetAddress getDestinationAddress() {
        return destinationAddress;
    }

    @Override
    public void setDestinationAddress(InetAddress address) {
        this.destinationAddress = address;
    }

    @Override
    public void setSourceAddress(InetAddress address) {
        this.sourceAddress = address;
    }

    @Override
    public TransportProtocol getProtocol() {
        return TransportProtocol.fromNumber(this.nextHeader);
    }

    @Override
    public int getHeaderLength() {
        return HEADER_SIZE_BYTES;
    }

    @Override
    public int getTotalLength() {
        return this.payloadLength + HEADER_SIZE_BYTES;
    }

    @Override
    public void setTotalLength(int length) {
        this.payloadLength = (short) length;
    }

    @Override
    public int getVersion() {
        return version;
    }

    @Override
    public void fillHeader(ByteBuffer buffer) {
        buffer.put((byte) ((version << 4) | (trafficClass & 0xF0) >> 4));
        buffer.put((byte) ((trafficClass & 0x0f) << 4 | (flowLabel & 0xf0000) >> 16));
        buffer.putShort((short) (flowLabel & 0x0FFFF));
        buffer.putShort(payloadLength);
        buffer.put((byte) nextHeader);
        buffer.put((byte) hopLimit);
        buffer.put(sourceAddress.getAddress());
        buffer.put(destinationAddress.getAddress());
    }

    private InetAddress getAddress(ByteBuffer buffer) throws UnknownHostException {
        byte[] addressBytes = new byte[16];
        buffer.get(addressBytes, 0, 16);
        return InetAddress.getByAddress(addressBytes);
    }

    @Override
    public String toString() {
        return "IP6Header{"
                + "version="
                + version
                + ", trafficClass="
                + trafficClass
                + ", flowLabel="
                + flowLabel
                + ", payloadLength="
                + payloadLength
                + ", nextHeader="
                + nextHeader
                + ", hopLimit="
                + hopLimit
                + ", sourceAddress="
                + sourceAddress
                + ", destinationAddress="
                + destinationAddress
                + '}';
    }
}
