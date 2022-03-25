/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.health

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.mobile.android.vpn.feature.isBadHealthMitigationEnabled
import com.duckduckgo.mobile.android.vpn.model.AppHealthState
import com.duckduckgo.mobile.android.vpn.model.HealthEventType.BAD_HEALTH
import com.duckduckgo.mobile.android.vpn.model.HealthEventType.GOOD_HEALTH
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.store.AppHealthDatabase
import com.jakewharton.threetenabp.AndroidThreeTen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneOffset

@ExperimentalCoroutinesApi
class AppBadHealthStateHandlerTest {

    @get:Rule @Suppress("unused") val coroutineRule = CoroutineTestRule()

    @Mock private lateinit var deviceShieldPixels: DeviceShieldPixels
    @Mock private lateinit var appBuildConfig: AppBuildConfig
    @Mock private lateinit var featureToggle: FeatureToggle
    private val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    private lateinit var db: AppHealthDatabase
    private lateinit var appBadHealthStateHandler: AppBadHealthStateHandler

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)

        AndroidThreeTen.init(context)

        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppHealthDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)
        whenever(featureToggle.isBadHealthMitigationEnabled()).thenReturn(true)

        appBadHealthStateHandler = AppBadHealthStateHandler(
            context, appBuildConfig, db, featureToggle, deviceShieldPixels, coroutineRule.testDispatcherProvider, coroutineRule.testScope
        )
    }

    @Test
    fun whenOnAppHealthUpdateThenAlwaysReturnFalse() = runTest {
        assertFalse(appBadHealthStateHandler.onAppHealthUpdate(EMPTY_HEALTH_DATA))

        verifyNoInteractions(deviceShieldPixels)
    }

    @Test
    fun whenOnAppHealthUpdateWithBadHealthDataThenStoreInDbAndSendPixel() = runTest {
        assertTrue(appBadHealthStateHandler.onAppHealthUpdate(BAD_HEALTH_DATA))

        val state = db.appHealthDao().latestHealthStateByType(BAD_HEALTH)

        assertEquals(listOf("alert"), state?.alerts)
        assertEquals(
            "{\"alerts\":[\"alert\"],\"systemHealth\":{\"isBadHealth\":true,\"rawMetrics\"" +
                ":[{\"informational\":false,\"metrics\":{\"metric\":{\"isBadState\":true,\"isCritical\":false,\"value\":\"value\"}}" +
                ",\"name\":\"rawMetric\",\"redacted\":false}]}}",
            state?.healthDataJsonString
        )

        verify(deviceShieldPixels).sendHealthMonitorReport(any())
    }

    @Test
    fun whenOnAppHealthUpdateWithRedactedBadHealthDataThenSkipRedactedMetricsFromDbAndSendPixel() = runTest {
        assertTrue(appBadHealthStateHandler.onAppHealthUpdate(REDACTED_BAD_HEALTH_DATA))

        val state = db.appHealthDao().latestHealthStateByType(BAD_HEALTH)

        assertEquals(listOf("alert"), state?.alerts)
        assertEquals(
            "{\"alerts\":[\"alert\"],\"systemHealth\":{\"isBadHealth\":true,\"rawMetrics\":[]}}",
            state?.healthDataJsonString
        )

        verify(deviceShieldPixels).sendHealthMonitorReport(any())
    }

    @Test
    fun whenOnAppHealthUpdateWithGoodHealthDataThenStoreInDb() = runTest {
        assertFalse(appBadHealthStateHandler.onAppHealthUpdate(EMPTY_HEALTH_DATA))

        val state = db.appHealthDao().latestHealthStateByType(GOOD_HEALTH)

        assertEquals(GOOD_HEALTH, state?.type)
        assertEquals(listOf<String>(), state?.alerts)
    }

    @Test
    fun whenAlertResolvesByRestartSendPixel() = runTest {
        assertTrue(appBadHealthStateHandler.onAppHealthUpdate(BAD_HEALTH_DATA))
        assertFalse(appBadHealthStateHandler.onAppHealthUpdate(EMPTY_HEALTH_DATA))

        val state = db.appHealthDao().latestHealthStateByType(GOOD_HEALTH)
        assertEquals(GOOD_HEALTH, state?.type)

        verify(deviceShieldPixels).badHealthResolvedByRestart(any())
        verify(deviceShieldPixels, never()).badHealthResolvedItself(any())
    }

    @Test
    fun whenAlertResolvesSoonEnoughAfterRestartSendResolvedByRestartPixel() = runTest {
        db.appHealthDao().insert(
            AppHealthState(
                type = BAD_HEALTH,
                alerts = listOf("alert"),
                healthDataJsonString = "",
                restartedAtEpochSeconds = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) - 30
            )
        )

        assertFalse(appBadHealthStateHandler.onAppHealthUpdate(EMPTY_HEALTH_DATA))

        val state = db.appHealthDao().latestHealthStateByType(GOOD_HEALTH)
        assertEquals(GOOD_HEALTH, state?.type)

        verify(deviceShieldPixels).badHealthResolvedByRestart(any())
        verify(deviceShieldPixels, never()).badHealthResolvedItself(any())
    }

    @Test
    fun whenAlertResolvesWayAfterRestartSendResolvedItselfPixel() = runTest {
        db.appHealthDao().insert(
            AppHealthState(
                type = BAD_HEALTH,
                alerts = listOf("alert"),
                healthDataJsonString = "",
                restartedAtEpochSeconds = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) - 60
            )
        )

        assertFalse(appBadHealthStateHandler.onAppHealthUpdate(EMPTY_HEALTH_DATA))

        val state = db.appHealthDao().latestHealthStateByType(GOOD_HEALTH)
        assertEquals(GOOD_HEALTH, state?.type)

        verify(deviceShieldPixels).badHealthResolvedItself(any())
        verify(deviceShieldPixels, never()).badHealthResolvedByRestart(any())
    }

    companion object {
        private val EMPTY_HEALTH_DATA = AppHealthData(listOf(), SystemHealthData(false, listOf()))

        private val BAD_HEALTH_DATA =
            AppHealthData(
                listOf("alert"),
                SystemHealthData(
                    true,
                    listOf(
                        RawMetricsSubmission(
                            "rawMetric",
                            metrics = mapOf("metric" to Metric("value", isBadState = true)),
                            redacted = false
                        )
                    )
                )
            )

        private val REDACTED_BAD_HEALTH_DATA =
            AppHealthData(
                listOf("alert"),
                SystemHealthData(
                    true,
                    listOf(
                        RawMetricsSubmission(
                            "rawMetric",
                            metrics = mapOf("metric" to Metric("value", isBadState = true)),
                            redacted = true
                        )
                    )
                )
            )

        private val BAD_HEALTH_DATA_NO_ALERT =
            AppHealthData(
                listOf(),
                SystemHealthData(
                    true,
                    listOf(
                        RawMetricsSubmission(
                            "rawMetric",
                            metrics = mapOf("metric" to Metric("value", isBadState = true)),
                            redacted = false
                        )
                    )
                )
            )
    }
}
