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
import xyz.hexene.localvpn.TCB
import xyz.hexene.localvpn.TCB.TCBStatus
import xyz.hexene.localvpn.TCB.TCBStatus.*

class TcpStateFlowTest {

    companion object {
        private val SYN_PACKET = PacketType(isSyn = true)
        private val ACK_PACKET = PacketType(isAck = true)
        private val ACK_FOR_OUR_FIN_PACKET = PacketType(isAck = true, ackNum = 1, finSequenceNumberToClient = 1)
        private val FIN_PACKET = PacketType(isFin = true)
        private val RST_PACKET = PacketType(isRst = true)
        private val DATA_PACKET = PacketType(isAck = true, hasData = true)
        private val FIN_PACKET_WITH_DATA = PacketType(isFin = true, hasData = true)
    }

    @Test
    fun whenConnectionClosedAndSocketOpeningAndImmediatelyConnectedThenMoveToSynReceived() {
        val response = TcpStateFlow.socketOpening(TcbState())

        assertClientStateMove(SYN_SENT, response.events[0])
        assertTrue(response.events[1] is WaitToConnect)
    }

    @Test
    fun whenConnectionClosedAndSocketOpeningAndServerNotListeningThenMoveToSynReceived() {
        val response = TcpStateFlow.socketOpening(TcbState(CLOSED, CLOSED))

        assertTrue(response.events.isEmpty())
    }

    @Test
    fun whenConnectionClosedAndSynPacketReceivedThenOpenConnection() {
        val tcbState = givenTcbState(serverState = LISTEN, clientState = CLOSED)
        val response = whenANewPacketArrives(tcbState, SYN_PACKET)

        assertTrue(response.events.size == 1)
        assertTrue(response.events[0] is OpenConnection)
    }

    @Test
    fun whenConnectionClosedAndRstPacketReceivedThenCloseConnection() {
        val tcbState = givenTcbState(serverState = CLOSED, clientState = CLOSED)
        val response = whenANewPacketArrives(tcbState, RST_PACKET)

        assertTrue(response.events[0] is CloseConnection)
    }

    @Test
    fun whenConnectionClosedAndAckPacketReceivedThenSendReset() {
        val tcbState = givenTcbState(serverState = CLOSED, clientState = CLOSED)
        val response = whenANewPacketArrives(tcbState, ACK_PACKET)

        assertTrue(response.events[0] is SendReset)
    }

    @Test
    fun whenConnectionClosedAndFinPacketReceivedThenSendReset() {
        val tcbState = givenTcbState(serverState = CLOSED, clientState = CLOSED)
        val response = whenANewPacketArrives(tcbState, ACK_PACKET)

        assertTrue(response.events.size == 1)
        assertTrue(response.events[0] is SendReset)
    }

    @Test
    fun whenSynReceivedAndAckReceivedThenMoveToEstablished() {
        val tcbState = givenTcbState(serverState = SYN_RECEIVED, clientState = SYN_SENT)
        val response = whenANewPacketArrives(tcbState, ACK_PACKET)

        assertTrue(response.events.size == 3)
        assertServerStateMove(ESTABLISHED, response.events[0])
        assertClientStateMove(ESTABLISHED, response.events[1])

        assertTrue(response.events[2] is ProcessPacket)
    }

    @Test
    fun whenSynReceivedAndResetReceivedThenConnectionClosed() {
        val tcbState = givenTcbState(serverState = SYN_RECEIVED, clientState = SYN_SENT)
        val response = whenANewPacketArrives(tcbState, RST_PACKET)

        assertTrue(response.events.size == 1)
        assertTrue(response.events[0] is CloseConnection)
    }

    @Test
    fun whenEstablishedAndFinWithNoDataReceivedThenMoveToCloseWait() {
        val tcbState = givenTcbState(serverState = ESTABLISHED, clientState = ESTABLISHED)
        val response = whenANewPacketArrives(tcbState, FIN_PACKET)

        assertTrue(response.events.size == 3)
        assertTrue(response.events[0] is SendFin)
        assertClientStateMove(FIN_WAIT_1, response.events[1])
        assertServerStateMove(LAST_ACK, response.events[2])
    }

