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

package com.duckduckgo.devicemigration.impl

import android.content.SharedPreferences
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_DEFAULT_BROWSER
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_IS_NEW_INSTALL
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_SEEN
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_SIGNED_IN_TO_SYNC
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.TRANSFERRED_FILE
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.api.DeviceSyncState
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class MigratedInstallPixelsTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val prefsByName = mutableMapOf<String, SharedPreferences>()
    private val provider = object : SharedPreferencesProvider by FakeSharedPreferencesProvider() {
        override fun getSharedPreferences(name: String, multiprocess: Boolean, migrate: Boolean): SharedPreferences =
            prefsByName.getOrPut(name) { InMemorySharedPreferences() }
    }
    private val transferred get() = provider.getSharedPreferences(TRANSFERRED_FILE)

    private val feature = FakeFeatureToggleFactory.create(DeviceMigrationFeature::class.java).apply {
        self().setRawStoredState(State(enable = true))
    }
    private var isDefaultBrowser = false
    private val defaultBrowserDetector: DefaultBrowserDetector = mock {
        on { isDefaultBrowser() } doAnswer { isDefaultBrowser }
    }
    private var signedInToSync = false
    private val deviceSyncState: DeviceSyncState = mock {
        on { isUserSignedInOnDevice() } doAnswer { signedInToSync }
    }
    private val statisticsDataStore: StatisticsDataStore = mock()
    private var now = 1_000_000_000_000L
    private val timeProvider = object : CurrentTimeProvider {
        override fun elapsedRealtime() = 0L
        override fun currentTimeMillis() = now
        override fun localDateTimeNow(): LocalDateTime = LocalDateTime.now()
    }
    private val pixel: Pixel = mock()

    // Each start is a new process: detection and the stamps are read again from disk.
    private fun start() {
        val markerStore = DeviceMigrationMarkerStore(provider)
        val deviceMigration = RealDeviceMigration(
            markerStore = markerStore,
            installTimeComparison = object : InstallTimeComparison {
                override fun isUnupdatedSinceFirstInstall() = true
            },
            deviceMigrationFeature = feature,
            statisticsDataStore = statisticsDataStore,
            deviceSyncState = lazyOf(deviceSyncState),
            defaultBrowserDetector = lazyOf(defaultBrowserDetector),
            dispatchers = coroutineRule.testDispatcherProvider,
            appCoroutineScope = coroutineRule.testScope,
        )
        MigratedInstallPixels(
            deviceMigration = deviceMigration,
            markerStore = markerStore,
            pixel = pixel,
            defaultBrowserDetector = lazyOf(defaultBrowserDetector),
            deviceSyncState = lazyOf(deviceSyncState),
            timeProvider = timeProvider,
            dispatchers = coroutineRule.testDispatcherProvider,
            appCoroutineScope = coroutineRule.testScope,
        ).onStart(mock<LifecycleOwner>())
    }

    @Test
    fun whenNotMigratedThenNoPixel() {
        start()

        verify(pixel, never()).enqueueFire(any<Pixel.PixelName>(), any(), any(), any())
    }

    @Test
    fun whenMigrationExperienceOffThenNoPixel() {
        feature.self().setRawStoredState(State(enable = false))
        transferFromPhone(defaultBrowser = true, signedInToSync = true)

        start()

        verify(pixel, never()).enqueueFire(any<Pixel.PixelName>(), any(), any(), any())
    }

    @Test
    fun whenMigratedThenDetectedPixelCarriesThePreviousPhoneState() {
        transferFromPhone(defaultBrowser = true, signedInToSync = false)

        start()

        verify(pixel).enqueueFire(
            DeviceMigrationPixelName.DETECTED,
            mapOf("old_phone_default_browser" to "true", "old_phone_sync" to "no"),
            type = Unique(),
        )
    }

    @Test
    fun whenPreviousPhoneLeftNoStampsThenDetectedPixelSaysUnknown() {
        transferred.edit().putBoolean(KEY_SEEN, true).putBoolean(KEY_IS_NEW_INSTALL, false).commit()

        start()

        verify(pixel).enqueueFire(
            DeviceMigrationPixelName.DETECTED,
            mapOf("old_phone_default_browser" to "unknown", "old_phone_sync" to "unknown"),
            type = Unique(),
        )
    }

    @Test
    fun whenStartedAgainThenDetectedPixelNotSentTwice() {
        transferFromPhone(defaultBrowser = true, signedInToSync = true)

        start()
        start()

        verify(pixel, times(1)).enqueueFire(eq(DeviceMigrationPixelName.DETECTED), any(), any(), any())
    }

    @Test
    fun whenLessThanSevenDaysSinceDetectionThenNoDay7Pixel() {
        transferFromPhone(defaultBrowser = true, signedInToSync = true)
        start()

        now += TimeUnit.DAYS.toMillis(7) - 1
        start()

        verify(pixel, never()).enqueueFire(eq(DeviceMigrationPixelName.DAY_7), any(), any(), any())
    }

    @Test
    fun whenSevenDaysSinceDetectionThenDay7PixelCarriesPreviousAndCurrentState() {
        transferFromPhone(defaultBrowser = true, signedInToSync = false)
        start()

        isDefaultBrowser = true
        signedInToSync = true
        now += TimeUnit.DAYS.toMillis(7)
        start()

        verify(pixel).enqueueFire(
            DeviceMigrationPixelName.DAY_7,
            mapOf(
                "old_phone_default_browser" to "true",
                "old_phone_sync" to "no",
                "is_default_browser" to "true",
                "sync_enabled" to "true",
            ),
            type = Unique(),
        )
    }

    @Test
    fun whenDay7PixelSentThenNotSentAgain() {
        transferFromPhone(defaultBrowser = false, signedInToSync = false)
        start()

        now += TimeUnit.DAYS.toMillis(8)
        start()
        now += TimeUnit.DAYS.toMillis(8)
        start()

        verify(pixel, times(1)).enqueueFire(eq(DeviceMigrationPixelName.DAY_7), any(), any(), any())
    }

    private fun transferFromPhone(defaultBrowser: Boolean, signedInToSync: Boolean) {
        transferred.edit()
            .putBoolean(KEY_SEEN, true)
            .putBoolean(KEY_IS_NEW_INSTALL, false)
            .putBoolean(KEY_DEFAULT_BROWSER, defaultBrowser)
            .putBoolean(KEY_SIGNED_IN_TO_SYNC, signedInToSync)
            .commit()
    }
}
