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
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Transition.NoTransition
import timber.log.Timber
import xyz.hexene.localvpn.Packet
import xyz.hexene.localvpn.TCB.TCBStatus
import xyz.hexene.localvpn.TCB.TCBStatus.*


class TcpStateFlow {

    companion object {

        // private val CLOSE_AND_RESET = TcpStateAction(MoveToState(CLOSED), SendReset)

        fun newPacket(currentState: TCPState, packetType: PacketType): TcpStateAction {
            val newAction =  when (currentState.serverState) {
                LISTEN -> handlePacketInStateListen(currentState, packetType)
                ESTABLISHED -> handlePacketInEstablished(packetType)
                CLOSED -> handlePacketInStateClosed(packetType)
                SYN_SENT -> handlePacketInSynSent(packetType)
                SYN_RECEIVED -> handlePacketInSynReceived(packetType)
                LAST_ACK -> handlePacketInLastAck(packetType)
                FIN_WAIT_1 -> handlePacketInFinWait1(packetType)
                FIN_WAIT_2 -> handlePacketInFinWait2(packetType)
                CLOSING -> handlePacketInClosing(packetType)
                TIME_WAIT -> handlePacketInTimeWait(packetType)
                else -> {
                    Timber.e("Unsure how to handle packet. current state: $currentState: $packetType")
                    TcpStateAction(NoTransition, NoEvent)
                }
            }
            Timber.i("TCPStateFlow: from state: $currentState and packetType $packetType, new actions are ${newAction.events.joinToString() }}")
        }

        private fun handlePacketInTimeWait(packetType: PacketType): TcpStateAction {
            return when {
                packetType.isFin -> TcpStateAction(NoTransition, SendAckAndCloseConnection)
                else -> TcpStateAction(NoTransition, NoEvent)
            }
        }

        private fun handlePacketInClosing(packetType: PacketType): TcpStateAction {
            // return when {
            //     packetType.isAck -> TcpStateAction(MoveToState(TIME_WAIT))
            //     else -> CLOSE_AND_RESET
            // }
            return TcpStateAction()
        }

        private fun handlePacketInFinWait1(packetType: PacketType): TcpStateAction {
            // return when {
            //     packetType.isFinAck -> TcpStateAction(MoveToState(TIME_WAIT), SendAck)
            //     packetType.isAck -> TcpStateAction(MoveToState(FIN_WAIT_2))
            //     packetType.isFin -> TcpStateAction(MoveToState(CLOSING), SendAck)
            //     else -> CLOSE_AND_RESET
            // }
            return TcpStateAction()
        }

        private fun handlePacketInFinWait2(packetType: PacketType): TcpStateAction {
            // return when {
            //     packetType.isFin -> TcpStateAction(MoveToState(TIME_WAIT), SendAck)
            //     else -> CLOSE_AND_RESET
            // }
            return TcpStateAction()
        }

        private fun handlePacketInLastAck(packetType: PacketType): TcpStateAction {
            // return when {
            //     packetType.isAck -> TcpStateAction(MoveToState(CLOSED), CloseConnection)
            //     else -> CLOSE_AND_RESET
            // }
            return TcpStateAction()
        }

        private fun handlePacketInStateClosed(packetType: PacketType): TcpStateAction {
            Timber.w("Received packet but in closed state; sending RST")
            return when {
                packetType.isSyn -> handlePacketInStateListen(currentState, packetType)
                else -> TcpStateAction(NoTransition, SendReset)
            }
        }

        private fun handlePacketInStateListen(
            currentState: TCPState,
            packetType: PacketType
        ): TcpStateAction {
//             return when {
//                 packetType.isSyn -> TcpStateAction(NoTransition, OpenConnection)
// //                packetType.isFin -> {
// //                    Timber.w("In LISTEN, but received FIN packet. Sending FIN-ACK")
// //                    TcpStateAction(NoTransition, SendFinAck)
// //                }
//                 packetType.isRst -> TcpStateAction(MoveToState(CLOSED), CloseConnection)
//                 else -> TcpStateAction(NoTransition, SendReset)
//             }
            if (currentState.clientState == SYN_SENT){
                // duplicate syn flag sent through, we don't want to do anything
                return TcpStateAction()
            }


            return TcpStateAction(MoveToState(currentState.copy(clientState = SYN_SENT)))
        }

        private fun handlePacketInSynSent(packetType: PacketType): TcpStateAction {
            // return when {
            //     packetType.isAck -> TcpStateAction(MoveToState(ESTABLISHED))
            //     packetType.isSyn -> TcpStateAction(event = ProcessDuplicateSyn)
            //     else -> CLOSE_AND_RESET
            // }
            return TcpStateAction()
        }

        private fun handlePacketInSynReceived(packetType: PacketType): TcpStateAction {
            // return when {
            //     packetType.isAck -> TcpStateAction(MoveToState(ESTABLISHED), WaitToRead)
            //     else -> CLOSE_AND_RESET
            // }
            return TcpStateAction()
        }

        private fun handlePacketInEstablished(packetType: PacketType): TcpStateAction {
            // return when {
            //     packetType.isFin -> TcpStateAction(MoveToState(LAST_ACK), SendFinAck)
            //     packetType.isAck -> TcpStateAction(event = ProcessPacket)
            //     else -> CLOSE_AND_RESET
            // }
            return TcpStateAction()
        }

        fun socketOpening(currentState: TCBStatus, finishedConnecting: Boolean): TcpStateAction {
            // if (currentState != CLOSED) {
            //     return CLOSE_AND_RESET
            // }
            //
            // return if (finishedConnecting) {
            //     TcpStateAction(MoveToState(SYN_RECEIVED), SendSynAck)
            // } else {
            //     TcpStateAction(MoveToState(SYN_SENT), WaitToRead)
            // }
            return TcpStateAction()
        }

        fun socketEndOfStream(currentState: TCPState): TcpStateAction {
            // return when (currentState.serverState) {
            //     ESTABLISHED -> TcpStateAction(MoveToState(FIN_WAIT_1), SendFinAck)
            //     CLOSE_WAIT -> TcpStateAction(MoveToState(LAST_ACK), SendFinAck)
            //     FIN_WAIT_1 -> TcpStateAction(NoTransition, SendFinAck)
            //     else -> {
            //         Timber.e("Socket end of stream. in state $currentState")
            //         CLOSE_AND_RESET
            //     }
            // }
            return TcpStateAction()
        }
    }

    data class TcpStateAction(val events: List<Event> = listOf(NoEvent))

    sealed class Event {
        override fun toString(): String {
            return this::class.java.simpleName
        }

        data class MoveToState(val state: TCPState) : Event() {
            override fun toString(): String {
                return "MoveToState: $state"
            }
        }

        object NoEvent : Event()
        object OpenConnection : Event()
        object SendAck : Event()
        object SendFin : Event()
        object SendSynAck : Event()
        object SendAckAndCloseConnection : Event()
        object SendFinAck : Event()
        object SendReset : Event()
        object WaitToRead : Event()
        object ProcessDuplicateSyn : Event()
        object CloseConnection : Event()
        object ProcessPacket : Event()
    }

    data class PacketType(val isSyn: Boolean = false, val isAck: Boolean = false, val isFin: Boolean = false, val isRst: Boolean = false) {
        val isFinAck: Boolean = isAck && isFin
    }
}

fun Packet.asPacketType(): TcpStateFlow.PacketType {
    val tcpHeader = this.tcpHeader
    return TcpStateFlow.PacketType(isSyn = tcpHeader.isSYN, isAck = tcpHeader.isACK, isFin = tcpHeader.isFIN, isRst = tcpHeader.isRST)
}
