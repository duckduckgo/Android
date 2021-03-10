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

package com.duckduckgo.mobile.android.vpn.heartbeat

import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.mobile.android.vpn.dao.HeartBeatEntity
import com.duckduckgo.mobile.android.vpn.dao.VpnHeartBeatDao
import com.duckduckgo.mobile.android.vpn.dao.VpnPhoenixDao
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.jakewharton.threetenabp.AndroidThreeTen
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
class VpnServiceHeartbeatProcessorTest {

    private val vpnDatabase: VpnDatabase = mock()
    private val phoenixDao: VpnPhoenixDao = mock()
    private val heartBeatDao: VpnHeartBeatDao = mock()
    private val listener: VpnServiceHeartbeatProcessor.Listener = mock()

    private val testDispatcher = TestCoroutineDispatcher()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val testDispatcherProvider = object : DispatcherProvider {
        override fun default(): CoroutineDispatcher = testDispatcher
        override fun io(): CoroutineDispatcher = testDispatcher
        override fun main(): CoroutineDispatcher = testDispatcher
        override fun unconfined(): CoroutineDispatcher = testDispatcher

    }

    private val aliveEntity = HeartBeatEntity("ALIVE")
    private val stopEntity = HeartBeatEntity("STOPPED")

    private lateinit var processor: VpnServiceHeartbeatProcessor

    @Before
    fun setup() {
        AndroidThreeTen.init(context)

        whenever(vpnDatabase.vpnPhoenixDao()).thenReturn(phoenixDao)
        whenever(vpnDatabase.vpnHeartBeatDao()).thenReturn(heartBeatDao)
        whenever(heartBeatDao.insertType(eq("ALIVE"))).thenReturn(aliveEntity)
        whenever(heartBeatDao.insertType(eq("STOPPED"))).thenReturn(stopEntity)

        processor = VpnServiceHeartbeatProcessor(context, vpnDatabase, testDispatcherProvider)
    }

    @Test
    fun whenAliveThenInsertInHeartBeatDatabase() = runBlockingTest {
        VpnServiceHeartbeatReceiver.aliveBroadcastIntent(context, 1, TimeUnit.SECONDS).also {
            processor.processHeartBeat(it, listener)
        }

        verify(heartBeatDao).insertType("ALIVE")
    }

    @Test
    fun whenAliveThenCallOnAliveReceived() = runBlockingTest {
        VpnServiceHeartbeatReceiver.aliveBroadcastIntent(context, 1, TimeUnit.SECONDS).also {
            processor.processHeartBeat(it, listener)
        }

        verify(listener).onAliveReceived()
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun whenStoppedThenCallOnStopReceived() = runBlockingTest {
        VpnServiceHeartbeatReceiver.stoppedBroadcastIntent(context).also {
            processor.processHeartBeat(it, listener)
        }

        verify(listener).onStopReceived()
        verifyNoMoreInteractions(listener)
    }

    @Test
    fun whenCheckLastHeartBeatAndNoDbEntryThenNoop() = runBlockingTest {
        processor.checkLastHeartBeat(listener)

        verifyZeroInteractions(listener)
    }

    @Test
    fun whenCheckLastHeartBeatAndLastIsStoppedEntryThenNoop() = runBlockingTest {
        whenever(heartBeatDao.hearBeats()).thenReturn(listOf(stopEntity))

        processor.checkLastHeartBeat(listener)

        verifyZeroInteractions(listener)
    }

    @Test
    fun whenCheckLastHeartBeatAndLastIsAliveEntryThenOnAliveMissed() = runBlockingTest {
        whenever(heartBeatDao.hearBeats()).thenReturn(listOf(aliveEntity))

        processor.checkLastHeartBeat(listener)

        verify(listener).onAliveMissed()
    }
}
