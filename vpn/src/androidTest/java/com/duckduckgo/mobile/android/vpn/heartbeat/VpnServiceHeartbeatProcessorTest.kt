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

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.mobile.android.vpn.VpnCoroutineTestRule
import com.duckduckgo.mobile.android.vpn.dao.HeartBeatEntity
import com.duckduckgo.mobile.android.vpn.dao.VpnHeartBeatDao
import com.duckduckgo.mobile.android.vpn.runBlocking
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.jakewharton.threetenabp.AndroidThreeTen
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import junit.framework.Assert.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class VpnServiceHeartbeatProcessorTest {

    private lateinit var db: VpnDatabase

    private val fakeDb: VpnDatabase = mock()
    private val heartBeatDao: VpnHeartBeatDao = mock()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var processor: VpnServiceHeartbeatProcessor
    private val aliveEntity = HeartBeatEntity("ALIVE", 1000)
    private val anotherAliveEntity = HeartBeatEntity("ALIVE", 2000)

    private val vpnCoroutineTestRule = VpnCoroutineTestRule()

    @Before
    fun setup() {
        AndroidThreeTen.init(context)

        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, VpnDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        whenever(fakeDb.vpnHeartBeatDao()).thenReturn(heartBeatDao)

        processor = VpnServiceHeartbeatProcessor(context, db, vpnCoroutineTestRule.testDispatcherProvider)
    }

    @Test
    fun whenOnStoppedReceivedThenInsertInHeartBeatDatabase() = vpnCoroutineTestRule.runBlocking {
        processor.onStopReceive()

        assertEquals(1, db.vpnHeartBeatDao().hearBeats().size)
        assertEquals("STOPPED", db.vpnHeartBeatDao().hearBeats()[0].type)
    }

    @Test
    fun whenOnAliveReceivedThenInsertInHeartBeatDatabase() = vpnCoroutineTestRule.runBlocking {
        processor.onAliveReceivedDidNextOneArrived(1)

        assertEquals(1, db.vpnHeartBeatDao().hearBeats().size)
        assertEquals("ALIVE", db.vpnHeartBeatDao().hearBeats()[0].type)
    }

    @Test
    fun whenOnAliveReceivedAndNextAliveIsMissedThenReturnTrue() = vpnCoroutineTestRule.runBlocking {
        assertTrue(processor.onAliveReceivedDidNextOneArrived(1))
    }

    @Test
    fun whenOnAliveReceivedAndNextAliveArrivesThenReturnFalse() = vpnCoroutineTestRule.runBlocking {
        whenever(heartBeatDao.insertType(eq("ALIVE"))).thenReturn(aliveEntity)
        whenever(heartBeatDao.hearBeats()).thenReturn(listOf(anotherAliveEntity))

        val fakeProcessor = VpnServiceHeartbeatProcessor(context, fakeDb, vpnCoroutineTestRule.testDispatcherProvider)

        assertFalse(fakeProcessor.onAliveReceivedDidNextOneArrived(1))
    }

    @Test
    fun whenWasLastHeartbeatAliveAndNoDbEntryThenReturnFalse() = vpnCoroutineTestRule.runBlocking {
        assertFalse(processor.didReceivedAliveLastTime())
    }

    @Test
    fun whenWasLastHeartbeatAliveAndLastIsStoppedEntryThenReturnFalse() = vpnCoroutineTestRule.runBlocking {
        db.vpnHeartBeatDao().insertType("STOPPED")

        assertFalse(processor.didReceivedAliveLastTime())
    }

    @Test
    fun whenWasLastHeartbeatAliveAndLastIsAliveEntryThenReturnTrue() = vpnCoroutineTestRule.runBlocking {
        db.vpnHeartBeatDao().insertType("ALIVE")

        assertTrue(processor.didReceivedAliveLastTime())
    }
}
