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
import com.duckduckgo.remote.messaging.internal.feature.RmfSettingPlugin
import com.duckduckgo.remotemessaging.internal.databinding.RmfSimpleViewBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Dev-only action: forces an immediate RMF config re-download, which (with `alwaysProcessRemoteConfig`
 * on) re-processes and re-runs the matcher against the current settings so message selection updates
 * without waiting for the periodic worker.
 */
@InjectWith(ViewScope::class)
class RmfForceRefreshSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var downloader: RemoteMessagingConfigDownloader

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    private val binding: RmfSimpleViewBinding by viewBinding()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.root.setPrimaryText("Force RMF config re-download")
        binding.root.setSecondaryText("Re-downloads + re-processes now (re-runs the matcher against current settings)")
        binding.root.setClickListener {
            Toast.makeText(context, "RMF: re-downloading config…", Toast.LENGTH_SHORT).show()
            appCoroutineScope.launch(dispatcherProvider.io()) {
                downloader.download()
            }
        }
    }
}

@ContributesMultibinding(ActivityScope::class)
class RmfForceRefreshSettingViewPlugin @Inject constructor() : RmfSettingPlugin {
    override fun getView(context: Context): View {
        return RmfForceRefreshSettingView(context)
    }
}
