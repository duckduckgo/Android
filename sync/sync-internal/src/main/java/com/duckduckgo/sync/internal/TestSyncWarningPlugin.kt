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

package com.duckduckgo.sync.internal

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.sync.api.SyncMessagePlugin
import com.duckduckgo.sync.internal.databinding.TestSyncMessagePluginBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@ContributesMultibinding(scope = ActivityScope::class, boundType = SyncMessagePlugin::class)
class TestSyncWarningPlugin @Inject constructor() : SyncMessagePlugin {
    override fun getView(context: Context): View {
        return TestSyncMessageView(context)
    }

    @SingleInstanceIn(scope = AppScope::class)
    class StateHolder @Inject constructor() {
        val isEnabled = MutableStateFlow(false)
    }

    @InjectWith(ViewScope::class)
    class TestSyncMessageView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0,
    ) : FrameLayout(context, attrs, defStyle) {
        private val binding by viewBinding<TestSyncMessagePluginBinding>()

        @Inject
        lateinit var stateHolder: StateHolder

        private val stateJob = ConflatedJob()

        init {
            binding.root.setText("This is a test warning triggered from the internal dev settings. You can dismiss it by tapping it.")
            setOnClickListener {
                stateHolder.isEnabled.value = false
            }
        }

        override fun onAttachedToWindow() {
            AndroidSupportInjection.inject(this)
            super.onAttachedToWindow()

            val lifecycleOwner = findViewTreeLifecycleOwner()!!
            stateJob += stateHolder.isEnabled
                .onEach { render(it) }
                .flowWithLifecycle(lifecycleOwner.lifecycle)
                .launchIn(lifecycleOwner.lifecycleScope)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            stateJob.cancel()
        }

        private fun render(isEnabled: Boolean) {
            isVisible = isEnabled
        }
    }
}
