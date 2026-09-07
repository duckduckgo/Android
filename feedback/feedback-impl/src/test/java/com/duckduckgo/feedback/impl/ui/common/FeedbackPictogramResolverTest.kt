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

package com.duckduckgo.feedback.impl.ui.common

import com.duckduckgo.feedback.impl.R
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedbackPictogramResolverTest {

    @Test
    fun whenPictogramsEnabledThenButtonAssetIsThemeIndependent() {
        assertEquals(
            R.drawable.response_good_56,
            resolveFeedbackButtonAsset(isPositive = true, isLightMode = true, isPictogramsEnabled = true),
        )
        assertEquals(
            R.drawable.response_good_56,
            resolveFeedbackButtonAsset(isPositive = true, isLightMode = false, isPictogramsEnabled = true),
        )
        assertEquals(
            R.drawable.response_bad_56,
            resolveFeedbackButtonAsset(isPositive = false, isLightMode = true, isPictogramsEnabled = true),
        )
        assertEquals(
            R.drawable.response_bad_56,
            resolveFeedbackButtonAsset(isPositive = false, isLightMode = false, isPictogramsEnabled = true),
        )
    }

    @Test
    fun whenPictogramsDisabledThenButtonAssetsAreUnchanged() {
        assertEquals(
            R.drawable.button_happy_light_theme,
            resolveFeedbackButtonAsset(isPositive = true, isLightMode = true, isPictogramsEnabled = false),
        )
        assertEquals(
            R.drawable.button_happy_dark_theme,
            resolveFeedbackButtonAsset(isPositive = true, isLightMode = false, isPictogramsEnabled = false),
        )
        assertEquals(
            R.drawable.button_sad_light_theme,
            resolveFeedbackButtonAsset(isPositive = false, isLightMode = true, isPictogramsEnabled = false),
        )
        assertEquals(
            R.drawable.button_sad_dark_theme,
            resolveFeedbackButtonAsset(isPositive = false, isLightMode = false, isPictogramsEnabled = false),
        )
    }

    @Test
    fun whenPictogramsEnabledThenFaceAssetMatchesResponse() {
        assertEquals(
            R.drawable.response_good_56,
            resolveFeedbackFaceAsset(isPositive = true, isPictogramsEnabled = true),
        )
        assertEquals(
            R.drawable.response_bad_56,
            resolveFeedbackFaceAsset(isPositive = false, isPictogramsEnabled = true),
        )
    }

    @Test
    fun whenPictogramsDisabledThenFaceAssetsAreUnchanged() {
        assertEquals(
            R.drawable.ic_happy_face,
            resolveFeedbackFaceAsset(isPositive = true, isPictogramsEnabled = false),
        )
        assertEquals(
            R.drawable.ic_sad_face,
            resolveFeedbackFaceAsset(isPositive = false, isPictogramsEnabled = false),
        )
    }
}
