/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.feature.toggles.impl

import com.duckduckgo.anvil.annotations.ContributesPluginPoint
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle.State.Target
import com.duckduckgo.feature.toggles.api.Toggle.TargetMatcherPlugin
import com.duckduckgo.feature.toggles.impl.FeatureTogglesPixelName.EXPERIMENT_ENROLLMENT
import com.duckduckgo.feature.toggles.internal.api.FeatureTogglesCallback
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import okio.ByteString.Companion.encode
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealFeatureTogglesCallback @Inject constructor(
    private val pixel: Pixel,
    private val targetMatchers: PluginPoint<TargetMatcherPlugin>,
) : FeatureTogglesCallback {

    override fun onCohortAssigned(
        experimentName: String,
        cohortName: String,
        enrollmentDate: String,
    ) {
        val parsedDate = ZonedDateTime.parse(enrollmentDate).truncatedTo(ChronoUnit.DAYS).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val params = mapOf("enrollmentDate" to parsedDate)
        val pixelName = getPixelName(experimentName, cohortName)
        val tag = "${pixelName}_$params".encode().md5().hex()
        pixel.fire(pixelName = pixelName, parameters = params, type = Pixel.PixelType.Unique(tag = tag))
    }

    override fun matchesToggleTargets(targets: List<Any>): Boolean {
        // no targets mean any target
        if (targets.isEmpty()) return true

        targets.forEach { target ->
            if (target is Target) {
                val targetMatched = targetMatchers.getPlugins().all { it.matchesTargetProperty(target) }
                // one target matched, return true already
                if (targetMatched) {
                    return true
                }
            }
        }

        return false
    }

    private fun getPixelName(
        experimentName: String,
        cohortName: String,
    ): String {
        return "${EXPERIMENT_ENROLLMENT.pixelName}_${experimentName}_$cohortName"
    }
}

internal enum class FeatureTogglesPixelName(override val pixelName: String) : Pixel.PixelName {
    EXPERIMENT_ENROLLMENT("experiment_enroll"),
}

@ContributesPluginPoint(
    scope = AppScope::class,
    boundType = TargetMatcherPlugin::class,
)
@Suppress("unused")
private interface TargetMatcherPluginTrigger

@ContributesMultibinding(AppScope::class)
class LocaleToggleTargetMatcher @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) : TargetMatcherPlugin {
    override fun matchesTargetProperty(target: Target): Boolean {
        val country = appBuildConfig.deviceLocale.country.lowercase()
        val language = appBuildConfig.deviceLocale.language.lowercase()

        val isCountryMatching = target.localeCountry?.let {
            country == it.lowercase()
        } ?: true
        val isLanguageMatching = target.localeLanguage?.let {
            language == it.lowercase()
        } ?: true

        return isCountryMatching && isLanguageMatching
    }
}

@ContributesMultibinding(AppScope::class)
class MinSdkToggleTargetMatcher @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) : TargetMatcherPlugin {
    override fun matchesTargetProperty(target: Target): Boolean {
        val sdkInt = appBuildConfig.sdkInt

        val isSdkIntMatching = target.minSdkVersion?.let {
            sdkInt >= it
        } ?: true

        return isSdkIntMatching
    }
}
