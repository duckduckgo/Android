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

package com.duckduckgo.app.onboarding

import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingFeatureToggles
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface DuckAiOnboardingAvailability {
    suspend fun isDuckAiOnboardingEnabled(): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDuckAiOnboardingAvailability @Inject constructor(
    private val toggles: ExtendedOnboardingFeatureToggles,
    private val duckChat: DuckChat,
    private val browserConfig: AndroidBrowserConfigFeature,
    private val appBuildConfig: AppBuildConfig,
    private val dispatcherProvider: DispatcherProvider,
) : DuckAiOnboardingAvailability {
    override suspend fun isDuckAiOnboardingEnabled(): Boolean =
        withContext(dispatcherProvider.io()) {
            duckChat.isEnabled() &&
                browserConfig.singleTabFireDialog().isEnabled() &&
                toggles.duckAiOnboarding().isEnabled() &&
                appBuildConfig.deviceLocale.language !in INCOMPLETE_TRANSLATION_LANGUAGES
        }

    companion object {
        // Languages still missing some Duck.ai onboarding translations. The onboarding is hidden
        // for these so users aren't shown a half-translated flow. Remove each as its translations
        // land. Unsupported locales fall back to fully-English (complete) copy.
        //
        // Includes codes the resource resolver can map onto an incomplete folder even though
        // deviceLocale.language differs: "no"/"nn" (Norwegian macrolanguage) -> values-nb,
        // "mo" (deprecated Moldovan) -> values-ro. NOTE: Locale.getLanguage() returns obsolete
        // codes (he->iw, id->in, yi->ji), so use those if ever adding Hebrew/Indonesian/Yiddish.
        private val INCOMPLETE_TRANSLATION_LANGUAGES = setOf("de", "el", "mo", "nb", "nn", "no", "pl", "ro", "ru", "tr")
    }
}
