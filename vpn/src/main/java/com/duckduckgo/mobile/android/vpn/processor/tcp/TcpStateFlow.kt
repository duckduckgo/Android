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

        @AddTrace(name = "debug.new_packet", enabled = true)
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
//                CLOSED -> handlePacketInStateClosed(packetType)
//                SYN_SENT -> handlePacketInSynSent(packetType)
//                SYN_RECEIVED -> handlePacketInSynReceived(packetType)
//                LAST_ACK -> handlePacketInLastAck(packetType)


//                CLOSING -> handlePacketInClosing(packetType)
//                TIME_WAIT -> handlePacketInTimeWait(packetType)
                else -> unhandledEvent(connectionKey, currentState, packetType)
            }
            Timber.d("$connectionKey. [$currentState]. New actions are [${newActions.ifEmpty { listOf("No Actions") }.joinToString()}]")
            return TcpStateAction(newActions)
        }

        private fun handlePacketInTimeWait(packetType: PacketType): List<Event> {
            return when {
                packetType.isRst -> {
                    Timber.w("Received RESET while in TIME_WAIT. Closing connection")
                    listOf(CloseConnection)
                }
                else -> listOf(SendAck)
            }
        }

        private fun handlePacketInLastAck(packetType: PacketType, connectionKey: String): List<Event> {
            return when {
                packetType.isRst -> {
                    Timber.w("Received RESET while in CLOSE_WAIT. Closing connection")
                    listOf(CloseConnection)
                }
                isAckForOurFin(packetType, connectionKey) && (packetType.isAck || packetType.isFin) -> {
                    listOf(MoveServerToState(TIME_WAIT), MoveClientToState(CLOSED), DelayedCloseConnection)
                }
                else -> emptyList()
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
                Timber.w(
                    "%s - In LAST_ACK, received [fin=%s, ack=%s] with matching numbers. %d",
                    connectionKey, packetType.isFin, packetType.isAck, packetType.finSequenceNumberToClient
                )
            }
            return match
        }

        private fun handlePacketInFinWait1(connectionKey: String, currentState: TcbState, packetType: PacketType): List<Event> {
            val eventList = mutableListOf<Event>()

            // check if there's data. if so, should ACK it
            if (packetType.hasData) {
                eventList.add(SendAck)
            }

            eventList.addAll(
                when {
                    packetType.isFin -> {
                        listOf(SendAck, MoveServerToState(CLOSING), MoveClientToState(FIN_WAIT_1))
                    }
                    packetType.isAck -> {
                        listOf(MoveServerToState(FIN_WAIT_2))
                    }
                    else -> unhandledEvent(connectionKey, currentState, packetType)
                }
            )
            return eventList
        }

        private fun handlePacketInFinWait2(connectionKey: String, currentState: TcbState, packetType: PacketType): List<Event> {
            val eventList = mutableListOf<Event>()

            // check if there's data. if so, should ACK it
            if (packetType.hasData) {
                eventList.add(SendAck)
            }

            eventList.addAll(
                when {
                    packetType.isFin -> {
                        listOf(SendAck, MoveServerToState(TIME_WAIT), MoveClientToState(LAST_ACK), DelayedCloseConnection)
                    }
                    packetType.isRst -> listOf(CloseConnection)
                    else -> unhandledEvent(connectionKey, currentState, packetType)
                }
            )

            return eventList
        }

        //        private fun handlePacketInTimeWait(packetType: PacketType): TcpStateAction {
//            return when {
//                packetType.isFin -> TcpStateAction(NoTransition, SendAckAndCloseConnection)
//                else -> TcpStateAction(NoTransition, NoEvent)
//            }
//        }
//
        private fun handlePacketInClosing(connectionKey: String, currentState: TcbState, packetType: PacketType): List<Event> {
            val eventList = mutableListOf<Event>()

            // check if there's data. if so, should ACK it
            if (packetType.hasData) {
                eventList.add(SendAck)
            }

            eventList.addAll(
                when {
                    packetType.isRst -> listOf(CloseConnection)
                    packetType.isAck -> {
                        // check for data, and acknowledge it if there is any
                        listOf(MoveClientToState(CLOSED), MoveServerToState(TIME_WAIT), DelayedCloseConnection)
                    }
                    else -> unhandledEvent(connectionKey, currentState, packetType)
                }
            )

            return eventList
        }

        //
