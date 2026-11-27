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

package com.duckduckgo.pagetranslation.impl.translatekit

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.pagetranslation.impl.PageTranslationFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class TranslateKitLoaderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val feature = FakeFeatureToggleFactory.create(PageTranslationFeature::class.java)
    private val library = FakeTranslateKitLibrary()
    private val loader = RealTranslateKitLoader(library, feature, coroutineRule.testScope, coroutineRule.testDispatcherProvider)

    @Test
    fun initializeLibraryOnceWhenPagesFinishWithFlagOn() {
        setFlag(isEnabled = true)

        repeat(3) { loader.loadIfNeeded() }

        assertEquals(1, library.initCalls)
        assertTrue(loader.isAvailable())
    }

    @Test
    fun deferLoadingUntilFlagTurnsOn() {
        setFlag(isEnabled = false)

        loader.loadIfNeeded()

        assertEquals(0, library.initCalls)

        setFlag(isEnabled = true)
        loader.loadIfNeeded()

        assertEquals(1, library.initCalls)
        assertTrue(loader.isAvailable())
    }

    @Test
    fun reportUnavailableWhenFlagIsOffDespiteInitializedLibrary() {
        setFlag(isEnabled = true)
        loader.loadIfNeeded()

        setFlag(isEnabled = false)

        assertFalse(loader.isAvailable())
    }

    @Test
    fun reportUnavailableWhenLibraryHasNoNativeBinary() {
        library.isNativeBinaryAvailable = false
        setFlag(isEnabled = true)

        loader.loadIfNeeded()

        assertFalse(loader.isAvailable())
    }

    @Test
    fun reportUnavailableWithoutRetryWhenInitializationThrows() {
        library.isInitializationFailing = true
        setFlag(isEnabled = true)

        loader.loadIfNeeded()
        loader.loadIfNeeded()

        assertFalse(loader.isAvailable())
        assertEquals(1, library.initCalls)
    }

    private fun setFlag(isEnabled: Boolean) {
        feature.translation().setRawStoredState(State(enable = isEnabled))
    }

    private class FakeTranslateKitLibrary : TranslateKitLibrary {
        var isNativeBinaryAvailable = true
        var isInitializationFailing = false
        var initCalls = 0
        private var isLibraryInitialized = false

        override fun init() {
            initCalls++

            if (isInitializationFailing) throw IllegalStateException(INITIALIZATION_FAILURE_MESSAGE)

            isLibraryInitialized = isNativeBinaryAvailable
        }

        override fun isInitialized(): Boolean = isLibraryInitialized

        override fun decompressZstd(compressedFile: File, outputFile: File) = Unit
    }

    private companion object {
        const val INITIALIZATION_FAILURE_MESSAGE = "native init failed"
    }
}
