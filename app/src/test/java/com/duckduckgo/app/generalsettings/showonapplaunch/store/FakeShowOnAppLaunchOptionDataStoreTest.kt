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

package com.duckduckgo.app.generalsettings.showonapplaunch.store

import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.SpecificPage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeShowOnAppLaunchOptionDataStoreTest {

    private val testee = FakeShowOnAppLaunchOptionDataStore()

    @Test
    fun whenSpecificPageIsWrittenWithResolvedUrlThenOptionFlowClearsResolvedUrl() = runTest {
        testee.setShowOnAppLaunchOption(SpecificPage("https://example.com/", "https://resolved.example.com/"))

        assertEquals(SpecificPage("https://example.com/"), testee.optionFlow.first())
    }

    @Test
    fun whenSpecificPageUrlChangesThenOptionFlowReflectsUpdatedUrl() = runTest {
        testee.setShowOnAppLaunchOption(SpecificPage("https://example.com/"))
        testee.setSpecificPageUrl("https://updated.example.com/")

        assertEquals(SpecificPage("https://updated.example.com/"), testee.optionFlow.first())
    }

    @Test
    fun whenResolvedPageUrlChangesThenOptionFlowReflectsUpdatedResolvedUrl() = runTest {
        testee.setShowOnAppLaunchOption(SpecificPage("https://example.com/"))
        testee.setResolvedPageUrl("https://resolved.example.com/")

        assertEquals(
            SpecificPage("https://example.com/", "https://resolved.example.com/"),
            testee.optionFlow.first(),
        )
    }
}
