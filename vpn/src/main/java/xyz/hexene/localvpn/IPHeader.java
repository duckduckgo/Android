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
import java.nio.ByteBuffer;

public interface IPHeader {
    InetAddress getSourceAddress();

    void setSourceAddress(InetAddress address);

    InetAddress getDestinationAddress();

    void setDestinationAddress(InetAddress address);

    TransportProtocol getProtocol();

    int getDefaultHeaderSize();

    int getHeaderLength();

    int getTotalLength();

    void setTotalLength(int length);

    int getVersion();

    void fillHeader(ByteBuffer buffer);

    /**
     * Calculates checksums assuming the checksum is a 16-bit header field. This method is
     * generalized to work for IP, ICMP, UDP, and TCP packets given the proper parameters.
     */
    static int computeChecksum(
            ByteBuffer backingBuffer, int checksumOffset, int length, boolean update) {

        ByteBuffer buffer = backingBuffer.duplicate();
        buffer.position(0);

        // Clear previous checksum
        buffer.putShort(checksumOffset, (short) 0);

        int ipLength = length;
        int sum = 0;
        while (ipLength > 0) {
            sum += Packet.BitUtils.getUnsignedShort(buffer.getShort());
            ipLength -= 2;
        }
        while (sum >> 16 > 0) sum = (sum & 0xFFFF) + (sum >> 16);

        sum = ~sum;
        if (update) {
            backingBuffer.putShort(checksumOffset, (short) sum);
        }

        return sum;
    }
}
