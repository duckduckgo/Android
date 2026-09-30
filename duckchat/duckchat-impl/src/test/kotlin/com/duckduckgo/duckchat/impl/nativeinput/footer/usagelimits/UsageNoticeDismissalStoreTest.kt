/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.nativeinput.footer.FailingPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class UsageNoticeDismissalStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var testee: UsageNoticeDismissalStore

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = coroutineRule.testScope,
            produceFile = { context.preferencesDataStoreFile("usage_notice_${UUID.randomUUID()}") },
        )
        testee = UsageNoticeDismissalStore(dataStore)
    }

    @Test
    fun whenNothingWasDismissedThenThereAreNoDismissals() = runTest {
        assertEquals(emptyMap<UsageWindow, UsageNoticeDismissal>(), testee.dismissals.first())
    }

    @Test
    fun whenNoticeIsDismissedThenItsIdentityAndBandArePersisted() = runTest {
        testee.dismiss(approaching(78))

        assertEquals(
            UsageNoticeDismissal(UsageNoticeId.APPROACHING, UsageWindow.WEEKLY, RESETS_AT, 75),
            testee.dismissals.first()[UsageWindow.WEEKLY],
        )
    }

    @Test
    fun whenNoticesInBothWindowsAreDismissedThenBothAreRemembered() = runTest {
        testee.dismiss(approaching(55))
        testee.dismiss(approaching(92).copy(window = UsageWindow.DAILY))

        assertEquals(
            mapOf(
                UsageWindow.WEEKLY to UsageNoticeDismissal(UsageNoticeId.APPROACHING, UsageWindow.WEEKLY, RESETS_AT, 50),
                UsageWindow.DAILY to UsageNoticeDismissal(UsageNoticeId.APPROACHING, UsageWindow.DAILY, RESETS_AT, 90),
            ),
            testee.dismissals.first(),
        )
    }

    @Test
    fun whenTheSameWindowIsDismissedAgainThenTheNewerRecordWins() = runTest {
        testee.dismiss(approaching(55))
        testee.dismiss(approaching(80))

        assertEquals(75, testee.dismissals.first().getValue(UsageWindow.WEEKLY).band)
    }

    @Test
    fun whenClearedThenAllWindowsAreForgotten() = runTest {
        testee.dismiss(approaching(55))
        testee.dismiss(approaching(55).copy(window = UsageWindow.DAILY))

        testee.clear()

        assertEquals(emptyMap<UsageWindow, UsageNoticeDismissal>(), testee.dismissals.first())
    }

    @Test
    fun whenDataStoreWriteFailsThenDismissAndClearDoNotThrow() = runTest {
        val broken = UsageNoticeDismissalStore(FailingPreferencesDataStore())

        broken.dismiss(approaching(55))
        broken.clear()
    }

    @Test
    fun whenNoticeIsMarkedActedOnThenItIsPersistedUntilCleared() = runTest {
        val notice = approaching(100).copy(reached = true, dismissible = false)

        testee.markActedOn(notice)
        assertEquals(UsageNoticeActedOn(UsageNoticeId.APPROACHING, UsageWindow.WEEKLY, RESETS_AT, 100), testee.actedOn.first())

        testee.clearActedOn()
        assertNull(testee.actedOn.first())
    }

    @Test
    fun whenDataStoreReadFailsThenThereAreNoDismissals() = runTest {
        val failingStore: DataStore<Preferences> = mock()
        whenever(failingStore.data).thenReturn(flow { throw IOException("corrupt") })

        assertEquals(emptyMap<UsageWindow, UsageNoticeDismissal>(), UsageNoticeDismissalStore(failingStore).dismissals.first())
    }

    private fun approaching(percent: Int) = UsageNotice(
        id = UsageNoticeId.APPROACHING,
        window = UsageWindow.WEEKLY,
        percentUsed = percent,
        resetsAtMillis = RESETS_AT,
        reached = false,
        dismissible = true,
    )

    private companion object {
        const val RESETS_AT = 1788134400000L
    }
}
