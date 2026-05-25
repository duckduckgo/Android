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

package com.duckduckgo.adblocking.impl.domain

import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionFeature
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class RealAdBlockingStatusCheckerTest {

    private var killSwitchEnabled = true
    private var contingencyModeEnabled = false

    private val selfToggle: Toggle = mock {
        on { isEnabled() } doAnswer { killSwitchEnabled }
    }
    private val contingencyModeToggle: Toggle = mock {
        on { isEnabled() } doAnswer { contingencyModeEnabled }
    }
    private val feature: AdBlockingExtensionFeature = mock {
        on { self() } doReturn selfToggle
        on { enableContingencyMode() } doReturn contingencyModeToggle
    }

    private val checker = RealAdBlockingStatusChecker(feature)

    @Test
    fun whenKillSwitchIsOnAndContingencyModeIsOffThenCanInject() {
        assertTrue(checker.canInject())
    }

    @Test
    fun whenKillSwitchIsOffThenCannotInject() {
        killSwitchEnabled = false

        assertFalse(checker.canInject())
    }

    @Test
    fun whenContingencyModeIsOnThenCannotInject() {
        contingencyModeEnabled = true

        assertFalse(checker.canInject())
    }
}
