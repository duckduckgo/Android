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

/**
 * Enum encapsulating properties of different network protocols.
 * Note that in order to improve performance, caching of the enum values occur. The bi-product of this is that we no
 * longer ensure immutability of this enum as the contents of the values array can be modified.
 *
 * @author Simon Langhoff, Janus Varmarken
 */
public enum Protocol {

    /**
     * Encapsulates properties of the Transmission Control Protocol.
     */
    TCP {
        @Override
        public short getProtocolNumber() {
            return 6;
        }
    },

    /**
     * Encapsulates properties of the User Datagram Protocol.
     */
    UDP {
        @Override
        public short getProtocolNumber() {
            return 17;
        }
    },


    /**
     * Encapsulates properties of the Internet Control Message Protocol.
     */
    ICMP {
        @Override
        public short getProtocolNumber() {
            return 1;
        }
    };

    private final static Protocol[] values = Protocol.values();

    /**
     * Gets the {@link Protocol} that corresponds to a given protocol number.
     * @param protocolNumber An IANA assigned protocol number.
     * @return The {@link Protocol} identified by the given {@code protocolNumber}
     *      or {@code null} if there is no match. A perfectly valid protocol number may produce a {@code null}
     *      result as we (at the time of writing) only support a tiny subset of the IANA protocol numbers.
     * @see <a href="http://www.iana.org/assignments/protocol-numbers/protocol-numbers.xhtml">IANA protocol numbers</a>
     */
    public static Protocol getFromProtocolNumber(short protocolNumber) {
        for(Protocol p : values) {
            if (p.getProtocolNumber() == protocolNumber) {
                return p;
            }
        }
        return null;
    }

    /**
     * <p>
     *      Gets the protocol number of this {@code Protocol} (as assigned by the IANA).
     *      See <a href="http://www.iana.org/assignments/protocol-numbers/protocol-numbers.xhtml" target="_blank">IANA</a>.
     * </p>
     *
     * @return The protocol number of this {@code Protocol}.
     */
    abstract public short getProtocolNumber();
}
