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

public class IP4Header implements IPHeader {
    private byte version;
    public byte IHL;
    private int headerLength;
    public short typeOfService;
    private int totalLength;

    public int identificationAndFlagsAndFragmentOffset;

    public short TTL;
    public final short protocolNum;
    private TransportProtocol protocol;
    public int headerChecksum;

    private InetAddress sourceAddress;
    private InetAddress destinationAddress;

    public int optionsAndPadding;

    @Override
    public InetAddress getSourceAddress() {
        return sourceAddress;
    }

    @Override
    public void setSourceAddress(InetAddress address) {
        this.sourceAddress = address;
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
    public TransportProtocol getProtocol() {
        return protocol;
    }

    @Override
    public int getHeaderLength() {
        return this.headerLength;
    }

    @Override
    public int getTotalLength() {
        return totalLength;
    }

    @Override
    public void setTotalLength(int totalLength) {
        this.totalLength = totalLength;
    }

    @Override
    public int getVersion() {
        return version;
    }

    IP4Header(ByteBuffer buffer) throws UnknownHostException {
        byte versionAndIHL = buffer.get();
        this.version = (byte) (versionAndIHL >> 4);
        this.IHL = (byte) (versionAndIHL & 0x0F);
        this.headerLength = this.IHL << 2;

        this.typeOfService = Packet.BitUtils.getUnsignedByte(buffer.get());
        this.totalLength = Packet.BitUtils.getUnsignedShort(buffer.getShort());

        this.identificationAndFlagsAndFragmentOffset = buffer.getInt();

        this.TTL = Packet.BitUtils.getUnsignedByte(buffer.get());
        this.protocolNum = Packet.BitUtils.getUnsignedByte(buffer.get());
        this.protocol = TransportProtocol.fromNumber(protocolNum);
        this.headerChecksum = Packet.BitUtils.getUnsignedShort(buffer.getShort());

        byte[] addressBytes = new byte[4];
        buffer.get(addressBytes, 0, 4);
        this.sourceAddress = InetAddress.getByAddress(addressBytes);

        buffer.get(addressBytes, 0, 4);
        this.destinationAddress = InetAddress.getByAddress(addressBytes);

        // this.optionsAndPadding = buffer.getInt();
    }

    @Override
    public void fillHeader(ByteBuffer buffer) {
        buffer.put((byte) (this.version << 4 | this.IHL));
        buffer.put((byte) this.typeOfService);
        buffer.putShort((short) this.totalLength);

        buffer.putInt(this.identificationAndFlagsAndFragmentOffset);

        buffer.put((byte) this.TTL);
        buffer.put((byte) this.protocol.getNumber());
        buffer.putShort((short) this.headerChecksum);

        buffer.put(this.sourceAddress.getAddress());
        buffer.put(this.destinationAddress.getAddress());
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("IP4Header{");
        sb.append("version=").append(version);
        sb.append(", IHL=").append(IHL);
        sb.append(", typeOfService=").append(typeOfService);
        sb.append(", totalLength=").append(totalLength);
        sb.append(", identificationAndFlagsAndFragmentOffset=")
                .append(identificationAndFlagsAndFragmentOffset);
        sb.append(", TTL=").append(TTL);
        sb.append(", protocol=").append(protocolNum).append(":").append(protocol);
        sb.append(", headerChecksum=").append(headerChecksum);
        sb.append(", sourceAddress=").append(sourceAddress.getHostAddress());
        sb.append(", destinationAddress=").append(destinationAddress.getHostAddress());
        sb.append('}');
        return sb.toString();
    }
}
