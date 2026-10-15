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

package com.duckduckgo.app.internalfeedback

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.text.format.Formatter
import com.duckduckgo.app.startup.metrics.ProcessTimeProvider
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.autofill.api.InternalTestUserChecker
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.common.utils.device.DeviceInfo.FormFactor
import com.duckduckgo.common.utils.extensions.toSanitizedLanguageTag
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.experiments.api.VariantManager
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.privacy.config.api.PrivacyConfig
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

interface InternalFeedbackDeviceInfoProvider {
    /**
     * @return device info in the internal feedback form's cross-platform `DeviceInfo` schema, with unavailable optional fields omitted.
     */
    suspend fun getDeviceInfo(): Result<JSONObject>
}

@ContributesBinding(AppScope::class)
class RealInternalFeedbackDeviceInfoProvider @Inject constructor(
    private val context: Context,
    private val appBuildConfig: AppBuildConfig,
    private val deviceInfo: DeviceInfo,
    private val internalTestUserChecker: InternalTestUserChecker,
    private val webViewVersionProvider: WebViewVersionProvider,
    private val statisticsDataStore: StatisticsDataStore,
    private val variantManager: VariantManager,
    private val networkProtectionState: NetworkProtectionState,
    private val privacyConfig: PrivacyConfig,
    private val featureTogglesInventory: FeatureTogglesInventory,
    private val tabRepository: TabRepository,
    private val processTimeProvider: ProcessTimeProvider,
    private val dispatcherProvider: DispatcherProvider,
) : InternalFeedbackDeviceInfoProvider {

    override suspend fun getDeviceInfo(): Result<JSONObject> = withContext(dispatcherProvider.io()) {
        runCatching {
            JSONObject()
                .put("platform", "android")
                .put("appVersion", appBuildConfig.versionName)
                .put("osName", "Android")
                .put("osVersion", Build.VERSION.RELEASE)
                .put("deviceModel", appBuildConfig.model)
                .put("deviceManufacturer", appBuildConfig.manufacturer)
                .put("appBuild", appBuildConfig.versionCode.toString())
                .put("formFactor", getFormFactor())
                .putOpt("architecture", Build.SUPPORTED_ABIS.firstOrNull())
                .put("locale", appBuildConfig.deviceLocale.toSanitizedLanguageTag())
                .put("channel", getChannel())
                .put("isInternalUser", internalTestUserChecker.isInternalTestUser)
                .put("webViewVersion", webViewVersionProvider.getFullVersion())
                .putOpt("atb", statisticsDataStore.atb?.formatWithVariant(variantManager.getVariantKey()))
                .put("vpnOn", networkProtectionState.isRunning())
                .putOpt("remoteConfigVersion", privacyConfig.privacyConfigData()?.version)
                .putOpt("activeExperiments", getActiveExperiments())
                .put("diagnostics", getDiagnostics())
        }
    }

    private fun getFormFactor(): String = when (deviceInfo.formFactor()) {
        FormFactor.PHONE -> "mobile"
        FormFactor.TABLET -> "tablet"
    }

    private fun getChannel(): String {
        val flavor = when (appBuildConfig.flavor) {
            BuildFlavor.INTERNAL -> "Internal"
            BuildFlavor.PLAY -> "Play"
            BuildFlavor.FDROID -> "F-Droid"
        }
        return "$flavor (${appBuildConfig.buildType})"
    }

    private suspend fun getActiveExperiments(): JSONArray? {
        val experiments = featureTogglesInventory.getAllActiveExperimentToggles().mapNotNull { toggle ->
            toggle.getCohort()?.let { cohort ->
                JSONObject()
                    .put("name", toggle.featureName().name)
                    .put("cohort", cohort.name)
            }
        }
        return experiments.takeIf { it.isNotEmpty() }?.let { JSONArray(it) }
    }

    private fun getDiagnostics(): JSONObject = JSONObject()
        .put("Tabs", tabRepository.getOpenTabCount().toString())
        .put("Memory", getMemory())
        .put("Disk", getDisk())
        .put("Session", getSessionLength())

    private fun getMemory(): String {
        val memoryInfo = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(memoryInfo)
        val available = Formatter.formatShortFileSize(context, memoryInfo.availMem)
        val total = Formatter.formatShortFileSize(context, memoryInfo.totalMem)
        val lowMemory = if (memoryInfo.lowMemory) "yes" else "no"
        return "$available available, $total total, low memory: $lowMemory"
    }

    private fun getDisk(): String {
        val availableBytes = StatFs(context.filesDir.path).availableBytes
        return "${Formatter.formatShortFileSize(context, availableBytes)} free"
    }

    private fun getSessionLength(): String {
        val sessionMillis = processTimeProvider.currentUptimeMs() - processTimeProvider.startupTimeMs()
        return sessionMillis.milliseconds.inWholeMinutes.minutes.toString()
    }
}
