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

package com.duckduckgo.subscriptions.internal.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.subscriptions.impl.repository.AuthRepository
import com.duckduckgo.subscriptions.internal.SubsSettingPlugin
import com.duckduckgo.subscriptions.internal.databinding.SubsSimpleViewBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@InjectWith(ViewScope::class)
class CopySubscriptionDataView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    @Inject
    lateinit var authRepository: AuthRepository

    private val binding: SubsSimpleViewBinding by viewBinding()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.root.setPrimaryText("Subscriptions data")

        findViewTreeLifecycleOwner()?.lifecycle?.coroutineScope?.launch(dispatcherProvider.main()) {
            while (true) {
                binding.root.setSecondaryText(getData())
                delay(1.seconds)
            }
        }

        binding.root.setClickListener {
            copyDataToClipboard()
        }
    }

    private fun copyDataToClipboard() {
        val clipboardManager = context.getSystemService(ClipboardManager::class.java)

        appCoroutineScope.launch(dispatcherProvider.io()) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("", getData()))

            withContext(dispatcherProvider.main()) {
                Toast.makeText(context, "Data copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun getData(): String {
        val textParts = listOf(
            "Account: ${authRepository.getAccount()}",
            "Subscription: ${authRepository.getSubscription()}",
            "Entitlements: ${authRepository.getEntitlements()}",
            "Access token (V2): ${authRepository.getAccessTokenV2()}",
            "Refresh token (V2): ${authRepository.getRefreshTokenV2()}",
            "Auth token (V1): ${authRepository.getAuthToken()}",
            "Access token (V1): ${authRepository.getAccessToken()}",
        )

        return textParts.joinToString(separator = "\n---\n")
    }
}

@ContributesMultibinding(ActivityScope::class)
class CopySubscriptionDataViewPlugin @Inject constructor() : SubsSettingPlugin {
    override fun getView(context: Context): View {
        return CopySubscriptionDataView(context)
    }
}
