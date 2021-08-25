/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.privacy.config.impl.observers

import android.content.Context
import android.content.res.Resources
import com.duckduckgo.privacy.config.impl.FileUtilities.loadResource
import com.duckduckgo.privacy.config.impl.PrivacyConfigPersister
import com.duckduckgo.privacy.config.impl.PrivacyCoroutineTestRule
import com.duckduckgo.privacy.config.impl.runBlocking
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LocalPrivacyConfigObserverTest {

    @get:Rule
    var coroutineRule = PrivacyCoroutineTestRule()

    private val mockPrivacyConfigPersister: PrivacyConfigPersister = mock()
    private val mockContext: Context = mock()
    lateinit var testee: LocalPrivacyConfigObserver

    @Before
    fun before() {
        testee = LocalPrivacyConfigObserver(mockContext, mockPrivacyConfigPersister, TestCoroutineScope(), coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenOnCreateApplicationThenCallPersistPrivacyConfig() = coroutineRule.runBlocking {
        givenLocalPrivacyConfigFileExists()

        testee.storeLocalPrivacyConfig()

        verify(mockPrivacyConfigPersister).persistPrivacyConfig(any())
    }

    private fun givenLocalPrivacyConfigFileExists() {
        val resources: Resources = mock()
        whenever(mockContext.resources).thenReturn(resources)
        whenever(resources.openRawResource(any())).thenReturn(loadResource("json/privacy_config.json"))
    }
}
