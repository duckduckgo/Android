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

import com.duckduckgo.app.browser.R
import com.duckduckgo.app.privacy.model.PrivacyGrade
import org.junit.Assert.assertEquals
import org.junit.Test

class PrivacyGradeRendererExtensionTest {

    @Test
    fun whenGradeIsAThenIconReflectsSame() {
        assertEquals(R.drawable.privacygrade_icon_a, PrivacyGrade.A.icon())
    }

    @Test
    fun whenGradeIsBThenIconReflectsSame() {
        assertEquals(R.drawable.privacygrade_icon_b, PrivacyGrade.B.icon())
    }

    @Test
    fun whenGradeIsCThenIconReflectsSame() {
        assertEquals(R.drawable.privacygrade_icon_c, PrivacyGrade.C.icon())
    }

    @Test
    fun whenGradeIsDThenIconReflectsSame() {
        assertEquals(R.drawable.privacygrade_icon_d, PrivacyGrade.D.icon())
    }

    @Test
    fun whenGradeIsUnknownThenIconReflectsSame() {
        assertEquals(R.drawable.privacygrade_icon_unknown, PrivacyGrade.UNKNOWN.icon())
    }

    @Test
    fun whenGradeIsAThenSmallIconReflectsSame() {
        assertEquals(R.drawable.privacygrade_icon_small_a, PrivacyGrade.A.smallIcon())
    }

    @Test
    fun whenGradeIsBThenSmallIconReflectsSame() {
        assertEquals(R.drawable.privacygrade_icon_small_b, PrivacyGrade.B.smallIcon())
    }

    @Test
    fun whenGradeIsCThenSmallIconReflectsSame() {
        assertEquals(R.drawable.privacygrade_icon_small_c, PrivacyGrade.C.smallIcon())
    }

    @Test
    fun whenGradeIsDThenSmallIconReflectsSame() {
        assertEquals(R.drawable.privacygrade_icon_small_d, PrivacyGrade.D.smallIcon())
    }

    @Test
    fun whenGradeIsUnknownThenSmallIconIsZero() {
        assertEquals(0, PrivacyGrade.UNKNOWN.smallIcon())
    }

    @Test
    fun whenGradeIsAAndPrivacyOnThenBannerReflectsSame() {
        assertEquals(R.drawable.privacygrade_banner_a_on, PrivacyGrade.A.banner(true))
    }

    @Test
    fun whenGradeIsBAndPrivacyOnThenBannerReflectsSame() {
        assertEquals(R.drawable.privacygrade_banner_b_on, PrivacyGrade.B.banner(true))
    }

    @Test
    fun whenGradeIsCAndPrivacyOnThenBannerReflectsSame() {
        assertEquals(R.drawable.privacygrade_banner_c_on, PrivacyGrade.C.banner(true))
    }

    @Test
    fun whenGradeIsDAndPrivacyOnThenBannerReflectsSame() {
        assertEquals(R.drawable.privacygrade_banner_d_on, PrivacyGrade.D.banner(true))
    }

    @Test
    fun whenGradeIsUnknownAndPrivacyOnThenBannerIsUnknown() {
        assertEquals(R.drawable.privacygrade_banner_unknown, PrivacyGrade.UNKNOWN.banner(true))
    }

    @Test
    fun whenGradeIsAAndPrivacyOffThenBannerReflectsSame() {
        assertEquals(R.drawable.privacygrade_banner_a_off, PrivacyGrade.A.banner(false))
    }

    @Test
    fun whenGradeIsBAndPrivacyOffThenBannerReflectsSame() {
        assertEquals(R.drawable.privacygrade_banner_b_off, PrivacyGrade.B.banner(false))
    }

    @Test
    fun whenGradeIsCAndPrivacyOffThenBannerReflectsSame() {
        assertEquals(R.drawable.privacygrade_banner_c_off, PrivacyGrade.C.banner(false))
    }

    @Test
    fun whenGradeIsDAndPrivacyOffThenBannerReflectsSame() {
        assertEquals(R.drawable.privacygrade_banner_d_off, PrivacyGrade.D.banner(false))
    }

    @Test
    fun whenGradeIsUnknownAndPrivacyOffThenBannerIsUnknown() {
        assertEquals(R.drawable.privacygrade_banner_unknown, PrivacyGrade.UNKNOWN.banner(false))
    }
}
