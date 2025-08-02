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

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.subscriptions.internal.SubsSettingPlugin
import com.duckduckgo.subscriptions.internal.SubscriptionsInternalStore
import com.duckduckgo.subscriptions.internal.databinding.SubsOverrideUrlViewBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

@InjectWith(ViewScope::class)
class OverrideSubscriptionBaseUrlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var store: SubscriptionsInternalStore

    private val binding: SubsOverrideUrlViewBinding by viewBinding()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.also { base ->
            base.urlEditText.text = store.getBaseUrl().orEmpty()
            base.resetButton.setOnClickListener {
                base.urlEditText.text = ""
                store.setBaseUrl(null)
                Toast.makeText(context?.applicationContext, "Base URL reset to http://duckduckgo.com/subscriptions", Toast.LENGTH_SHORT).show()
            }
            base.saveButton.setOnClickListener {
                store.setBaseUrl(base.urlEditText.text.trim())
                Toast.makeText(context?.applicationContext, "Base URL saved", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@ContributesMultibinding(ActivityScope::class)
class OverrideSubscriptionBaseUrlViewPlugin @Inject constructor() : SubsSettingPlugin {
    override fun getView(context: Context): View {
        return OverrideSubscriptionBaseUrlView(context)
    }
}
