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

package com.duckduckgo.mobile.android.vpn.service

import java.net.Socket

interface VpnSocketProtector {
    /**
     * Call this method to protect the socket from VPN.
     *
     * @param socket The file descriptor of the socket to protect.
     * @#return true if the socket is protected, false otherwise.
     */
    fun protect(socket: Int): Boolean

    /**
     * Call this method to protect the socket from VPN.
     *
     * @param socket The [Socket] to protect.
     * @#return true if the socket is protected, false otherwise.
     */
    fun protect(socket: Socket): Boolean
}
