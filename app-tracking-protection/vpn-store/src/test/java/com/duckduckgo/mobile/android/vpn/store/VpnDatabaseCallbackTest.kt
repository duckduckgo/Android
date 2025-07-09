/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.store

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerMetadata
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VpnDatabaseCallbackTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    private val vpnDatabase: VpnDatabase = Room.inMemoryDatabaseBuilder(
        context,
        VpnDatabase::class.java,
    ).allowMainThreadQueries().build()

    private val vpnDatabaseCallback = VpnDatabaseCallback(
        context,
        { vpnDatabase },
        coroutineRule.testDispatcherProvider,
        coroutineRule.testScope,
        Mutex(),
    )

    @Test
    fun whenOnCreateAndNullETagThenPrePopulate() = runTest {
        assertNull(vpnDatabase.vpnAppTrackerBlockingDao().getTrackerBySubdomain("15.taboola.com"))
        assertNull(vpnDatabase.vpnAppTrackerBlockingDao().getTrackerBlocklistMetadata())

        vpnDatabaseCallback.onCreate(vpnDatabase.openHelper.writableDatabase)

        assertNotNull(vpnDatabase.vpnAppTrackerBlockingDao().getTrackerBySubdomain("15.taboola.com"))
        assertEquals(110, vpnDatabase.vpnAppTrackerBlockingDao().getAppExclusionList().size)
        assertEquals(21, vpnDatabase.vpnAppTrackerBlockingDao().getTrackerExceptionRules().size)
        // pre-population doesn't set metadata
        assertNull(vpnDatabase.vpnAppTrackerBlockingDao().getTrackerBlocklistMetadata())
        assertNull(vpnDatabase.vpnAppTrackerBlockingDao().getExclusionListMetadata())
        assertNull(vpnDatabase.vpnAppTrackerBlockingDao().getTrackerExceptionRulesMetadata())
    }

    @Test
    fun whenOnCreateAndETagNotNullThenSkipPrePopulate() = runTest {
        assertNull(vpnDatabase.vpnAppTrackerBlockingDao().getTrackerBySubdomain("15.taboola.com"))
        assertNull(vpnDatabase.vpnAppTrackerBlockingDao().getTrackerBlocklistMetadata())

        vpnDatabase.vpnAppTrackerBlockingDao().setTrackerBlocklistMetadata(AppTrackerMetadata(0, "1"))

        vpnDatabaseCallback.onCreate(vpnDatabase.openHelper.writableDatabase)

        // tracker list continues to be empty
        assertNull(vpnDatabase.vpnAppTrackerBlockingDao().getTrackerBySubdomain("15.taboola.com"))
        assertEquals(0, vpnDatabase.vpnAppTrackerBlockingDao().getAppExclusionList().size)
        assertEquals(0, vpnDatabase.vpnAppTrackerBlockingDao().getTrackerExceptionRules().size)
        assertNull(vpnDatabase.vpnAppTrackerBlockingDao().getExclusionListMetadata())
        assertNull(vpnDatabase.vpnAppTrackerBlockingDao().getTrackerExceptionRulesMetadata())
        assertEquals(
            "1",
            vpnDatabase.vpnAppTrackerBlockingDao().getTrackerBlocklistMetadata()?.eTag,
        )
    }
}
