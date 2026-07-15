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

package com.duckduckgo.remote.messaging.internal.setting

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.remote.messaging.impl.RemoteMessagingConfigDownloader
import com.duckduckgo.remote.messaging.internal.feature.RMF_PROD_CONFIG_URL
import com.duckduckgo.remote.messaging.internal.feature.RmfSettingPlugin
import com.duckduckgo.remote.messaging.internal.feature.resolveRmfConfigUrl
import com.duckduckgo.remotemessaging.internal.databinding.ViewRmfConfigSourceBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject

// Internal dev-only screen: labels are diagnostic and intentionally not localized.
@SuppressLint("SetTextI18n")
@InjectWith(ViewScope::class)
class RmfConfigSourceSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var rmfConfigSourceStore: RmfConfigSourceStore

    @Inject
    lateinit var remoteMessagingConfigDownloader: RemoteMessagingConfigDownloader

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    private val binding: ViewRmfConfigSourceBinding by viewBinding()

    // In-memory source of truth for the UI; the store is its async-persisted mirror.
    private var mode = RmfConfigMode.PRODUCTION
    private var prNumber = ""
    private var customUrl = ""

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.productionRadio.setClickListener { onModeSelected(RmfConfigMode.PRODUCTION) }
        binding.stagingRadio.setClickListener { onModeSelected(RmfConfigMode.STAGING) }
        binding.prNumberRadio.setClickListener { onModeSelected(RmfConfigMode.PR_NUMBER) }
        binding.customUrlRadio.setClickListener { onModeSelected(RmfConfigMode.CUSTOM_URL) }

        binding.prNumberInput.doOnTextChanged { text, _, _, _ ->
            prNumber = text?.toString().orEmpty()
            persist()
            renderEffectiveUrl()
        }
        binding.customUrlInput.doOnTextChanged { text, _, _, _ ->
            customUrl = text?.toString().orEmpty()
            persist()
            renderEffectiveUrl()
        }

        binding.downloadNowButton.setOnClickListener { downloadNow() }

        appCoroutineScope.launch(dispatcherProvider.io()) {
            mode = rmfConfigSourceStore.mode
            prNumber = rmfConfigSourceStore.prNumber
            customUrl = rmfConfigSourceStore.customUrl
            withContext(dispatcherProvider.main()) { render() }
        }
    }

    private fun onModeSelected(selected: RmfConfigMode) {
        mode = selected
        persist()
        render()
    }

    private fun render() {
        binding.productionRadio.setChecked(mode == RmfConfigMode.PRODUCTION)
        binding.stagingRadio.setChecked(mode == RmfConfigMode.STAGING)
        binding.prNumberRadio.setChecked(mode == RmfConfigMode.PR_NUMBER)
        binding.customUrlRadio.setChecked(mode == RmfConfigMode.CUSTOM_URL)

        if (binding.prNumberInput.text != prNumber) binding.prNumberInput.text = prNumber
        if (binding.customUrlInput.text != customUrl) binding.customUrlInput.text = customUrl

        binding.prNumberInput.isEditable = mode == RmfConfigMode.PR_NUMBER
        binding.customUrlInput.isEditable = mode == RmfConfigMode.CUSTOM_URL

        renderEffectiveUrl()
    }

    private fun renderEffectiveUrl() {
        val effective = resolveRmfConfigUrl(mode, prNumber, customUrl, RMF_PROD_CONFIG_URL.toHttpUrl()) ?: RMF_PROD_CONFIG_URL
        binding.effectiveUrl.text = "Effective: $effective"
    }

    private fun persist() {
        val mode = this.mode
        val prNumber = this.prNumber
        val customUrl = this.customUrl
        appCoroutineScope.launch(dispatcherProvider.io()) {
            rmfConfigSourceStore.mode = mode
            rmfConfigSourceStore.prNumber = prNumber
            rmfConfigSourceStore.customUrl = customUrl
        }
    }

    private fun downloadNow() {
        binding.downloadNowButton.isEnabled = false
        val mode = this.mode
        val prNumber = this.prNumber
        val customUrl = this.customUrl
        appCoroutineScope.launch(dispatcherProvider.io()) {
            // Flush the current selection to the store first, so the resolver reads it during this download.
            rmfConfigSourceStore.mode = mode
            rmfConfigSourceStore.prNumber = prNumber
            rmfConfigSourceStore.customUrl = customUrl
            val success = remoteMessagingConfigDownloader.download()
            withContext(dispatcherProvider.main()) {
                binding.downloadNowButton.isEnabled = true
                val message = if (success) "RMF config downloaded" else "RMF config download failed"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@ContributesMultibinding(ActivityScope::class)
class RmfConfigSourceViewPlugin @Inject constructor() : RmfSettingPlugin {
    override fun getView(context: Context): View {
        return RmfConfigSourceSettingView(context)
    }
}
