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

package com.duckduckgo.app.audit

import android.content.Context
import android.widget.Toast
import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.app.browser.R
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.internal.features.api.InternalFeaturePlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import timber.log.Timber
import timber.log.Timber.Forest
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class AuditSettingsFeature @Inject constructor(
    private val context: Context,
    private val testFeature: AitorTestFeature,
) : InternalFeaturePlugin {
    override fun internalFeatureTitle(): String {
        return "Test Incremental FF"
    }

    override fun internalFeatureSubtitle(): String {
        return "Tap to check FF value"
    }

    override fun onInternalFeatureClicked(activityContext: Context) {
        val rolloutState = testFeature.rollout().getRawStoredState()
        Timber.e(
            """
                "aitor" is ${testFeature.self().isEnabled()}
                "rollout" is ${testFeature.rollout().isEnabled()}
                "rollout" raw state is $rolloutState
            """.trimIndent(),
        )
    }
}

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "aitor",
)
interface AitorTestFeature {

    @Toggle.DefaultValue(false)
    fun self(): Toggle

    @Toggle.DefaultValue(false)
    fun rollout(): Toggle
}
