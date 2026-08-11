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

package com.duckduckgo.app.browser.omnibar

import com.google.android.material.card.MaterialCardView
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class AddressBarRebrandRadiusTest {
    @Test
    fun whenRebrandEnabledThenRadiusAppliedToEveryCard() {
        val outerCard = mock<MaterialCardView>()
        val innerCard = mock<MaterialCardView>()

        applyAddressBarRebrandRadius(true, 48f, 16f, outerCard, innerCard)

        verify(outerCard).radius = 48f
        verify(innerCard).radius = 48f
    }

    @Test
    fun whenRebrandDisabledThenLegacyRadiusAppliedToEveryCard() {
        val outerCard = mock<MaterialCardView>()
        val innerCard = mock<MaterialCardView>()

        applyAddressBarRebrandRadius(false, 48f, 16f, outerCard, innerCard)

        verify(outerCard).radius = 16f
        verify(innerCard).radius = 16f
    }
}
