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

package com.duckduckgo.app.privacymonitor.renderer

import android.support.annotation.DrawableRes
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.privacymonitor.model.PrivacyGrade

@DrawableRes
fun PrivacyGrade.icon(): Int {
    return when (this) {
        PrivacyGrade.A -> R.drawable.privacygrade_icon_a
        PrivacyGrade.B -> R.drawable.privacygrade_icon_b
        PrivacyGrade.C -> R.drawable.privacygrade_icon_c
        PrivacyGrade.D -> R.drawable.privacygrade_icon_d
        PrivacyGrade.UNKNOWN -> R.drawable.privacygrade_icon_unknown
    }
}

@DrawableRes
fun PrivacyGrade.smallIcon(): Int {
    return when (this) {
        PrivacyGrade.A -> R.drawable.privacygrade_icon_small_a
        PrivacyGrade.B -> R.drawable.privacygrade_icon_small_b
        PrivacyGrade.C -> R.drawable.privacygrade_icon_small_c
        PrivacyGrade.D -> R.drawable.privacygrade_icon_small_d
        PrivacyGrade.UNKNOWN -> 0
    }
}

@DrawableRes
fun PrivacyGrade.banner(privacyOn: Boolean): Int {
    if (privacyOn) {
        return privacyOnBanner()
    }
    return privacyOffBanner()
}

@DrawableRes
private fun PrivacyGrade.privacyOnBanner(): Int {
    return when (this) {
        PrivacyGrade.A -> R.drawable.privacygrade_banner_a_on
        PrivacyGrade.B -> R.drawable.privacygrade_banner_b_on
        PrivacyGrade.C -> R.drawable.privacygrade_banner_c_on
        PrivacyGrade.D -> R.drawable.privacygrade_banner_d_on
        PrivacyGrade.UNKNOWN -> R.drawable.privacygrade_banner_unknown
    }
}

@DrawableRes
private fun PrivacyGrade.privacyOffBanner(): Int {
    return when (this) {
        PrivacyGrade.A -> R.drawable.privacygrade_banner_a_off
        PrivacyGrade.B -> R.drawable.privacygrade_banner_b_off
        PrivacyGrade.C -> R.drawable.privacygrade_banner_c_off
        PrivacyGrade.D -> R.drawable.privacygrade_banner_d_off
        PrivacyGrade.UNKNOWN -> R.drawable.privacygrade_banner_unknown
    }
}


