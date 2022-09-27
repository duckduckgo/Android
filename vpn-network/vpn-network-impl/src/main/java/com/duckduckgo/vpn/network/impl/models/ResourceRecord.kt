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
package com.duckduckgo.vpn.network.impl.models

import java.text.SimpleDateFormat
import java.util.*

class ResourceRecord {
    var Time: Long = 0
    var QName: String? = null
    var AName: String? = null
    var Resource: String? = null
    var TTL = 0

    override fun toString(): String {
        return (
            formatter.format(Date(Time).time) +
                " Q " +
                QName +
                " A " +
                AName +
                " R " +
                Resource +
                " TTL " +
                TTL +
                " " +
                formatter.format(Date(Time + TTL * 1000L).time)
            )
    }

    companion object {
        private val formatter = SimpleDateFormat.getDateTimeInstance()
    }
}
