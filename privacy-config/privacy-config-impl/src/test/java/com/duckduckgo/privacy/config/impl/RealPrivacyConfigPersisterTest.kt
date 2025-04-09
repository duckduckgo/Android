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

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.feature.toggles.api.FeatureException
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.duckduckgo.privacy.config.impl.models.JsonPrivacyConfig
import com.duckduckgo.privacy.config.store.PrivacyConfig
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.PrivacyConfigRepository
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import com.duckduckgo.privacy.config.store.RealPrivacyConfigRepository
import com.duckduckgo.privacy.config.store.features.unprotectedtemporary.RealUnprotectedTemporaryRepository
import com.duckduckgo.privacy.config.store.features.unprotectedtemporary.UnprotectedTemporaryRepository
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
class RealPrivacyConfigPersisterTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    lateinit var testee: RealPrivacyConfigPersister
    private val mockTogglesRepository: PrivacyFeatureTogglesRepository = mock()

    private lateinit var db: PrivacyConfigDatabase
    private lateinit var privacyRepository: PrivacyConfigRepository
    private lateinit var unprotectedTemporaryRepository: UnprotectedTemporaryRepository
    private val pluginPoint = FakePrivacyFeaturePluginPoint(listOf(FakePrivacyFeaturePlugin()))
    private val variantManagerPlugin = FakePrivacyVariantManagerPlugin()
    private lateinit var sharedPreferences: SharedPreferences

    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun before() {
        prepareDb()
        sharedPreferences = InMemorySharedPreferences().apply {
            edit {
                putInt("plugin_signature", pluginPoint.signature())
            }
        }

        testee =
            RealPrivacyConfigPersister(
                pluginPoint,
                variantManagerPlugin,
                mockTogglesRepository,
                unprotectedTemporaryRepository,
                privacyRepository,
                db,
                sharedPreferences,
            )
    }

    @After
    fun after() {
        db.close()
    }

    private fun prepareDb() {
        db =
            Room.inMemoryDatabaseBuilder(context, PrivacyConfigDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        privacyRepository = RealPrivacyConfigRepository(db)
        unprotectedTemporaryRepository =
            RealUnprotectedTemporaryRepository(
                db,
                TestScope(),
                coroutineRule.testDispatcherProvider,
                isMainProcess = true,
            )
    }

    @Test
    fun whenHashIsNullSignatureReturnsFeatureName() {
        val expected = pluginPoint.getPlugins().sumOf { it.featureName.hashCode() }
        assertEquals(expected, pluginPoint.signature())
    }

    @Test
    fun whenHashIsNotNullSignatureReturnsHash() {
        val pluginPoint = FakePrivacyFeaturePluginPoint(listOf(HashedFakePrivacyFeaturePlugin()))
        val expected = pluginPoint.getPlugins().sumOf { it.hash().hashCode() }
        assertEquals(expected, pluginPoint.signature())
    }

    @Test
    fun whenDifferentPluginPointsThenReturnDifferentSignatures() {
        val differentPluginPoint = FakePrivacyFeaturePluginPoint(listOf(FakePrivacyFeaturePlugin(), FakePrivacyFeaturePlugin()))
        assertNotEquals(pluginPoint.signature(), differentPluginPoint.signature())
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
    fun whenPersistPrivacyConfigAndMultiplePluginMatchesFeatureNameThenCallThemAll() =
        runTest {
            val differentPluginPoint = FakePrivacyFeaturePluginPoint(listOf(FakePrivacyFeaturePlugin(), FakePrivacyFeaturePlugin()))
            // override
            val testee =
                RealPrivacyConfigPersister(
                    differentPluginPoint,
                    variantManagerPlugin,
                    mockTogglesRepository,
                    unprotectedTemporaryRepository,
                    privacyRepository,
                    db,
                    sharedPreferences,
                )
            testee.persistPrivacyConfig(getJsonPrivacyConfig())

            for (plugin in differentPluginPoint.getPlugins()) {
                assertEquals(1, (plugin as FakePrivacyFeaturePlugin).count)
            }
        }

    @Test
    fun whenPersistPrivacyConfigAndVersionIsLowerThanPreviousOneStoredThenDoNothing() =
        runTest {
            privacyRepository.insert(PrivacyConfig(version = 3, readme = "readme", eTag = "eTag", timestamp = "2023-01-02"))

            testee.persistPrivacyConfig(getJsonPrivacyConfig())

            assertEquals(3, privacyRepository.get()!!.version)
            verify(mockTogglesRepository, never()).deleteAll()
        }

    @Test
    fun whenPersistPrivacyConfigAndVersionIsLowerThanPreviousOneAndDifferentPluginsThenStoreNewConfig() =
        runTest {
            sharedPreferences.edit().putInt("plugin_signature", 0)
            privacyRepository.insert(PrivacyConfig(version = 3, readme = "readme", eTag = "eTag", timestamp = "2023-01-02"))

            testee.persistPrivacyConfig(getJsonPrivacyConfig())

            assertEquals(3, privacyRepository.get()!!.version)
            verify(mockTogglesRepository, never()).deleteAll()
        }

    @Test
    fun whenPersistPrivacyConfigAndVersionIsEqualsThanPreviousOneStoredThenDoNothing() =
        runTest {
            privacyRepository.insert(PrivacyConfig(version = 2, readme = "readme", eTag = "eTag", timestamp = "2023-01-02"))

            testee.persistPrivacyConfig(getJsonPrivacyConfig())

            assertEquals(2, privacyRepository.get()!!.version)
            verify(mockTogglesRepository, never()).deleteAll()
        }

    @Test
    fun whenPersistPrivacyConfigAndVersionIsEqualsThanPreviousOneStoredAndDifferentPluginsThenUpdateConfig() =
        runTest {
            sharedPreferences.edit().putInt("plugin_signature", 0)
            privacyRepository.insert(PrivacyConfig(version = 2, readme = "readme", eTag = "eTag", timestamp = "2023-01-02"))

            testee.persistPrivacyConfig(getJsonPrivacyConfig())

            assertEquals(2, privacyRepository.get()!!.version)
            verify(mockTogglesRepository).deleteAll()
        }

    @Test
    fun whenPersistPrivacyConfigAndVersionIsHigherThanPreviousOneStoredThenStoreNewConfig() =
        runTest {
            privacyRepository.insert(PrivacyConfig(version = 1, readme = "readme", eTag = "eTag", timestamp = "2023-01-02"))

            testee.persistPrivacyConfig(getJsonPrivacyConfig())

            assertEquals(2, privacyRepository.get()!!.version)
        }

    @Test
    fun whenPersistPrivacyConfigAndVersionIsHigherThanPreviousOneStoredAndDifferentPluginsThenStoreNewConfig() =
        runTest {
            sharedPreferences.edit().putInt("plugin_signature", 0)
            privacyRepository.insert(PrivacyConfig(version = 1, readme = "readme", eTag = "eTag", timestamp = "2023-01-02"))

            testee.persistPrivacyConfig(getJsonPrivacyConfig())

            assertEquals(2, privacyRepository.get()!!.version)
        }

    private fun getJsonPrivacyConfig(): JsonPrivacyConfig {
        return JsonPrivacyConfig(
            version = 2,
            readme = "readme",
            features = mapOf(FEATURE_NAME to JSONObject(FEATURE_JSON)),
            unprotectedTemporary = unprotectedTemporaryList,
            experimentalVariants = VARIANT_MANAGER_JSON,
        )
    }

    class FakePrivacyFeaturePluginPoint(private val plugins: List<PrivacyFeaturePlugin>) :
        PluginPoint<PrivacyFeaturePlugin> {
        override fun getPlugins(): Collection<PrivacyFeaturePlugin> {
            return plugins
        }
    }

    private class FakePrivacyFeaturePlugin : PrivacyFeaturePlugin {
        var count = 0

        override fun store(
            featureName: String,
            jsonString: String,
        ): Boolean {
            count++
            return true
        }

        override val featureName: String =
            PrivacyFeatureName.GpcFeatureName.value
    }

    private class HashedFakePrivacyFeaturePlugin : PrivacyFeaturePlugin {
        var count = 0

        override fun store(
            featureName: String,
            jsonString: String,
        ): Boolean {
            count++
            return true
        }

        override val featureName: String = "HashedFakePrivacyFeaturePlugin"

        override fun hash() = "HashedFakePrivacyFeaturePluginHash"
    }

    class FakePrivacyVariantManagerPlugin : PrivacyFeaturePlugin {

        override fun store(
            featureName: String,
            jsonString: String,
        ): Boolean {
            return true
        }

        override val featureName = "experimentalVariants"
    }

    class FakeFakePrivacyConfigCallbackPluginPoint(
        private val plugins: List<PrivacyConfigCallbackPlugin>,
    ) : PluginPoint<PrivacyConfigCallbackPlugin> {
        override fun getPlugins(): Collection<PrivacyConfigCallbackPlugin> {
            return plugins
        }
    }

    internal class FakePrivacyConfigCallbackPlugin : PrivacyConfigCallbackPlugin {
        internal var downloadCallCount = 0

        override fun onPrivacyConfigDownloaded() {
            downloadCallCount++
        }
    }

    companion object {
        private const val FEATURE_NAME = "gpc"
        private const val FEATURE_JSON = "{\"state\": \"enabled\"}"
        val unprotectedTemporaryList = listOf(FeatureException("example.com", "reason"))
        private val VARIANT_MANAGER_JSON = null
    }
}
