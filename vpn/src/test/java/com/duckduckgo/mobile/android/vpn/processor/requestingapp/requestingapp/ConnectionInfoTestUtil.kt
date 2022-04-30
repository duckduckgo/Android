/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.processor.requestingapp.requestingapp

import com.duckduckgo.mobile.android.vpn.processor.requestingapp.ConnectionInfo
import xyz.hexene.localvpn.TransportProtocol
import java.net.InetAddress

internal fun aConnectionInfo(
    destinationAddress: InetAddress = InetAddress.getByName(anExternalIpAddress()),
    destinationPort: Int = 80,
    sourceAddress: InetAddress = InetAddress.getByName(anInternalIpAddress()),
    sourcePort: Int = 40000,
    protocol: Int = aProtocol()
): ConnectionInfo = ConnectionInfo(destinationAddress, destinationPort, sourceAddress, sourcePort, protocol)

internal fun anInternalIpAddress(): String = "192.168.0.1"
internal fun anExternalIpAddress(): String = "52.142.124.215"
private fun aProtocol(): Int = TransportProtocol.TCP.number
