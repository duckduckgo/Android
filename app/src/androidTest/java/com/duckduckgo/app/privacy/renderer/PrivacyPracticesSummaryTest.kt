/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.privacy.renderer

import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.privacy.model.PrivacyPractices.Summary.*
import org.junit.Assert.assertEquals
import org.junit.Test

class PrivacyPracticesSummaryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun whenTermsAreGoodThenBannerIsGood() {
        val testee = GOOD
        assertEquals(R.drawable.practices_banner_good, testee.banner())
    }

    @Test
    fun whenTermsArePoorThenBannerIsPoor() {
        val testee = POOR
        assertEquals(R.drawable.practices_banner_bad, testee.banner())
    }

    @Test
    fun whenTermsAreMixedThenBannerNeutral() {
        val testee = MIXED
        assertEquals(R.drawable.practices_banner_neutral, testee.banner())
    }

    @Test
    fun whenTermsAreUnknownThenBannerIsNeutral() {
        val testee = UNKNOWN
        assertEquals(R.drawable.practices_banner_neutral, testee.banner())
    }

    @Test
    fun whenTermsAreGoodThenIconIsGood() {
        val testee = GOOD
        assertEquals(R.drawable.practices_icon_good, testee.icon())
    }

    @Test
    fun whenTermsArePoorThenIconIsPoor() {
        val testee = POOR
        assertEquals(R.drawable.practices_icon_bad, testee.icon())
    }

    @Test
    fun whenTermsAreMixedThenIconNeutral() {
        val testee = MIXED
        assertEquals(R.drawable.practices_icon_neutral, testee.icon())
    }

    @Test
    fun whenTermsAreUnknownThenIconIsNeutral() {
        val testee = UNKNOWN
        assertEquals(R.drawable.practices_icon_neutral, testee.icon())
    }

    @Test
    fun whenTermsAreGoodThenSuccessFailureIconIsSuccess() {
        val testee = GOOD
        assertEquals(R.drawable.icon_success, testee.successFailureIcon())
    }

    @Test
    fun whenTermsArePoorThenSuccessFailureIconIsFailure() {
        val testee = POOR
        assertEquals(R.drawable.icon_fail, testee.successFailureIcon())
    }

    @Test
    fun whenTermsAreMixedThenSuccessFailureIconIsFailure() {
        val testee = MIXED
        assertEquals(R.drawable.icon_fail, testee.successFailureIcon())
    }

    @Test
    fun whenTermsAreUnknownThenSuccessFailureIconIsFailure() {
        val testee = UNKNOWN
        assertEquals(R.drawable.icon_fail, testee.successFailureIcon())
    }

    @Test
    fun whenTermsAreGoodThenTextReflectsSame() {
        val testee = GOOD
        assertEquals(context.getString(R.string.practicesGood), testee.text(context))
    }

    @Test
    fun whenTermsArePoorThenTextReflectsSame() {
        val testee = POOR
        assertEquals(context.getString(R.string.practicesBad), testee.text(context))
    }

    @Test
    fun whenTermsAreMixedThenTextReflectsSame() {
        val testee = MIXED
        assertEquals(context.getString(R.string.practicesMixed), testee.text(context))
    }

    @Test
    fun whenTermsAreUnknownThenTextReflectsSame() {
        val testee = UNKNOWN
        assertEquals(context.getString(R.string.practicesUnknown), testee.text(context))
    }
}
