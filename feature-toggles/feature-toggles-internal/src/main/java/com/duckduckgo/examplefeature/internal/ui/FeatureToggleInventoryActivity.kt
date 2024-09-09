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

package com.duckduckgo.examplefeature.internal.ui

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.AtomicReference
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.ActionBottomSheetDialog
import com.duckduckgo.common.ui.view.listitem.OneLineListItem
import com.duckduckgo.common.ui.view.text.TextChangedWatcher
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.featuretoggles.internal.databinding.ActivityFeatureToggleInventoryBinding
import com.duckduckgo.internal.features.api.InternalFeaturePlugin
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import okio.Buffer

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(LaunchFeatureToggleInventoryActivityNoParams::class)
class FeatureToggleInventoryActivity : DuckDuckGoActivity() {
    @Inject
    lateinit var featureTogglesInventory: FeatureTogglesInventory

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var moshi: Moshi

    private val featureNameFilter: AtomicReference<String> = AtomicReference("")

    private val searchTextWatcher = object : TextChangedWatcher() {
        override fun afterTextChanged(editable: Editable) {
            logcat { "aitor editable $editable" }
            when {
                editable.toString().isBlank() -> {
                    featureNameFilter.set("")
                }
                else -> {
                    featureNameFilter.set(editable.toString())
                }
            }
            lifecycleScope.launch {
                populateViews()
            }
        }
    }

    private val binding: ActivityFeatureToggleInventoryBinding by viewBinding()

    private val toggles: Deferred<List<Toggle>> = lifecycleScope.async(start = LAZY) {
        featureTogglesInventory.getAll()
    }

    private val jsonAdapter by lazy {
        moshi.adapter(Toggle.State::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        binding.searchName.addTextChangedListener(searchTextWatcher)

        lifecycleScope.launch {
            populateViews()
        }
    }

    private suspend fun populateViews() = withContext(dispatcherProvider.io()) {
        val views = getFeatureViews()

        withContext(dispatcherProvider.main()) {
            binding.featureToggle.removeAllViews()
            views.forEach { binding.featureToggle.addView(it) }
            binding.progress.isVisible = false
            binding.container.isVisible = true
        }
    }

    private suspend fun getFeatureViews(): List<View> = withContext(dispatcherProvider.io()) {
        val toggles = this@FeatureToggleInventoryActivity.toggles.await()
        val match = featureNameFilter.get().lowercase()
        logcat { "aitor $match" }
        val parentFeature = toggles
            .filter { it.featureName().parentName == null }
            .sortedBy { it.featureName().name.lowercase() }
        val subFeatures = toggles
            .filter { it.featureName().parentName != null }
            .sortedBy { it.featureName().name.lowercase() }
        val features = mutableListOf<Toggle>().apply {
            for (parent in parentFeature) {
                add(parent)
                addAll(subFeatures.filter { it.featureName().parentName == parent.featureName().name })
            }
            // Apply search box filter if needed
        }.filter {
            if (match.isNotBlank()) {
                it.featureName().name.lowercase().contains(match)
            } else {
                true
            }
        }

        val views = features.map { feature ->
            OneLineListItem(this@FeatureToggleInventoryActivity).apply {
                if (feature.featureName().parentName != null) {
                    setPrimaryText("\u2514   ${feature.featureName().name}")
                    setPrimaryTextTruncation(truncated = true)
                } else {
                    setPrimaryText(feature.featureName().name)
                }
                showSwitch()
                val featureState = feature.getRawStoredState()
                quietlySetIsChecked(featureState?.enable == true) { _, isChecked ->
                    feature.getRawStoredState()?.let { state ->
                        logcat { "setting $isChecked for ${feature.featureName()}" }
                        feature.setEnabled(state.copy(enable = isChecked))
                    }
                }
                setOnClickListener { _ ->
                    ActionBottomSheetDialog.Builder(this@FeatureToggleInventoryActivity)
                        .setTitle("Feature Flag info")
                        .setPrimaryItem(featureState?.toJsonString() ?: "[empty]")
                        .show()
                }
            }
        }

        return@withContext views
    }

    private fun Toggle.State.toJsonString(): String {
        val buffer = Buffer()
        val writer = JsonWriter.of(buffer).apply {
            indent = "  " // 2 spaces indent
        }
        jsonAdapter.toJson(writer, this)

        return buffer.readUtf8()
    }
}

object LaunchFeatureToggleInventoryActivityNoParams : ActivityParams {
    private fun readResolve(): Any = LaunchFeatureToggleInventoryActivityNoParams
}

@ContributesMultibinding(AppScope::class)
@PriorityKey(InternalFeaturePlugin.FEATURE_INVENTORY_PRIO_KEY)
class FeatureInventoryInternalFeaturePlugin @Inject constructor(
    private val globalActivityStarter: GlobalActivityStarter,
) : InternalFeaturePlugin {
    override fun internalFeatureTitle(): String {
        return "Feature Flag Inventory"
    }

    override fun internalFeatureSubtitle(): String {
        return "Inventory of all App feature flags"
    }

    override fun onInternalFeatureClicked(activityContext: Context) {
        globalActivityStarter.start(activityContext, LaunchFeatureToggleInventoryActivityNoParams)
    }
}