    @Test
    fun whenEstablishedAndFinWithDataReceivedThenMoveToCloseWait() {
        val tcbState = givenTcbState(serverState = ESTABLISHED, clientState = ESTABLISHED)
        val response = whenANewPacketArrives(tcbState, FIN_PACKET_WITH_DATA)

        assertTrue(response.events.size == 4)
        assertTrue(response.events[0] is ProcessPacket)
        assertTrue(response.events[1] is SendFin)
        assertClientStateMove(FIN_WAIT_1, response.events[2])
        assertServerStateMove(LAST_ACK, response.events[3])
    }

    @Test
    fun whenEstablishedAndSynThenSendReset() {
        val tcbState = givenTcbState(serverState = ESTABLISHED, clientState = ESTABLISHED)
        val response = whenANewPacketArrives(tcbState, SYN_PACKET)

        assertTrue(response.events[0] is SendReset)
    }

    @Test
    fun whenEstablishedAndRstThenCloseConnection() {
        val tcbState = givenTcbState(serverState = ESTABLISHED, clientState = ESTABLISHED)
        val response = whenANewPacketArrives(tcbState, RST_PACKET)

        assertTrue(response.events[0] is CloseConnection)
    }

    @Test
    fun whenEstablishedAndAckThenProcessPacket() {
        val tcbState = givenTcbState(serverState = ESTABLISHED, clientState = ESTABLISHED)
        val response = whenANewPacketArrives(tcbState, ACK_PACKET)

        assertTrue(response.events[0] is ProcessPacket)
    }

    @Test
    fun whenLastAckAndResetThenCloseConnection() {
        val tcbState = givenTcbState(serverState = LAST_ACK, clientState = ESTABLISHED)
        val response = whenANewPacketArrives(tcbState, RST_PACKET)

        assertTrue(response.events[0] is CloseConnection)
    }

    @Test
    fun whenLastAckAndClosingAckReceivedThenCloseConnection() {
        val tcbState = givenTcbState(serverState = LAST_ACK, clientState = ESTABLISHED)
        val response = whenANewPacketArrives(tcbState, ACK_FOR_OUR_FIN_PACKET)

        assertTrue(response.events[0] is CloseConnection)
    }

    @Test
    fun whenLastAckAndUnexpectedAckReceivedThenProcessPacket() {
        val tcbState = givenTcbState(serverState = LAST_ACK, clientState = ESTABLISHED)
        val response = whenANewPacketArrives(tcbState, ACK_PACKET)

        assertTrue(response.events[0] is ProcessPacket)
    }

    @Test
    fun whenFinWait1AndAckReceivedThenMoveToFinWait2() {
        val tcbState = givenTcbState(serverState = FIN_WAIT_1, clientState = ESTABLISHED)
        val response = whenANewPacketArrives(tcbState, ACK_PACKET)

        assertTrue(response.events[0] is ProcessPacket)
        assertServerStateMove(FIN_WAIT_2, response.events[1])
    }

    @Test
    fun whenFinWait1AndAckForOurFinReceivedThenMoveToFinWait2() {
        val tcbState = givenTcbState(serverState = FIN_WAIT_1, clientState = ESTABLISHED)
        val response = whenANewPacketArrives(tcbState, ACK_FOR_OUR_FIN_PACKET)

        assertServerStateMove(FIN_WAIT_2, response.events[0])
    }

    @Test
    fun whenFinWait1AndRstReceivedThenCloseConnection() {
        val tcbState = givenTcbState(serverState = FIN_WAIT_1, clientState = ESTABLISHED)
        val response = whenANewPacketArrives(tcbState, RST_PACKET)

        assertTrue(response.events[0] is CloseConnection)
    }

    @Test
    fun whenFinWait1AndFinReceivedThenSendAckAndMovesToClosing() {
        val tcbState = givenTcbState(serverState = FIN_WAIT_1, clientState = ESTABLISHED)
        val response = whenANewPacketArrives(tcbState, FIN_PACKET)

        assertTrue(response.events[0] is SendAck)
        assertServerStateMove(CLOSING, response.events[1])
    }

    @Test
    fun whenFinWait1AndPacketWithDataReceivedThenSendsAck() {
        val tcbState = givenTcbState(serverState = FIN_WAIT_1, clientState = ESTABLISHED)
        val response = whenANewPacketArrives(tcbState, FIN_PACKET_WITH_DATA)

        assertTrue(response.events[0] is SendAck)
    }

