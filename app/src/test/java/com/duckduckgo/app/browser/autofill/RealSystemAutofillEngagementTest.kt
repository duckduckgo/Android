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

package com.duckduckgo.app.browser.autofill

import android.annotation.SuppressLint
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SYSTEM_AUTOFILL_USED
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@SuppressLint("DenyListedApi")
class RealSystemAutofillEngagementTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()
    private val pixel: Pixel = mock()
    private val dispatchers: DispatcherProvider = coroutineTestRule.testDispatcherProvider
    private val feature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)

    private lateinit var testee: RealSystemAutofillEngagement

    @Before
    fun setup() {
        testee = RealSystemAutofillEngagement(
            appCoroutineScope = coroutineTestRule.testScope,
            dispatchers = dispatchers,
            autofillFeature = feature,
            pixel = pixel,
        )
    }

    @Test
    fun whenFeatureEnabledThenPixelFired() = runTest {
        feature.canDetectSystemAutofillEngagement().setRawStoredState(State(true))
        testee.onSystemAutofillEvent()
        verify(pixel).fire(AUTOFILL_SYSTEM_AUTOFILL_USED, type = Daily())
    }

    @Test
    fun whenFeatureDisabledThenPixelNotFired() = runTest {
        feature.canDetectSystemAutofillEngagement().setRawStoredState(State(false))
        testee.onSystemAutofillEvent()
        verify(pixel, never()).fire(AUTOFILL_SYSTEM_AUTOFILL_USED, type = Daily())
    }
}
