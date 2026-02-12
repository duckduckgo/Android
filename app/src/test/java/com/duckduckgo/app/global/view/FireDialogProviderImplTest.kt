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

package com.duckduckgo.app.global.view

import com.duckduckgo.app.global.view.FireDialogProvider.FireDialogOrigin
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FireDialogProviderImplTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val fakeAndroidBrowserConfigFeature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)

    private lateinit var testee: FireDialogProviderImpl

    @Before
    fun setup() {
        testee = FireDialogProviderImpl(
            androidBrowserConfigFeature = fakeAndroidBrowserConfigFeature,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun `when singleTabFireDialog enabled then return dialog`() = runTest {
        fakeAndroidBrowserConfigFeature.singleTabFireDialog().setRawStoredState(State(true))
        fakeAndroidBrowserConfigFeature.granularFireDialog().setRawStoredState(State(false))
        fakeAndroidBrowserConfigFeature.improvedDataClearingOptions().setRawStoredState(State(false))

        val dialog = testee.createFireDialog(FireDialogOrigin.BROWSER)

        // Currently returns NonGranularFireDialog as stub; will be replaced with SingleTabFireDialog
        assertTrue(dialog is NonGranularFireDialog)
    }

    @Test
    fun `when singleTabFireDialog enabled then takes priority over granular flag`() = runTest {
        fakeAndroidBrowserConfigFeature.singleTabFireDialog().setRawStoredState(State(true))
        fakeAndroidBrowserConfigFeature.granularFireDialog().setRawStoredState(State(true))
        fakeAndroidBrowserConfigFeature.improvedDataClearingOptions().setRawStoredState(State(false))

        val dialog = testee.createFireDialog(FireDialogOrigin.BROWSER)

        // singleTabFireDialog takes priority, so result should NOT be GranularFireDialog
        assertTrue(dialog is NonGranularFireDialog)
    }

    @Test
    fun `when granularFireDialog enabled then return GranularFireDialog`() = runTest {
        fakeAndroidBrowserConfigFeature.singleTabFireDialog().setRawStoredState(State(false))
        fakeAndroidBrowserConfigFeature.granularFireDialog().setRawStoredState(State(true))
        fakeAndroidBrowserConfigFeature.improvedDataClearingOptions().setRawStoredState(State(false))

        val dialog = testee.createFireDialog(FireDialogOrigin.BROWSER)

        assertTrue(dialog is GranularFireDialog)
    }

    @Test
    fun `when improvedDataClearingOptions enabled then return NonGranularFireDialog`() = runTest {
        fakeAndroidBrowserConfigFeature.singleTabFireDialog().setRawStoredState(State(false))
        fakeAndroidBrowserConfigFeature.granularFireDialog().setRawStoredState(State(false))
        fakeAndroidBrowserConfigFeature.improvedDataClearingOptions().setRawStoredState(State(true))

        val dialog = testee.createFireDialog(FireDialogOrigin.BROWSER)

        assertTrue(dialog is NonGranularFireDialog)
    }

    @Test
    fun `when all flags disabled then return LegacyFireDialog`() = runTest {
        fakeAndroidBrowserConfigFeature.singleTabFireDialog().setRawStoredState(State(false))
        fakeAndroidBrowserConfigFeature.granularFireDialog().setRawStoredState(State(false))
        fakeAndroidBrowserConfigFeature.improvedDataClearingOptions().setRawStoredState(State(false))

        val dialog = testee.createFireDialog(FireDialogOrigin.BROWSER)

        assertTrue(dialog is LegacyFireDialog)
    }
}
