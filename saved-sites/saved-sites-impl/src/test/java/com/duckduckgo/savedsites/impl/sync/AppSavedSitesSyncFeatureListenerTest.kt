/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.savedsites.impl.sync

import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.FileUtilities
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.sync.api.SyncActivityWithEmptyParams
import com.duckduckgo.sync.api.engine.FeatureSyncError.COLLECTION_LIMIT_REACHED
import com.duckduckgo.sync.api.engine.FeatureSyncError.INVALID_REQUEST
import com.duckduckgo.sync.api.engine.SyncChangesResponse
import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AppSavedSitesSyncFeatureListenerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()
    private val realContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val notificationManager = NotificationManagerCompat.from(realContext)
    private val savedSitesSyncStore = RealSavedSitesSyncStore(realContext, coroutineRule.testScope, coroutineRule.testDispatcherProvider)
    private val globalActivityStarterMock = mock<GlobalActivityStarter>().apply {
        whenever(this.startIntent(realContext, SyncActivityWithEmptyParams)).thenReturn(Intent())
    }

    private val testee = AppSavedSitesSyncFeatureListener(
        realContext,
        savedSitesSyncStore,
        notificationManager,
        AppSavedSitesSyncNotificationBuilder(globalActivityStarterMock),
    )

    @Test
    fun whenNoValuesThenIsSyncPausedIsFalse() {
        assertFalse(savedSitesSyncStore.isSyncPaused)
    }

    @Test
    fun whenSyncPausedAndOnSuccessWithChangesThenIsSyncPausedIsFalse() {
        savedSitesSyncStore.isSyncPaused = true
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/merger_first_get.json")
        val validChanges = SyncChangesResponse(BOOKMARKS, updatesJSON)

        testee.onSuccess(validChanges)

        assertFalse(savedSitesSyncStore.isSyncPaused)
        assertTrue(savedSitesSyncStore.syncPausedReason.isEmpty())
    }

    @Test
    fun whenSyncPausedAndOnSuccessWithoutChangesThenSyncPaused() {
        savedSitesSyncStore.isSyncPaused = true
        val validChanges = SyncChangesResponse.empty(BOOKMARKS)

        testee.onSuccess(validChanges)

        assertTrue(savedSitesSyncStore.isSyncPaused)
    }

    @Test
    fun whenSyncPausedAndOnErrorThenSyncPaused() {
        savedSitesSyncStore.isSyncPaused = true

        testee.onError(COLLECTION_LIMIT_REACHED)

        assertTrue(savedSitesSyncStore.isSyncPaused)
        assertEquals(COLLECTION_LIMIT_REACHED.name, savedSitesSyncStore.syncPausedReason)
    }

    @Test
    fun whenSyncPausedAndNewErrorThenSyncPausedAndReasonUpdated() {
        savedSitesSyncStore.isSyncPaused = true

        testee.onError(INVALID_REQUEST)

        assertTrue(savedSitesSyncStore.isSyncPaused)
        assertEquals(INVALID_REQUEST.name, savedSitesSyncStore.syncPausedReason)
    }

    @Test
    fun whenSyncActiveAndOnErrorThenSyncPaused() {
        savedSitesSyncStore.isSyncPaused = false

        testee.onError(COLLECTION_LIMIT_REACHED)

        assertTrue(savedSitesSyncStore.isSyncPaused)
        assertEquals(COLLECTION_LIMIT_REACHED.name, savedSitesSyncStore.syncPausedReason)
    }

    @Test
    fun whenOnSyncDisabledThenSyncPausedFalse() {
        savedSitesSyncStore.isSyncPaused = true

        testee.onSyncDisabled()

        assertFalse(savedSitesSyncStore.isSyncPaused)
    }
}
