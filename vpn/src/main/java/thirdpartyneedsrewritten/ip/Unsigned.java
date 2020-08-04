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
 * Utility class to get and put unsigned values to a ByteBuffer object.
 */
class Unsigned {
    public static short getUnsignedByte(ByteBuffer bb) {
        return ((short) (bb.get() & 0xff));
    }

    public static short getUnsignedByte(ByteBuffer bb, int position) {
        return ((short) (bb.get(position) & (short) 0xff));
    }

    public static int getUnsignedShort(ByteBuffer bb) {
        return (bb.getShort() & 0xffff);
    }


    public static int getUnsignedShort(ByteBuffer bb, int position) {
        return (bb.getShort(position) & 0xffff);
    }

    public static long getUnsignedInt(ByteBuffer bb) {
        return ((long) bb.getInt() & 0xffffffffL);
    }
}
