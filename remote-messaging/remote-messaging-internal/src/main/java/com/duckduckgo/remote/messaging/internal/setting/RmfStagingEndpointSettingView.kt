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

package com.duckduckgo.remote.messaging.internal.setting

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import android.widget.CompoundButton
import android.widget.FrameLayout
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.remote.messaging.impl.RemoteMessagingConfigDownloader
import com.duckduckgo.remote.messaging.internal.feature.RmfSettingPlugin
import com.duckduckgo.remote.messaging.internal.store.DevRmfSettingsDataStore
import com.duckduckgo.remote.messaging.store.RemoteMessagingConfigRepository
import com.duckduckgo.remote.messaging.store.RemoteMessagingDatabase
import com.duckduckgo.remotemessaging.internal.databinding.RmfSimpleViewBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import javax.inject.Inject

private const val DEFAULT_PROD_URL = "https://staticcdn.duckduckgo.com/remotemessaging/config/v1/android-config.json"
private const val DEFAULT_STAGING_URL = "https://staticcdn.duckduckgo.com/remotemessaging/config/staging/android-config.json"

@InjectWith(ViewScope::class)
class RmfStagingEndpointSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var rmfInternalSettings: RmfInternalSettings

    @Inject
    lateinit var devRmfSettingsDataStore: DevRmfSettingsDataStore

    @Inject
    lateinit var configRepository: RemoteMessagingConfigRepository

    @Inject
    lateinit var downloader: RemoteMessagingConfigDownloader

    @Inject
    lateinit var database: RemoteMessagingDatabase

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    private val binding: RmfSimpleViewBinding by viewBinding()

    private val stagingToggleListener = CompoundButton.OnCheckedChangeListener { _, value ->
        appCoroutineScope.launch(dispatcherProvider.io()) {
            rmfInternalSettings.useStatingEndpoint().setRawStoredState(State(enable = value))
            renderCurrentState()
        }
    }

    private val customUrlToggleListener = CompoundButton.OnCheckedChangeListener { _, value ->
        devRmfSettingsDataStore.useCustomRmfUrl = value
        binding.urlInput.isEditable = value
        if (!value) binding.validation.hide()
        runValidation()
        appCoroutineScope.launch(dispatcherProvider.io()) { renderCurrentState() }
    }

    private val urlInputWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            devRmfSettingsDataStore.customRmfUrl = s?.toString()
            runValidation()
        }
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.stagingEndpointToggle.showSwitch()
        binding.stagingEndpointToggle.setPrimaryText("Use RMF staging endpoint")
        binding.stagingEndpointToggle.setSecondaryText("Enable to use RMF staging endpoint")

        binding.urlInput.addTextChangedListener(urlInputWatcher)
        binding.load.setOnClickListener { forceDownload() }
        binding.reset.setOnClickListener { resetAndRedownload() }

        appCoroutineScope.launch(dispatcherProvider.io()) {
            val stagingEnabled = rmfInternalSettings.useStatingEndpoint().isEnabled()
            val useCustomUrl = devRmfSettingsDataStore.useCustomRmfUrl
            val customUrl = devRmfSettingsDataStore.customRmfUrl.orEmpty()
            withContext(dispatcherProvider.main()) {
                binding.stagingEndpointToggle.quietlySetIsChecked(stagingEnabled, stagingToggleListener)
                binding.customUrlToggle.quietlySetIsChecked(useCustomUrl, customUrlToggleListener)
                binding.urlInput.text = customUrl
                binding.urlInput.isEditable = useCustomUrl
                runValidation()
            }
            renderCurrentState()
        }
    }

    private fun forceDownload() {
        runValidation()
        binding.load.isEnabled = false
        appCoroutineScope.launch(dispatcherProvider.io()) {
            configRepository.invalidate()
            downloader.download()
            renderCurrentState()
            withContext(dispatcherProvider.main()) { binding.load.isEnabled = true }
        }
    }

    private fun resetAndRedownload() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            devRmfSettingsDataStore.useCustomRmfUrl = false
            devRmfSettingsDataStore.customRmfUrl = null
            database.remoteMessagesDao().deleteAll()
            database.remoteMessagingCohortDao().deleteAll()
            configRepository.invalidate()
            downloader.download()
            withContext(dispatcherProvider.main()) {
                binding.customUrlToggle.quietlySetIsChecked(false, customUrlToggleListener)
                binding.urlInput.text = ""
                binding.urlInput.isEditable = false
                binding.validation.hide()
            }
            renderCurrentState()
        }
    }

    private suspend fun renderCurrentState() {
        val config = configRepository.get()
        val effectiveUrl = currentEffectiveUrl()
        withContext(dispatcherProvider.main()) {
            binding.currentVersion.setSecondaryText(config.version.toString())
            binding.timestamp.setSecondaryText(config.evaluationTimestamp)
            binding.latestUrl.setSecondaryText(effectiveUrl)
        }
    }

    private fun runValidation() {
        val canUse = canUseCustomUrl()
        if (canUse || !devRmfSettingsDataStore.useCustomRmfUrl) {
            binding.validation.gone()
        } else {
            binding.validation.text = "URL is not valid. Default URL will be used"
            binding.validation.show()
        }
        binding.load.isEnabled = true
    }

    private fun canUseCustomUrl(): Boolean {
        val storedUrl = devRmfSettingsDataStore.customRmfUrl
        if (!devRmfSettingsDataStore.useCustomRmfUrl) return false
        if (storedUrl.isNullOrEmpty()) return false
        val uri = runCatching { URI(storedUrl) }.getOrNull() ?: return false
        val scheme = uri.scheme ?: return false
        if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) return false
        return !uri.host.isNullOrEmpty()
    }

    private fun currentEffectiveUrl(): String {
        return when {
            canUseCustomUrl() -> devRmfSettingsDataStore.customRmfUrl.orEmpty()
            rmfInternalSettings.useStatingEndpoint().isEnabled() -> DEFAULT_STAGING_URL
            else -> DEFAULT_PROD_URL
        }
    }
}

@ContributesMultibinding(ActivityScope::class)
class RecoverSubscriptionViewPlugin @Inject constructor() : RmfSettingPlugin {
    override fun getView(context: Context): View {
        return RmfStagingEndpointSettingView(context)
    }
}
