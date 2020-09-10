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

import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.*
import com.duckduckgo.mobile.android.vpn.processor.tcp.TcpStateFlow.Event.*
import org.junit.Assert.*
import org.junit.Test
import xyz.hexene.localvpn.TCB.TCBStatus
import xyz.hexene.localvpn.TCB.TCBStatus.*

class TcpStateFlowTest {

    companion object {
        private val SYN_PACKET = PacketType(isSyn = true)
        private val ACK_PACKET = PacketType(isAck = true)
        private val FIN_PACKET = PacketType(isFin = true)
    }

//    @Test
//    fun whenConnectionClosedAndSynPacketReceivedThenOpenConnection() {
//        val response = TcpStateFlow.newPacket(CLOSED, SYN_PACKET)
//        assertNoTransition(response)
//        assertEvent(OpenConnection, response)
//    }
//
//    @Test
//    fun whenConnectionClosedAndSocketOpeningAndImmediatelyConnectedThenMoveToSynReceived() {
//        val response = TcpStateFlow.socketOpening(CLOSED)
//        assertState(SYN_RECEIVED, response)
//        assertEvent(SendSynAck, response)
//    }
//
//    @Test
//    fun whenConnectionClosedAndSocketOpeningAndNotImmediatelyConnectedThenMoveToSynSent() {
//        val response = TcpStateFlow.socketOpening(CLOSED)
//        assertState(SYN_SENT, response)
//        assertEvent(NoEvent, response)
//    }
//
//    @Test
//    fun whenConnectionNotClosedAndSocketOpeningAndNotImmediatelyConnectedThenResetAndClose() {
//        val response = TcpStateFlow.socketOpening(SYN_SENT)
//        assertState(CLOSED, response)
//        assertEvent(SendReset, response)
//    }
//
//    @Test
//    fun whenConnectionNotClosedAndSocketOpeningAndImmediatelyConnectedThenResetAndClose() {
//        val response = TcpStateFlow.socketOpening(SYN_SENT)
//        assertState(CLOSED, response)
//        assertEvent(SendReset, response)
//    }
//
//    @Test
//    fun whenSynSentAndAckReceivedThenMoveToEstablished() {
//        val response = TcpStateFlow.newPacket(SYN_SENT, ACK_PACKET)
//        assertState(ESTABLISHED, response)
//        assertEvent(NoEvent, response)
//    }
//
//    @Test
//    fun whenSynReceivedAndAckReceivedThenMoveToEstablished() {
//        val response = TcpStateFlow.newPacket(SYN_RECEIVED, ACK_PACKET)
//        assertState(ESTABLISHED, response)
//        assertEvent(WaitToRead, response)
//    }
//
//    @Test
//    fun whenEstablishedAndFinReceivedThenMoveToCloseWait() {
//        val response = TcpStateFlow.newPacket(ESTABLISHED, FIN_PACKET)
//        assertState(LAST_ACK, response)
//        assertEvent(SendFinAck, response)
//    }
//
//    @Test
//    fun whenEstablishedAndAckReceivedThenProcessPacket() {
//        val response = TcpStateFlow.newPacket(ESTABLISHED, ACK_PACKET)
//        assertNoTransition(response)
//        assertEvent(ProcessPacket, response)
//    }
//
//    @Test
//    fun whenLastAckAndAckReceivedThenMoveToClosed() {
//        val response = TcpStateFlow.newPacket(LAST_ACK, ACK_PACKET)
//        assertState(CLOSED, response)
//        assertEvent(NoEvent, response)
//    }
//
//    @Test
//    fun whenEstablishedAndSocketEndOfStreamHitThenMoveToFinWait1() {
//        val response = TcpStateFlow.socketEndOfStream()
//        assertState(FIN_WAIT_1, response)
//        assertEvent(SendFin, response)
//    }
//
//    @Test
//    fun whenFinWait1AndAckReceivedThenMoveToFinWait2() {
//        val response = TcpStateFlow.newPacket(FIN_WAIT_1, ACK_PACKET)
//        assertState(FIN_WAIT_2, response)
//        assertEvent(NoEvent, response)
//    }
//
//    @Test
//    fun whenFinWait1AndFinReceivedThenMoveToClosed() {
//        val response = TcpStateFlow.newPacket(FIN_WAIT_1, FIN_PACKET)
//        assertState(CLOSED, response)
//        assertEvent(SendAck, response)
//    }
//
//    @Test
//    fun whenFinWait2AndFinReceivedThenMoveToTimeWait() {
//        val response = TcpStateFlow.newPacket(FIN_WAIT_2, FIN_PACKET)
//        assertState(TIME_WAIT, response)
//        assertEvent(SendAck, response)
//    }
//
//    @Test
//    fun whenClosingAndAckReceivedThenMoveToTimeWait() {
//        val response = TcpStateFlow.newPacket(CLOSING, ACK_PACKET)
//        assertState(TIME_WAIT, response)
//        assertEvent(NoEvent, response)
//    }
//
//    @Test
//    fun whenTimeWaitAndAnyPacketReceivedThenReset() {
//        val response = TcpStateFlow.newPacket(TIME_WAIT, ACK_PACKET)
//        assertNoTransition(response)
//        assertEvent(NoEvent, response)
//    }
//
//    @Test
//    fun whenEstablishedAndSynReceivedThenProcessDuplicateSyn() {
//        val response = TcpStateFlow.newPacket(ESTABLISHED, SYN_PACKET)
//        assertEvent(ProcessDuplicateSyn, response)
//    }
//
//    private fun assertNoTransition(response: TcpStateAction) {
//        assertEquals(NoTransition, response.transition)
//    }
//
//    private fun assertState(expected: TCBStatus, response: TcpStateAction) {
//        assertTrue(response.transition is MoveToState)
//        val moveState = response.transition as MoveToState
//        assertEquals(expected, moveState.state)
//    }
//
//    private fun assertEvent(expected: Event, response: TcpStateAction) {
//        assertNotNull(response.events)
//        assertEquals(expected, response.events)
//    }
}