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

package com.duckduckgo.site.permissions.impl

import android.annotation.SuppressLint
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.site.permissions.api.ClientToolPermission.ALWAYS_ALLOW
import com.duckduckgo.site.permissions.api.ClientToolPermission.NEVER_ALLOW
import com.duckduckgo.site.permissions.impl.drm.DrmSessionStore
import com.duckduckgo.site.permissions.impl.feature.DrmPolicyFeature
import com.duckduckgo.site.permissions.impl.feature.SitePermissionsDialogRedesignFeature
import com.duckduckgo.site.permissions.impl.feature.SitePermissionsSystemRecoveryFeature
import com.duckduckgo.site.permissions.store.SitePermissionsDatabase
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionAskSettingType
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionsEntity
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class RealClientToolPermissionStoreTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        SitePermissionsDatabase::class.java,
    ).allowMainThreadQueries().build()

    private val store = RealClientToolPermissionStore(db.clientToolPermissionsDao(), coroutineRule.testDispatcherProvider)

    private val sitePermissionsDialogRedesignFeature = FakeFeatureToggleFactory.create(SitePermissionsDialogRedesignFeature::class.java)

    private val repository = SitePermissionsRepositoryImpl(
        db.sitePermissionsDao(),
        db.sitePermissionsAllowedDao(),
        mock(),
        coroutineRule.testScope,
        coroutineRule.testDispatcherProvider,
        mock(),
        DrmSessionStore(),
        FakeFeatureToggleFactory.create(DrmPolicyFeature::class.java),
        lazy { mock() },
        sitePermissionsDialogRedesignFeature,
    )

    private val manager = SitePermissionsManagerImpl(
        mock(),
        mock(),
        repository,
        coroutineRule.testDispatcherProvider,
        mock(),
        FakeFeatureToggleFactory.create(SitePermissionsSystemRecoveryFeature::class.java),
        FakeFeatureToggleFactory.create(DrmPolicyFeature::class.java),
        mock(),
        DrmSessionStore(),
        mock(),
        sitePermissionsDialogRedesignFeature,
    )

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenNothingStoredThenGetReturnsNull() = runTest {
        assertNull(store.get(DUCK_AI, TOOL))
    }

    @Test
    fun whenSetThenGetReturnsItForThatOriginAndToolOnly() = runTest {
        store.set(DUCK_AI, TOOL, ALWAYS_ALLOW)

        assertEquals(ALWAYS_ALLOW, store.get(DUCK_AI, TOOL))
        assertNull(store.get(DUCK_AI, OTHER_TOOL))
        assertNull(store.get("https://dev.duck.ai", TOOL))
    }

    @Test
    fun whenSetAgainThenLatestChoiceWins() = runTest {
        store.set(DUCK_AI, TOOL, ALWAYS_ALLOW)
        store.set(DUCK_AI, TOOL, NEVER_ALLOW)

        assertEquals(NEVER_ALLOW, store.get(DUCK_AI, TOOL))
    }

    @Test
    fun whenClearToolThenOnlyThatToolIsForgottenOnEveryOrigin() = runTest {
        store.set(DUCK_AI, TOOL, ALWAYS_ALLOW)
        store.set("https://dev.duck.ai", TOOL, NEVER_ALLOW)
        store.set(DUCK_AI, OTHER_TOOL, NEVER_ALLOW)

        store.clear(TOOL)

        assertNull(store.get(DUCK_AI, TOOL))
        assertNull(store.get("https://dev.duck.ai", TOOL))
        assertEquals(NEVER_ALLOW, store.get(DUCK_AI, OTHER_TOOL))
    }

    @Test
    fun whenClearAllThenEveryChoiceIsForgotten() = runTest {
        store.set(DUCK_AI, TOOL, ALWAYS_ALLOW)
        store.set(DUCK_AI, OTHER_TOOL, NEVER_ALLOW)

        store.clearAll()

        assertNull(store.get(DUCK_AI, TOOL))
        assertNull(store.get(DUCK_AI, OTHER_TOOL))
    }

    @Test
    fun whenFireButtonClearsAllButFireproofThenClientToolChoicesSurvive() = runTest {
        saveSitePermission("example.com")
        store.set(DUCK_AI, TOOL, ALWAYS_ALLOW)

        manager.clearAllButFireproof(emptyList())

        assertTrue(repository.domainsWithPermissions().isEmpty())
        assertEquals(ALWAYS_ALLOW, store.get(DUCK_AI, TOOL))
    }

    @Test
    fun whenFireButtonClearsDomainsThenClientToolChoicesSurvive() = runTest {
        sitePermissionsDialogRedesignFeature.singleTabBurnClearing().setRawStoredState(Toggle.State(true))
        saveSitePermission("example.com")
        store.set(DUCK_AI, TOOL, NEVER_ALLOW)

        manager.clearForDomainsButFireproof(setOf("example.com", "duck.ai"), emptyList())

        assertTrue(repository.domainsWithPermissions().isEmpty())
        assertEquals(NEVER_ALLOW, store.get(DUCK_AI, TOOL))
    }

    @Test
    fun whenSitePermissionsAreAllRemovedThenClientToolChoicesSurvive() = runTest {
        saveSitePermission("example.com")
        store.set(DUCK_AI, TOOL, ALWAYS_ALLOW)

        repository.deleteAll()

        assertTrue(repository.domainsWithPermissions().isEmpty())
        assertEquals(ALWAYS_ALLOW, store.get(DUCK_AI, TOOL))
    }

    private suspend fun saveSitePermission(domain: String) {
        repository.savePermission(SitePermissionsEntity(domain, askCameraSetting = SitePermissionAskSettingType.ALLOW_ALWAYS.name))
    }

    private companion object {
        const val DUCK_AI = "https://duck.ai"
        const val TOOL = "listOpenTabs"
        const val OTHER_TOOL = "otherTool"
    }
}
