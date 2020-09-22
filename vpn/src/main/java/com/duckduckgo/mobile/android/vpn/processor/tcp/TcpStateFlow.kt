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

package com.duckduckgo.mobile.android.vpn.processor.tcp

import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.*
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.MoveState.MoveClientToState
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.MoveState.MoveServerToState
import com.google.firebase.perf.metrics.AddTrace
import timber.log.Timber
import xyz.hexene.localvpn.Packet
import xyz.hexene.localvpn.TCB
import xyz.hexene.localvpn.TCB.TCBStatus.*


class TcpStateFlow {

    companion object {

        @AddTrace(name = "tcp_state_flow_new_packet", enabled = true)
        fun newPacket(
            connectionKey: String,
            currentState: TcbState,
            packetType: PacketType,
            sequenceNumberToClientInitial: Long
        ): TcpStateAction {
            val newActions = when (currentState.serverState) {
                LISTEN -> handlePacketInStateListen(connectionKey, currentState, packetType)
                SYN_RECEIVED -> handlePacketInSynReceived(connectionKey, currentState, packetType, sequenceNumberToClientInitial)
                ESTABLISHED -> handlePacketInEstablished(connectionKey, currentState, packetType)
                LAST_ACK -> handlePacketInLastAck(packetType, connectionKey)
                FIN_WAIT_1 -> handlePacketInFinWait1(connectionKey, currentState, packetType)
                FIN_WAIT_2 -> handlePacketInFinWait2(connectionKey, currentState, packetType)
                CLOSING -> handlePacketInClosing(connectionKey, currentState, packetType)
                TIME_WAIT -> handlePacketInTimeWait(packetType)
                CLOSED -> handlePacketInClosed(packetType)
                else -> unhandledEvent(connectionKey, currentState, packetType)
            }
            Timber.d("$connectionKey. [$currentState]. New actions are [${newActions.ifEmpty { listOf("No Actions") }.joinToString()}]")
            return TcpStateAction(newActions)
        }

        @AddTrace(name = "tcp_state_flow_handle_time_wait", enabled = true)
        private fun handlePacketInTimeWait(packetType: PacketType): List<Event> {
            return when {
                packetType.isRst -> {
                    Timber.w("Received RESET while in TIME_WAIT. Closing connection")
                    listOf(CloseConnection)
                }
                else -> listOf(SendReset)
            }
        }

        @AddTrace(name = "tcp_state_flow_handle_closed", enabled = true)
        private fun handlePacketInClosed(packetType: PacketType): List<Event> {
            return when {
                packetType.isRst -> {
                    Timber.w("Received RESET while in CLOSED. Closing connection")
                    listOf(CloseConnection)
                }
                else -> listOf(SendReset)
            }
        }

        @AddTrace(name = "tcp_state_flow_handle_last_ack", enabled = true)
        private fun handlePacketInLastAck(packetType: PacketType, connectionKey: String): List<Event> {
            return when {
                packetType.isRst -> {
                    Timber.w("Received RESET while in CLOSE_WAIT. Closing connection")
                    listOf(CloseConnection)
                }
                isAckForOurFin(packetType, connectionKey) && (packetType.isAck || packetType.isFin) -> {
                    listOf(CloseConnection)
                }
                else -> listOf(SendReset)
            }
        }

        private fun isAckForOurFin(packetType: PacketType, connectionKey: String): Boolean {
            val match = packetType.ackNum == packetType.finSequenceNumberToClient
            if (!match) {
                Timber.w(
                    "%s - In LAST_ACK, received [fin=%s, ack=%s] but mismatching numbers. Expected=%d, actual=%d",
                    connectionKey, packetType.isFin, packetType.isAck, packetType.finSequenceNumberToClient, packetType.ackNum
                )
            } else {
                Timber.d(
                    "%s - In LAST_ACK, received [fin=%s, ack=%s] with matching numbers. %d",
                    connectionKey, packetType.isFin, packetType.isAck, packetType.finSequenceNumberToClient
                )
            }
            return match
        }

        @AddTrace(name = "tcp_state_flow_handle_fin_wait_1", enabled = true)
        private fun handlePacketInFinWait1(connectionKey: String, currentState: TcbState, packetType: PacketType): List<Event> {
            val eventList = mutableListOf<Event>()

            // check if there's data. if so, should ACK it
            if (packetType.hasData) {
                eventList.add(SendAck)
            }

            eventList.addAll(
                when {
                    packetType.isRst -> {
                        Timber.w("Received RESET while in TIME_WAIT. Closing connection")
                        listOf(CloseConnection)
                    }
                    packetType.isAck -> {
                        listOf(MoveServerToState(FIN_WAIT_2))
                    }
                    packetType.isFin -> {
                        listOf(SendAck, MoveServerToState(CLOSING))
                    }
                    else -> listOf(SendReset)
                }
            )
            return eventList
        }

        @AddTrace(name = "tcp_state_flow_handle_fin_wait_2", enabled = true)
        private fun handlePacketInFinWait2(connectionKey: String, currentState: TcbState, packetType: PacketType): List<Event> {
            val eventList = mutableListOf<Event>()

            eventList.addAll(
                when {
                    packetType.isFin -> {
                        listOf(SendAck, MoveServerToState(CLOSING), MoveClientToState(TIME_WAIT), DelayedCloseConnection)
                    }
                    packetType.isRst -> {
                        Timber.w("Received RESET while in FIN_WAIT_2. Closing connection")
                        listOf(CloseConnection)
                    }
                    packetType.hasData -> {
                        listOf(SendAck)
                    }
                    else -> listOf(SendReset)
                }
            )

            return eventList
        }

        @AddTrace(name = "tcp_state_flow_handle_closing", enabled = true)
        private fun handlePacketInClosing(connectionKey: String, currentState: TcbState, packetType: PacketType): List<Event> {
            val eventList = mutableListOf<Event>()

            // check if there's data. if so, should ACK it
            if (packetType.hasData) {
                eventList.add(SendAck)
            }

            eventList.addAll(
                when {
                    packetType.isRst -> {
                        Timber.w("Received RESET while in CLOSING. Closing connection")
                        listOf(CloseConnection)
                    }
                    packetType.isAck -> {
                        listOf(MoveServerToState(TIME_WAIT), DelayedCloseConnection)
                    }
                    else -> listOf(SendReset)
                }
            )

            return eventList
        }

        @AddTrace(name = "tcp_state_flow_handle_listen", enabled = true)
        private fun handlePacketInStateListen(connectionKey: String, currentState: TcbState, packetType: PacketType): List<Event> {
            return when {
                packetType.isRst -> {
                    Timber.w("Received RESET while in LISTEN. Nothing to do")
                    listOf(CloseConnection)
                }
                packetType.isSyn -> {
                    if (currentState.clientState == SYN_SENT) {
                        Timber.i("Opening a connection when SYN already sent; duplicate SYN can be ignored")
                        return emptyList()
                    }
                    listOf(OpenConnection)
                }
                packetType.hasData -> listOf(SendAck)
                else -> listOf(SendReset)
            }
        }

        private fun socketOpeningInListenState(): List<Event> {
            return listOf(MoveClientToState(SYN_SENT), WaitToConnect)
        }

        @AddTrace(name = "tcp_state_flow_handle_syn_received", enabled = true)
        private fun handlePacketInSynReceived(
            connectionKey: String,
            currentState: TcbState,
            packetType: PacketType,
            initialSequenceNumberToClient: Long
        ): List<Event> {
            return when {
                packetType.isRst -> {
                    Timber.w("Received RESET while in SYN_RECEIVED. Closing connection")
                    listOf(CloseConnection)
                }
                packetType.isAck -> {
                    if (packetType.ackNum != initialSequenceNumberToClient + 1) {
                        Timber.e("Acknowledgement numbers don't match")
                    }

                    listOf(MoveServerToState(ESTABLISHED), MoveClientToState(ESTABLISHED), ProcessPacket)
                }
                else -> listOf(SendReset)
            }
        }

        @AddTrace(name = "tcp_state_flow_handle_established", enabled = true)
        private fun handlePacketInEstablished(connectionKey: String, currentState: TcbState, packetType: PacketType): List<Event> {
            return when {
                packetType.isFin -> {
                    mutableListOf<Event>().also { events ->
                        events.add(MoveClientToState(FIN_WAIT_1))
                        // FIN might also have data, so ProcessPacket will handle that. if not, still need to send ACK in response to FIN
                        if (packetType.hasData) {
                            events.add(ProcessPacket)
                        } else {
                            events.add(SendFin)
                        }
                        events.add(MoveServerToState(LAST_ACK))
                    }
                }
                packetType.isAck -> listOf(ProcessPacket)
                packetType.isRst -> {
                    Timber.w("Received RESET while in ESTABLISHED. Closing connection")
                    listOf(CloseConnection)
                }
                else -> listOf(SendReset)
            }
        }

        fun socketOpening(currentState: TcbState): TcpStateAction {
            val eventList = when (currentState.serverState) {
                LISTEN -> socketOpeningInListenState()
                else -> {
                    Timber.e("Could not open new socket as not in LISTEN state")
                    emptyList()
                }
            }
            return TcpStateAction(eventList)
        }

        fun socketEndOfStream(currentState: TcbState): TcpStateAction {
            return TcpStateAction(listOf(SendReset))
        }

        private fun unhandledEvent(connectionKey: String, currentState: TcbState, packetType: PacketType): List<Event> {
            Timber.e("Unhandled event in $currentState: $packetType for $connectionKey")
            return emptyList()
        }
    }

