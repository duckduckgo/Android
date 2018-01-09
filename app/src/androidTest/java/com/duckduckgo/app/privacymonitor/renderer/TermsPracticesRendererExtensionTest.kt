/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.privacymonitor.renderer

import android.support.test.InstrumentationRegistry
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.privacymonitor.model.TermsOfService
import com.duckduckgo.app.privacymonitor.renderer.banner
import com.duckduckgo.app.privacymonitor.renderer.icon
import com.duckduckgo.app.privacymonitor.renderer.text
import org.junit.Assert.assertEquals
import org.junit.Test

class TermsPracticesRendererExtensionTest {

    private val context = InstrumentationRegistry.getTargetContext()

    @Test
    fun whenTermsAreGoodThenBannerIsGood() {
        val testee = TermsOfService.Practices.GOOD
        assertEquals(R.drawable.practices_banner_good, testee.banner())
    }

    @Test
    fun whenTermsArePoorThenBannerIsPoor() {
        val testee = TermsOfService.Practices.POOR
        assertEquals(R.drawable.practices_banner_bad, testee.banner())
    }

    @Test
    fun whenTermsAreMixedThenBannerNeutral() {
        val testee = TermsOfService.Practices.MIXED
        assertEquals(R.drawable.practices_banner_neutral, testee.banner())
    }

    @Test
    fun whenTermsAreUnknownThenBannerIsNeutral() {
        val testee = TermsOfService.Practices.UNKNOWN
        assertEquals(R.drawable.practices_banner_neutral, testee.banner())
    }

    @Test
    fun whenTermsAreGoodThenIconIsGood() {
        val testee = TermsOfService.Practices.GOOD
        assertEquals(R.drawable.practices_icon_good, testee.icon())
    }

    @Test
    fun whenTermsArePoorThenIconIsPoor() {
        val testee = TermsOfService.Practices.POOR
        assertEquals(R.drawable.practices_icon_bad, testee.icon())
    }

    @Test
    fun whenTermsAreMixedThenIconNeutral() {
        val testee = TermsOfService.Practices.MIXED
        assertEquals(R.drawable.practices_icon_neutral, testee.icon())
    }

    @Test
    fun whenTermsAreUnknownThenIconIsNeutral() {
        val testee = TermsOfService.Practices.UNKNOWN
        assertEquals(R.drawable.practices_icon_neutral, testee.icon())
    }

    @Test
    fun whenTermsAreGoodThenTextReflectsSame() {
        val testee = TermsOfService.Practices.GOOD
        assertEquals(context.getString(R.string.practicesGood), testee.text(context))
    }

    @Test
    fun whenTermsArePoorThenTextReflectsSame() {
        val testee = TermsOfService.Practices.POOR
        assertEquals(context.getString(R.string.practicesBad), testee.text(context))
    }

    @Test
    fun whenTermsAreMixedThenTextReflectsSame() {
        val testee = TermsOfService.Practices.MIXED
        assertEquals(context.getString(R.string.practicesMixed), testee.text(context))
    }

    @Test
    fun whenTermsAreUnknownThenTextReflectsSame() {
        val testee = TermsOfService.Practices.UNKNOWN
        assertEquals(context.getString(R.string.practicesUnknown), testee.text(context))
    }
}