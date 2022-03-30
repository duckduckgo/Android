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

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.feature.toggles.api.FeatureName
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.impl.models.JsonPrivacyConfig
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.duckduckgo.privacy.config.store.PrivacyConfig
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.PrivacyConfigRepository
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import com.duckduckgo.privacy.config.store.RealPrivacyConfigRepository
import com.duckduckgo.privacy.config.store.UnprotectedTemporaryEntity
import com.duckduckgo.privacy.config.store.features.unprotectedtemporary.RealUnprotectedTemporaryRepository
import com.duckduckgo.privacy.config.store.features.unprotectedtemporary.UnprotectedTemporaryRepository
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class RealPrivacyConfigPersisterTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    lateinit var testee: RealPrivacyConfigPersister
    private val mockTogglesRepository: PrivacyFeatureTogglesRepository = mock()

    private lateinit var db: PrivacyConfigDatabase
    private lateinit var privacyRepository: PrivacyConfigRepository
    private lateinit var unprotectedTemporaryRepository: UnprotectedTemporaryRepository
    private val pluginPoint = FakePrivacyFeaturePluginPoint()

    @Before
    fun before() {
        prepareDb()

        testee =
            RealPrivacyConfigPersister(
                pluginPoint,
                mockTogglesRepository,
                unprotectedTemporaryRepository,
                privacyRepository,
                db
            )
    }

    @After
    fun after() {
        db.close()
    }

    private fun prepareDb() {
        db =
            Room.inMemoryDatabaseBuilder(mock(), PrivacyConfigDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        privacyRepository = RealPrivacyConfigRepository(db)
        unprotectedTemporaryRepository =
            RealUnprotectedTemporaryRepository(
                db, TestScope(), coroutineRule.testDispatcherProvider
            )
    }

    @Test
    fun whenPersistPrivacyConfigThenDeleteAllTogglesPreviouslyStored() =
        runTest {
            testee.persistPrivacyConfig(getJsonPrivacyConfig())

            verify(mockTogglesRepository).deleteAll()
        }

    @Test
    fun whenPersistPrivacyConfigThenUpdateAllUnprotectedTemporaryExceptions() =
        runTest {
            assertEquals(0, unprotectedTemporaryRepository.exceptions.size)

            testee.persistPrivacyConfig(getJsonPrivacyConfig())

            assertEquals(1, unprotectedTemporaryRepository.exceptions.size)
        }

    @Test
    fun whenPersistPrivacyConfigAndPluginMatchesFeatureNameThenStoreCalled() =
        runTest {
            testee.persistPrivacyConfig(getJsonPrivacyConfig())

            val plugin = pluginPoint.getPlugins().first() as FakePrivacyFeaturePlugin
            assertEquals(1, plugin.count)
        }

    @Test
    fun whenPersistPrivacyConfigAndVersionIsLowerThanPreviousOneStoredThenDoNothing() =
        runTest {
            privacyRepository.insert(PrivacyConfig(version = 3, readme = "readme"))

            testee.persistPrivacyConfig(getJsonPrivacyConfig())

            assertEquals(3, privacyRepository.get()!!.version)
            verify(mockTogglesRepository, never()).deleteAll()
        }

    @Test
    fun whenPersistPrivacyConfigAndVersionIsEqualsThanPreviousOneStoredThenDoNothing() =
        runTest {
            privacyRepository.insert(PrivacyConfig(version = 2, readme = "readme"))

            testee.persistPrivacyConfig(getJsonPrivacyConfig())

            assertEquals(2, privacyRepository.get()!!.version)
            verify(mockTogglesRepository, never()).deleteAll()
        }

    @Test
    fun whenPersistPrivacyConfigAndVersionIsHigherThanPreviousOneStoredThenStoreNewConfig() =
        runTest {
            privacyRepository.insert(PrivacyConfig(version = 1, readme = "readme"))

            testee.persistPrivacyConfig(getJsonPrivacyConfig())

            assertEquals(2, privacyRepository.get()!!.version)
        }

    private fun getJsonPrivacyConfig(): JsonPrivacyConfig {
        return JsonPrivacyConfig(
            version = 2,
            readme = "readme",
            features = mapOf(FEATURE_NAME to JSONObject(FEATURE_JSON)),
            unprotectedTemporaryList
        )
    }

    class FakePrivacyFeaturePluginPoint : PluginPoint<PrivacyFeaturePlugin> {
        val plugin = FakePrivacyFeaturePlugin()
        override fun getPlugins(): Collection<PrivacyFeaturePlugin> {
            return listOf(plugin)
        }
    }

    class FakePrivacyFeaturePlugin : PrivacyFeaturePlugin {
        var count = 0

        override fun store(
            name: FeatureName,
            jsonString: String
        ): Boolean {
            count++
            return true
        }

        override val featureName: PrivacyFeatureName =
            PrivacyFeatureName.GpcFeatureName
    }

    companion object {
        private const val FEATURE_NAME = "gpc"
        private const val FEATURE_JSON = "{\"state\": \"enabled\"}"
        val unprotectedTemporaryList = listOf(UnprotectedTemporaryEntity("example.com", "reason"))
    }
}