    data class TcpStateAction(val events: List<Event> = emptyList())

    sealed class Event {
        override fun toString(): String {
            return this::class.java.simpleName
        }

        sealed class MoveState : Event() {

            data class MoveClientToState(val state: TCB.TCBStatus) : MoveState() {
                override fun toString(): String {
                    return "MoveClientToState: $state"
                }
            }

            data class MoveServerToState(val state: TCB.TCBStatus) : MoveState() {
                override fun toString(): String {
                    return "MoveServerToState: $state"
                }
            }
        }

        object OpenConnection : Event()
        object WaitToConnect : Event()
        object ProcessPacket : Event()
        object SendAck : Event()
        object SendFin : Event()
        object SendSynAck : Event()
        object SendReset : Event()
        object CloseConnection : Event()
        object DelayedCloseConnection : Event()
    }

    data class PacketType(
        val isSyn: Boolean = false,
        val isAck: Boolean = false,
        val isFin: Boolean = false,
        val isRst: Boolean = false,
        val hasData: Boolean = false,
        val ackNum: Long = 0,
        val finSequenceNumberToClient: Long = -1
    )
}

fun Packet.asPacketType(finSequenceNumberToClient: Long = -1): TcpStateFlow.PacketType {
    val hasData = tcpPayloadSize(true) > 0

    val tcpHeader = this.tcpHeader
    return TcpStateFlow.PacketType(
        isSyn = tcpHeader.isSYN,
        isAck = tcpHeader.isACK,
        isFin = tcpHeader.isFIN,
        isRst = tcpHeader.isRST,
        hasData = hasData,
        ackNum = tcpHeader.acknowledgementNumber,
        finSequenceNumberToClient = finSequenceNumberToClient
    )
}