//        private fun handlePacketInFinWait1(packetType: PacketType): TcpStateAction {
//            // return when {
//            //     packetType.isFinAck -> TcpStateAction(MoveToState(TIME_WAIT), SendAck)
//            //     packetType.isAck -> TcpStateAction(MoveToState(FIN_WAIT_2))
//            //     packetType.isFin -> TcpStateAction(MoveToState(CLOSING), SendAck)
//            //     else -> CLOSE_AND_RESET
//            // }
//            return TcpStateAction()
//        }
//
//        private fun handlePacketInFinWait2(packetType: PacketType): TcpStateAction {
//            // return when {
//            //     packetType.isFin -> TcpStateAction(MoveToState(TIME_WAIT), SendAck)
//            //     else -> CLOSE_AND_RESET
//            // }
//            return TcpStateAction()
//        }
//
//        private fun handlePacketInLastAck(packetType: PacketType): TcpStateAction {
//            // return when {
//            //     packetType.isAck -> TcpStateAction(MoveToState(CLOSED), CloseConnection)
//            //     else -> CLOSE_AND_RESET
//            // }
//            return TcpStateAction()
//        }
//
//        private fun handlePacketInStateClosed(packetType: PacketType): TcpStateAction {
//            Timber.w("Received packet but in closed state; sending RST")
//            return when {
//                packetType.isSyn -> handlePacketInStateListen(currentState, packetType)
//                else -> TcpStateAction(NoTransition, SendReset)
//            }
//        }
//
        private fun handlePacketInStateListen(connectionKey: String, currentState: TcbState, packetType: PacketType): List<Event> {
            return when {
                packetType.isRst -> listOf(CloseConnection)
                packetType.isSyn -> {
                    if (currentState.clientState == SYN_SENT) {
                        Timber.i("Opening a connection when SYN already sent; duplicate SYN can be ignored")
                        return emptyList()
                    }
                    listOf(OpenConnection)
                }
                packetType.isFin -> listOf(SendReset)
                packetType.isAck -> emptyList()
                packetType.hasData -> listOf(SendReset)
                else -> unhandledEvent(connectionKey, currentState, packetType)
            }
        }

        private fun socketOpeningInListenState(): List<Event> {
            return listOf(MoveClientToState(SYN_SENT), WaitToConnect)
        }

        //}
//        }
//
//        private fun handlePacketInSynSent(packetType: PacketType): TcpStateAction {
//            // return when {
//            //     packetType.isAck -> TcpStateAction(MoveToState(ESTABLISHED))
//            //     packetType.isSyn -> TcpStateAction(event = ProcessDuplicateSyn)
//            //     else -> CLOSE_AND_RESET
//            // }
//            return TcpStateAction()
//        }
//
        private fun handlePacketInSynReceived(connectionKey: String, currentState: TcbState, packetType: PacketType, initialSequenceNumberToClient: Long): List<Event> {
            return when {
                packetType.isRst -> listOf(CloseConnection)
                packetType.isAck -> {
                    if (packetType.ackNum != initialSequenceNumberToClient + 1) {
                        Timber.e("Acknowledgement numbers don't match")
                    }

                    listOf(MoveServerToState(ESTABLISHED), MoveClientToState(ESTABLISHED), ProcessPacket)
                }
                else -> unhandledEvent(connectionKey, currentState, packetType)
            }
        }

        private fun handlePacketInEstablished(connectionKey: String, currentState: TcbState, packetType: PacketType): List<Event> {
            return when {
                packetType.isFin -> {
                    mutableListOf<Event>().also { events ->
                        events.add(MoveClientToState(FIN_WAIT_1))

                        // FIN might also have data, so ProcessPacket will handle that. if not, still need to send ACK in response to FIN
                        if (packetType.hasData) {
                            events.add(ProcessPacket)
                        } else {
                            events.add(SendAck)
                            events.add(SendFin)
                        }
                        events.add(MoveServerToState(CLOSE_WAIT))

//                        // client would normally be in FIN_WAIT_1 until it gets the ACK (FIN_WAIT_2). Safe to jump straight to FIN_WAIT_2
//                        events.add(MoveClientToState(FIN_WAIT_2))
//
//                        // server would normally be in CLOSE_WAIT until it sends its FIN. Safe to jump straight to LAST_ACK
//                        events.add(MoveServerToState(LAST_ACK))
                    }
                }
                packetType.isSyn -> listOf(SendReset)
                packetType.isAck -> listOf(ProcessPacket)
                packetType.isRst -> listOf(CloseConnection)
                else -> unhandledEvent(connectionKey, currentState, packetType)
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
//            val newServerState = when (currentState.serverState) {
//                ESTABLISHED -> FIN_WAIT_1
//                CLOSE_WAIT -> LAST_ACK
//                else -> FIN_WAIT_1
//            }
//
//            return TcpStateAction(
//                listOf(
//                    MoveServerToState(newServerState), DelayedSendFin
//                )
//            )
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
        object WaitToRead : Event()
        object WaitToConnect : Event()
        object ProcessPacket : Event()
        object SendAck : Event()
        object SendFin : Event()
        object DelayedSendFin : Event()
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
    ) {
        val isFinAck: Boolean = isAck && isFin
    }
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
