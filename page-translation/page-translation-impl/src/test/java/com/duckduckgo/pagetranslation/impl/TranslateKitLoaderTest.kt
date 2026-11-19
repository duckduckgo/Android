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

package com.duckduckgo.pagetranslation.impl

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TranslateKitLoaderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val feature = FakeFeatureToggleFactory.create(PageTranslationFeature::class.java)
    private val library = FakeTranslateKitLibrary()
    private val loader = TranslateKitLoader(library, feature, coroutineRule.testScope, coroutineRule.testDispatcherProvider)

    @Test
    fun whenPagesFinishWithFlagOnThenLibraryInitializedOnce() {
        setFlag(true)

        repeat(3) { loader.loadIfNeeded() }

        assertEquals(1, library.initCalls)
        assertTrue(loader.isAvailable())
    }

    @Test
    fun whenFlagOffThenNothingLoadsUntilFlagTurnsOn() {
        setFlag(false)
        loader.loadIfNeeded()
        assertEquals(0, library.initCalls)

        setFlag(true)
        loader.loadIfNeeded()

        assertEquals(1, library.initCalls)
        assertTrue(loader.isAvailable())
    }

    @Test
    fun whenFlagOffThenNotAvailableEvenIfLibraryInitialized() {
        setFlag(true)
        loader.loadIfNeeded()

        setFlag(false)

        assertFalse(loader.isAvailable())
    }

    @Test
    fun whenLibraryHasNoNativeBinaryThenNotAvailable() {
        library.hasNativeBinary = false
        setFlag(true)

        loader.loadIfNeeded()

        assertFalse(loader.isAvailable())
    }

    @Test
    fun whenInitThrowsThenNotAvailableAndNoRetry() {
        library.throwOnInit = true
        setFlag(true)

        loader.loadIfNeeded()
        loader.loadIfNeeded()

        assertFalse(loader.isAvailable())
        assertEquals(1, library.initCalls)
    }

    private fun setFlag(enabled: Boolean) {
        feature.translation().setRawStoredState(State(enabled))
    }

    private class FakeTranslateKitLibrary : TranslateKitLibrary {
        var hasNativeBinary = true
        var throwOnInit = false
        var initCalls = 0
        private var initialized = false

        override fun init() {
            initCalls++
            if (throwOnInit) throw IllegalStateException("native init failed")
            initialized = hasNativeBinary
        }

        override fun isInitialized(): Boolean = initialized
    }
}
