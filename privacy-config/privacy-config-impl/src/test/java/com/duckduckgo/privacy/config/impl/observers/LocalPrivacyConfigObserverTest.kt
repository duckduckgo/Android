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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.privacy.config.impl.FileUtilities.loadResource
import com.duckduckgo.privacy.config.impl.PrivacyConfigPersister
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class LocalPrivacyConfigObserverTest {

    @get:Rule var coroutineRule = CoroutineTestRule()

    private val mockPrivacyConfigPersister: PrivacyConfigPersister = mock()
    private val mockContext: Context = mock()
    lateinit var testee: LocalPrivacyConfigObserver

    @Before
    fun before() {
        testee =
            LocalPrivacyConfigObserver(
                mockContext,
                mockPrivacyConfigPersister,
                TestScope(),
                coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenOnCreateApplicationThenCallPersistPrivacyConfig() =
        runTest {
            givenLocalPrivacyConfigFileExists()

            testee.storeLocalPrivacyConfig()

            verify(mockPrivacyConfigPersister).persistPrivacyConfig(any())
        }

    private fun givenLocalPrivacyConfigFileExists() {
        val resources: Resources = mock()
        whenever(mockContext.resources).thenReturn(resources)
        whenever(resources.openRawResource(any()))
            .thenReturn(loadResource("json/privacy_config.json"))
    }
}
