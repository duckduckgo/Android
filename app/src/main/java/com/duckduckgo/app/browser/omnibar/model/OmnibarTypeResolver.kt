/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.omnibar.model

import com.duckduckgo.app.browser.omnibar.model.OmnibarType.FADE
import com.duckduckgo.app.browser.omnibar.model.OmnibarType.SCROLLING
import com.duckduckgo.app.browser.omnibar.model.OmnibarType.SINGLE
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.common.ui.experiments.visual.store.VisualDesignExperimentDataStore
import javax.inject.Inject

class OmnibarTypeResolver @Inject constructor(
    private val experimentsDataStore: VisualDesignExperimentDataStore,
    private val browserFeatures: AndroidBrowserConfigFeature
) {
    fun getOmnibarType(): OmnibarType {
        return if (experimentsDataStore.isExperimentEnabled.value) {
            FADE
        } else if (browserFeatures.newUIWithoutNavBar().isEnabled()) {
            SINGLE
        } else {
            SCROLLING
        }
    }
}
