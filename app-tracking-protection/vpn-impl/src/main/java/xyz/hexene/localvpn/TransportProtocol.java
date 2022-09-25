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

public enum TransportProtocol {
    TCP(6),
    UDP(17),
    Other(0xFF);

    private int protocolNumber;

    TransportProtocol(int protocolNumber) {
        this.protocolNumber = protocolNumber;
    }

    public static TransportProtocol fromNumber(int protocolNumber) {
        if (protocolNumber == 6) return TCP;
        else if (protocolNumber == 17) return UDP;
        else return Other;
    }

    public int getNumber() {
        return this.protocolNumber;
    }
}
