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

package com.duckduckgo.privacy.config.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.runBlocking
import com.duckduckgo.privacy.config.impl.models.JsonPrivacyConfig
import com.duckduckgo.privacy.config.impl.network.PrivacyConfigService
import com.duckduckgo.privacy.config.store.UnprotectedTemporaryEntity
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealPrivacyConfigDownloaderTest {

    @get:Rule var coroutineRule = CoroutineTestRule()

    lateinit var testee: RealPrivacyConfigDownloader

    private val mockPrivacyConfigPersister: PrivacyConfigPersister = mock()

    @Before
    fun before() {
        testee = RealPrivacyConfigDownloader(TestPrivacyConfigService(), mockPrivacyConfigPersister)
    }

    @Test
    fun whenDownloadIsNotSuccessfulThenReturnFalse() =
        coroutineRule.runBlocking {
            testee =
                RealPrivacyConfigDownloader(
                    TestFailingPrivacyConfigService(), mockPrivacyConfigPersister)
            assertFalse(testee.download())
        }

    @Test
    fun whenDownloadIsSuccessfulThenReturnTrue() =
        coroutineRule.runBlocking { assertTrue(testee.download()) }

    @Test
    fun whenDownloadIsSuccessfulThenPersistPrivacyConfigCalled() =
        coroutineRule.runBlocking {
            testee.download()

            verify(mockPrivacyConfigPersister).persistPrivacyConfig(any())
        }

    class TestFailingPrivacyConfigService : PrivacyConfigService {
        override suspend fun privacyConfig(): JsonPrivacyConfig {
            throw Exception()
        }
    }

    class TestPrivacyConfigService : PrivacyConfigService {
        override suspend fun privacyConfig(): JsonPrivacyConfig {
            return JsonPrivacyConfig(
                version = 1,
                readme = "readme",
                features = mapOf(FEATURE_NAME to JSONObject(FEATURE_JSON)),
                unprotectedTemporaryList)
        }
    }

    companion object {
        private const val FEATURE_NAME = "test"
        private const val FEATURE_JSON = "{\"state\": \"enabled\"}"
        val unprotectedTemporaryList = listOf(UnprotectedTemporaryEntity("example.com", "reason"))
    }
}
