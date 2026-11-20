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

package com.duckduckgo.agesignals.impl

import android.content.Context
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.di.scopes.AppScope
import com.google.android.play.agesignals.AgeSignalsManager
import com.google.android.play.agesignals.AgeSignalsManagerFactory
import dev.zacsweers.metro.ContributesBinding
import javax.inject.Inject

interface AgeSignalsManagerProvider {
    suspend fun get(): AgeSignalsManager
    suspend fun isFakeScenarioActive(): Boolean
}

@ContributesBinding(AppScope::class)
class RealAgeSignalsManagerProvider internal constructor(
    private val context: Context,
    private val appBuildConfig: AppBuildConfig,
    private val scenarioStore: AgeSignalsScenarioStore,
    private val createPlayManager: (Context) -> AgeSignalsManager,
) : AgeSignalsManagerProvider {

    @Inject
    constructor(
        context: Context,
        appBuildConfig: AppBuildConfig,
        scenarioStore: AgeSignalsScenarioStore,
    ) : this(context, appBuildConfig, scenarioStore, AgeSignalsManagerFactory::create)

    override suspend fun get(): AgeSignalsManager = fakeResponses()?.toFakeManager() ?: createPlayManager(context)

    override suspend fun isFakeScenarioActive(): Boolean = fakeResponses() != null

    private suspend fun fakeResponses(): FakeResponses? =
        if (appBuildConfig.flavor == BuildFlavor.INTERNAL) scenarioStore.scenario().fake else null
}
