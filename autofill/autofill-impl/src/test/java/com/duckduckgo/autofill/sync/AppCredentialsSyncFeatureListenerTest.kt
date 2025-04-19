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

package com.duckduckgo.autofill.sync

import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.FileUtilities
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.sync.api.SyncActivityWithEmptyParams
import com.duckduckgo.sync.api.engine.FeatureSyncError.COLLECTION_LIMIT_REACHED
import com.duckduckgo.sync.api.engine.SyncChangesResponse
import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS
import com.duckduckgo.sync.api.engine.SyncableType.CREDENTIALS
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AppCredentialsSyncFeatureListenerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()
    private val realContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val notificationManager = NotificationManagerCompat.from(realContext)
    private val credentialsSyncStore = RealCredentialsSyncStore(realContext, coroutineRule.testScope, coroutineRule.testDispatcherProvider)
    private val globalActivityStarterMock = mock<GlobalActivityStarter>().apply {
        whenever(this.startIntent(realContext, SyncActivityWithEmptyParams)).thenReturn(Intent())
    }

    private val testee = AppCredentialsSyncFeatureListener(
        realContext,
        credentialsSyncStore,
        notificationManager,
        AppCredentialsSyncNotificationBuilder(globalActivityStarterMock),
    )

    @Test
    fun whenNoValuesThenIsSyncPausedIsFalse() {
        assertFalse(credentialsSyncStore.isSyncPaused)
    }

    @Test
    fun whenSyncPausedAndOnSuccessWithChangesThenIsSyncPausedIsFalse() {
        credentialsSyncStore.isSyncPaused = true
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/sync/merger_first_get.json")
        val validChanges = SyncChangesResponse(CREDENTIALS, updatesJSON)

        testee.onSuccess(validChanges)

        assertFalse(credentialsSyncStore.isSyncPaused)
        assertTrue(credentialsSyncStore.syncPausedReason.isEmpty())
    }

    @Test
    fun whenSyncPausedAndOnSuccessWithoutChangesThenSyncPaused() {
        credentialsSyncStore.isSyncPaused = true
        val validChanges = SyncChangesResponse.empty(BOOKMARKS)

        testee.onSuccess(validChanges)

        assertTrue(credentialsSyncStore.isSyncPaused)
    }

    @Test
    fun whenSyncPausedAndOnErrorThenSyncPaused() {
        credentialsSyncStore.isSyncPaused = true

        testee.onError(COLLECTION_LIMIT_REACHED)

        assertTrue(credentialsSyncStore.isSyncPaused)
        assertEquals(COLLECTION_LIMIT_REACHED.name, credentialsSyncStore.syncPausedReason)
    }

    @Test
    fun whenSyncActiveAndOnErrorThenSyncPaused() {
        credentialsSyncStore.isSyncPaused = false

        testee.onError(COLLECTION_LIMIT_REACHED)

        assertTrue(credentialsSyncStore.isSyncPaused)
        assertEquals(COLLECTION_LIMIT_REACHED.name, credentialsSyncStore.syncPausedReason)
    }

    @Test
    fun whenOnSyncDisabledThenSyncPausedFalse() {
        credentialsSyncStore.isSyncPaused = true

        testee.onSyncDisabled()

        assertFalse(credentialsSyncStore.isSyncPaused)
        assertTrue(credentialsSyncStore.syncPausedReason.isEmpty())
    }
}
