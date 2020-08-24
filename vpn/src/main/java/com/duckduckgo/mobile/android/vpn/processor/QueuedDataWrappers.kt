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

package com.duckduckgo.mobile.android.vpn.processor

import xyz.hexene.localvpn.Packet
import java.nio.ByteBuffer

data class QueuedBuffer(val buffer: ByteBuffer, val createdTime: Long =  System.nanoTime())
data class QueuedPacket(val packet: Packet, val createdTime: Long = System.nanoTime())

fun QueuedBuffer.lagTimeNanoseconds(): Long = System.nanoTime() - createdTime
fun QueuedBuffer.lagTimeMilliseconds(): Long = (System.nanoTime() - createdTime).nanosecondsToMilliseconds()

fun QueuedPacket.lagTimeNanoseconds(): Long = System.nanoTime() - createdTime
fun QueuedPacket.lagTimeMilliseconds(): Long = (System.nanoTime() - createdTime).nanosecondsToMilliseconds()

private fun Long.nanosecondsToMilliseconds() : Long = this / MILLIS_IN_NANOSECOND

private const val MILLIS_IN_NANOSECOND = 1_000_000