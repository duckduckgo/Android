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

package com.duckduckgo.app.anr.internal.setting

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.anr.internal.databinding.CrashAnrsListViewBinding
import com.duckduckgo.app.anr.internal.feature.CrashANRsSettingPlugin
import com.duckduckgo.app.anr.internal.setting.VitalsAdapterList.VitalsItems.AnrItem
import com.duckduckgo.app.anr.internal.setting.VitalsAdapterList.VitalsItems.CrashItem
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(ViewScope::class)
class CrashANRsListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var crashANRsRepository: CrashANRsRepository

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    private val binding: CrashAnrsListViewBinding by viewBinding()
    private lateinit var anrAdapter: VitalsAdapterList

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        configureANRList()

        findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            crashANRsRepository.getANRs().combine(crashANRsRepository.getCrashes()) { anrs, crashes ->
                (
                    anrs.map {
                        AnrItem(
                            stackTrace = it.stackTrace,
                            customTab = it.customTab,
                            timestamp = it.timestamp,
                        )
                    } + crashes.map {
                        CrashItem(
                            stackTrace = it.stackTrace,
                            processName = it.processName,
                            customTab = it.customTab,
                            timestamp = it.timestamp,
                        )
                    }
                    ).sortedByDescending { it.timestamp }
            }.flowOn(dispatcherProvider.io()).collect {
                anrAdapter.setItems(it)
            }
        }
    }

    private fun configureANRList() {
        binding.anrList.layoutManager = LinearLayoutManager(context)
        anrAdapter = VitalsAdapterList()
        binding.anrList.adapter = anrAdapter
    }
}

@ContributesMultibinding(ActivityScope::class)
class CrashANRsListViewPlugin @Inject constructor() : CrashANRsSettingPlugin {
    override fun getView(context: Context): View {
        return CrashANRsListView(context)
    }
}
