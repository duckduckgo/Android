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

package com.duckduckgo.app.branddesignupdate

import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBrandDesignUpdateThemeProviderTest {

    private fun createProvider(flavor: String): AppBrandDesignUpdateThemeProvider {
        val toggles = FeatureToggles.Builder()
            .store(FakeToggleStore())
            .appVersionProvider { Int.MAX_VALUE }
            .flavorNameProvider { flavor }
            .featureName("appBrandDesignUpdate")
            .build()
            .create(AppBrandDesignUpdateToggles::class.java)
        return AppBrandDesignUpdateThemeProvider(toggles)
    }

    @Test
    fun whenFeatureToggleEnabledThenProviderReturnsTrue() {
        assertTrue(createProvider("internal").isAppBrandDesignUpdateEnabled())
    }

    @Test
    fun whenFeatureToggleDisabledThenProviderReturnsFalse() {
        assertFalse(createProvider("play").isAppBrandDesignUpdateEnabled())
    }
}
