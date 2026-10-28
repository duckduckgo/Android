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

import com.duckduckgo.app.global.model.PrivacyShield.MALICIOUS
import com.duckduckgo.app.global.model.PrivacyShield.PROTECTED
import com.duckduckgo.app.global.model.PrivacyShield.UNKNOWN
import com.duckduckgo.app.global.model.PrivacyShield.UNPROTECTED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RebrandPrivacyShieldLayoutTest {

    @Test
    fun whenShieldProtectedThenAnimationFillsSlot() {
        assertEquals(
            RebrandPrivacyShieldLayout(slotSizeDp = 44, contentInsetDp = 0),
            resolveRebrandPrivacyShieldLayout(PROTECTED),
        )
    }

    @Test
    fun whenShieldUnprotectedThenIconIsCenteredInSlot() {
        assertEquals(
            RebrandPrivacyShieldLayout(slotSizeDp = 44, contentInsetDp = 2),
            resolveRebrandPrivacyShieldLayout(UNPROTECTED),
        )
    }

    @Test
    fun whenShieldMaliciousThenIconIsCenteredInSlot() {
        assertEquals(
            RebrandPrivacyShieldLayout(slotSizeDp = 44, contentInsetDp = 2),
            resolveRebrandPrivacyShieldLayout(MALICIOUS),
        )
    }

    @Test
    fun whenShieldUnknownThenRebrandLayoutIsNotApplied() {
        assertNull(resolveRebrandPrivacyShieldLayout(UNKNOWN))
    }
}
