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
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.subscriptions.impl.store.SubscriptionsDataStore
import com.duckduckgo.subscriptions.internal.SubsSettingPlugin
import com.duckduckgo.subscriptions.internal.databinding.SubsSimpleViewBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
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
    lateinit var subscriptionsDataStore: SubscriptionsDataStore

    private val binding: SubsSimpleViewBinding by viewBinding()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.root.setPrimaryText("Copy subscriptions data")
        binding.root.setSecondaryText("Copies your data to the clipboard")

        binding.root.setClickListener {
            copyDataToClipboard()
        }
    }

    private fun copyDataToClipboard() {
        val clipboardManager = context.getSystemService(ClipboardManager::class.java)

        appCoroutineScope.launch(dispatcherProvider.io()) {
            val auth = subscriptionsDataStore.authToken
            val authToken = if (auth.isNullOrBlank()) {
                "No auth token found"
            } else {
                auth
            }

            val access = subscriptionsDataStore.accessToken
            val accessToken = if (access.isNullOrBlank()) {
                "No access token found"
            } else {
                access
            }

            val external = subscriptionsDataStore.externalId
            val externalId = if (external.isNullOrBlank()) {
                "No external id found"
            } else {
                external
            }
            val text = "Auth token is $authToken || Access token is $accessToken || External id is $externalId"

            clipboardManager.setPrimaryClip(ClipData.newPlainText("", text))

            withContext(dispatcherProvider.main()) {
                Toast.makeText(context, "Data copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@ContributesMultibinding(ActivityScope::class)
class CopySubscriptionDataViewPlugin @Inject constructor() : SubsSettingPlugin {
    override fun getView(context: Context): View {
        return CopySubscriptionDataView(context)
    }
}
