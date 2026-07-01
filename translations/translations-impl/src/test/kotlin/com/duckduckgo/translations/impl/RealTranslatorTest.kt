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

package com.duckduckgo.translations.impl

import android.content.Context
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.translations.api.TranslationModelManager
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class RealTranslatorTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val modelStore: ModelStore = mock()
    private val modelManager: TranslationModelManager = mock()

    private fun translator() = RealTranslator(
        context = context,
        appBuildConfig = appBuildConfig,
        appCoroutineScope = coroutineRule.testScope,
        dispatchers = coroutineRule.testDispatcherProvider,
        modelStore = modelStore,
        modelManager = modelManager,
    )

    @Test
    fun whenSdkIsBelow28ThenIsAvailableIsFalse() {
        whenever(appBuildConfig.sdkInt).thenReturn(26)

        assertFalse(translator().isAvailable())
    }

    @Test
    fun whenSdkIsJustBelow28ThenIsAvailableIsFalse() {
        whenever(appBuildConfig.sdkInt).thenReturn(27)

        assertFalse(translator().isAvailable())
    }

    @Test
    fun whenSdkIsBelow28ThenConstructionNeverTouchesTheModelLayerOrCrashes() {
        whenever(appBuildConfig.sdkInt).thenReturn(26)

        translator()

        verifyNoInteractions(modelStore, modelManager)
    }
}
