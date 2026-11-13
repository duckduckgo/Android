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

package com.duckduckgo.sync.impl.promotion.devicemigration

import com.duckduckgo.devicemigration.api.DeviceMigration
import com.duckduckgo.sync.api.DeviceSyncState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealSyncAfterDeviceTransferPromptTest {

    private val deviceMigration = FakeDeviceMigration()
    private val deviceSyncState: DeviceSyncState = mock<DeviceSyncState>().apply {
        whenever(isFeatureEnabled()).thenReturn(true)
        whenever(isUserSignedInOnDevice()).thenReturn(false)
    }
    private fun newPrompt() = RealSyncAfterDeviceTransferPrompt(deviceMigration, deviceSyncState)

    @Test
    fun whenMigratedInstallWithoutSyncOnEitherPhoneAndFlowFinishedThenShows() = runTest {
        assertTrue(newPrompt().shouldShow())
    }

    @Test
    fun whenInstallIsNotMigratedThenDoesNotShow() = runTest {
        deviceMigration.migrated = false

        assertFalse(newPrompt().shouldShow())
    }

    @Test
    fun whenPreviousPhoneHadSyncOrItIsNotKnownYetThenDoesNotShow() = runTest {
        deviceMigration.previousDeviceHadSync = true

        assertFalse(newPrompt().shouldShow())
    }

    @Test
    fun whenSyncIsAlreadyOnThisPhoneThenDoesNotShow() = runTest {
        whenever(deviceSyncState.isUserSignedInOnDevice()).thenReturn(true)

        assertFalse(newPrompt().shouldShow())
    }

    @Test
    fun whenSyncFeatureIsUnavailableThenDoesNotShow() = runTest {
        whenever(deviceSyncState.isFeatureEnabled()).thenReturn(false)

        assertFalse(newPrompt().shouldShow())
    }

    @Test
    fun whenMigrationFlowIsStillPendingThenDoesNotShow() = runTest {
        deviceMigration.flowPending = true

        assertFalse(newPrompt().shouldShow())
    }

    @Test
    fun whenFeatureDisabledThenDoesNotShow() = runTest {
        deviceMigration.experienceEnabled = false

        assertFalse(newPrompt().shouldShow())
    }

    @Test
    fun whenDismissedThenDoesNotShow() = runTest {
        val prompt = newPrompt()

        prompt.dismiss()

        assertFalse(prompt.shouldShow())
    }

    @Test
    fun whenDismissedThenStaysHiddenForANewInstance() = runTest {
        newPrompt().dismiss()

        assertFalse(newPrompt().shouldShow())
    }

    @Test
    fun dismissalIsADoneMarkOnThisInstall() = runTest {
        newPrompt().dismiss()

        assertTrue(deviceMigration.done.isNotEmpty())
    }

    private class FakeDeviceMigration : DeviceMigration {
        var migrated = true
        var flowPending = false
        var previousDeviceHadSync = false
        var experienceEnabled = true

        override suspend fun isMigratedInstall(): Boolean = migrated
        override suspend fun isMigrationFlowPending(): Boolean = flowPending
        override fun sourceDeviceIsNewInstall(): Boolean? = if (migrated) false else null
        override suspend fun sourceDeviceWasDefaultBrowser(): Boolean? = null
        override suspend fun markMigrationFlowCompleted() {
            flowPending = false
        }
        override suspend fun previousDeviceHadSync(): Boolean = migrated && previousDeviceHadSync
        override suspend fun isMigrationExperienceEnabled(): Boolean = experienceEnabled

        val done = mutableSetOf<String>()
        override fun isDoneOnThisInstall(key: String): Boolean = key in done
        override fun markDoneOnThisInstall(key: String) {
            done += key
        }
    }
}