    @Test
    fun whenFinWait1AndUnexpectedPacketReceivedThenSendReset() {
        val tcbState = givenTcbState(serverState = FIN_WAIT_1, clientState = ESTABLISHED)
        val response = whenANewPacketArrives(tcbState, SYN_PACKET)

        assertTrue(response.events[0] is SendReset)
    }

    @Test
    fun whenFinWait2AndPacketWithDataReceivedThenProcessPacket() {
        val tcbState = givenTcbState(serverState = FIN_WAIT_2, clientState = ESTABLISHED)
        val response = whenANewPacketArrives(tcbState, DATA_PACKET)

        assertTrue(response.events[0] is ProcessPacket)
    }

    @Test
    fun whenFinWait2AndWithDataReceivedThenSendsAck() {
        val tcbState = givenTcbState(serverState = FIN_WAIT_2, clientState = ESTABLISHED)
        val response = whenANewPacketArrives(tcbState, FIN_PACKET_WITH_DATA)

        assertTrue(response.events[0] is SendAck)
        assertServerStateMove(CLOSING, response.events[1])
        assertClientStateMove(TIME_WAIT, response.events[2])
    }

    @Test
    fun whenFinWait2AndRstThenCloseConnection() {
        val tcbState = givenTcbState(serverState = FIN_WAIT_2, clientState = ESTABLISHED)
        val response = whenANewPacketArrives(tcbState, RST_PACKET)

        assertTrue(response.events[0] is CloseConnection)
    }

    @Test
    fun whenFinWait2AndUnexpectedPacketThenSendReset() {
        val tcbState = givenTcbState(serverState = FIN_WAIT_2, clientState = ESTABLISHED)
        val response = whenANewPacketArrives(tcbState, SYN_PACKET)

        assertTrue(response.events[0] is SendReset)
    }

    @Test
    fun whenClosingAndDataReceivedThenSendsAck() {
        val tcbState = givenTcbState(serverState = CLOSING, clientState = ESTABLISHED)
        val response = whenANewPacketArrives(tcbState, DATA_PACKET)

        assertTrue(response.events[0] is SendAck)
    }

    @Test
    fun whenClosingAndAckReceivedThenMoveToTimeWaitAndCloseDelayed() {
        val tcbState = givenTcbState(serverState = CLOSING, clientState = ESTABLISHED)
        val response = whenANewPacketArrives(tcbState, ACK_PACKET)

        assertServerStateMove(TIME_WAIT, response.events[0])
        assertTrue(response.events[1] is DelayedCloseConnection)
    }

    @Test
    fun whenClosingAndUnexpectedPacketThenSendReset() {
        val tcbState = givenTcbState(serverState = CLOSING, clientState = ESTABLISHED)
        val response = whenANewPacketArrives(tcbState, SYN_PACKET)

        assertTrue(response.events[0] is SendReset)
    }

    @Test
    fun whenTimeWaitAndRstReceivedThenCloseConnection() {
        val tcbState = givenTcbState(serverState = TIME_WAIT, clientState = CLOSE_WAIT)
        val response = whenANewPacketArrives(tcbState, RST_PACKET)

        assertTrue(response.events[0] is CloseConnection)
    }

    @Test
    fun whenTimeWaitAndUnexpectedPacketThenSendReset() {
        val tcbState = givenTcbState(serverState = TIME_WAIT, clientState = CLOSE_WAIT)
        val response = whenANewPacketArrives(tcbState, SYN_PACKET)

        assertTrue(response.events[0] is SendReset)
    }

    private fun givenTcbState(serverState: TCBStatus, clientState: TCBStatus): TcbState {
        return TcbState(serverState = serverState, clientState = clientState)
    }

    private fun whenANewPacketArrives(tcbState: TcbState, packetType: PacketType): TcpStateFlow.TcpStateAction {
        val initialSeqNumber = -1L
        val connectionKey = "someKey"
        return TcpStateFlow.newPacket(connectionKey, tcbState, packetType, initialSeqNumber)
    }

    private fun assertClientStateMove(status: TCBStatus, events: TcpStateFlow.Event) {
        val clientMove = events as MoveState.MoveClientToState
        assertTrue(clientMove.state == status)
    }

    private fun assertServerStateMove(status: TCBStatus, events: TcpStateFlow.Event) {
        val serverMove = events as MoveState.MoveServerToState
        assertTrue(serverMove.state == status)
    }
}