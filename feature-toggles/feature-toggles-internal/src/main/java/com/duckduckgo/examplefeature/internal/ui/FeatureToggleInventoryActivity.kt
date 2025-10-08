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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.ActionBottomSheetDialog
import com.duckduckgo.common.ui.view.listitem.OneLineListItem
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.text.TextChangedWatcher
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.featuretoggles.internal.databinding.ActivityFeatureToggleInventoryBinding
import com.duckduckgo.internal.features.api.InternalFeaturePlugin
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Buffer
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(LaunchFeatureToggleInventoryActivityNoParams::class)
class FeatureToggleInventoryActivity : DuckDuckGoActivity() {
    @Inject
    lateinit var featureTogglesInventory: FeatureTogglesInventory

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var moshi: Moshi

    private val featureNameFilter = MutableStateFlow("")

    private val searchTextWatcher = object : TextChangedWatcher() {
        override fun afterTextChanged(editable: Editable) {
            featureNameFilter.value = editable.toString().trim()
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
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                featureNameFilter.collect { populateViews(nameFilter = it) }
            }
        }
    }

    private suspend fun populateViews(nameFilter: String) = withContext(dispatcherProvider.io()) {
        val views = getFeatureViews(nameFilter)

        withContext(dispatcherProvider.main()) {
            binding.featureToggle.removeAllViews()
            views.forEach { binding.featureToggle.addView(it) }
            binding.progress.isVisible = false
            binding.container.isVisible = true
        }
    }

    private suspend fun getFeatureViews(nameFilter: String): List<View> = withContext(dispatcherProvider.io()) {
        val toggles = this@FeatureToggleInventoryActivity.toggles.await()
        val match = nameFilter.lowercase()
        val parentFeatures = toggles
            .filter { it.featureName().parentName == null }
            .sortedBy { it.featureName().name.lowercase() }
        val subFeatures = toggles
            .filter { it.featureName().parentName != null }
            .sortedBy { it.featureName().name.lowercase() }

        val features = mutableListOf<Toggle>().apply {
            // add parent features that match and all their sub-features
            parentFeatures.forEach { parentFeature ->
                if (match.isNotBlank()) {
                    if (parentFeature.featureName().name.lowercase().contains(match)) {
                        add(parentFeature)
                        // add also all sub-features
                        addAll(
                            subFeatures.filter { it.featureName().parentName == parentFeature.featureName().name },
                        )
                    }
                } else {
                    for (parent in parentFeatures) {
                        add(parent)
                        addAll(subFeatures.filter { it.featureName().parentName == parent.featureName().name })
                    }
                }
            }

            // add sub-features that match and their parent feature
            subFeatures.forEach { feature ->
                if (match.isNotBlank() && feature.featureName().name.lowercase().contains(match)) {
                    // add its parent too
                    addAll(
                        parentFeatures.filter { it.featureName().name == feature.featureName().parentName },
                    )
                    add(feature)
                }
            }
        }.distinct() // de-dup

        val views = features.map { feature ->
            OneLineListItem(this@FeatureToggleInventoryActivity).apply {
                if (feature.featureName().parentName != null) {
                    setPrimaryText("\u2514   ${feature.featureName().name}")
                    setPrimaryTextTruncation(truncated = true)
                } else {
                    setPrimaryText(feature.featureName().name)
                }
                showSwitch()
                quietlySetIsChecked(feature.isEnabled()) { buttonView, isChecked ->
                    // the callback will be executed in main thread so we need to move it off of it
                    this@FeatureToggleInventoryActivity.lifecycleScope.launch(dispatcherProvider.io()) {
                        feature.getRawStoredState()?.let { state ->
                            // we change the 'remoteEnableState' instead of the 'enable' state because the latter is
                            // a computed state
                            feature.setRawStoredState(state.copy(remoteEnableState = isChecked))
                        } ?: feature.setRawStoredState(State(remoteEnableState = isChecked))

                        // Validate the toggle state. For instance, we won't be able to disable toggles forced-enabled
                        // for internal builds
                        feature.isEnabled().let {
                            withContext(dispatcherProvider.main()) {
                                buttonView.isChecked = it
                            }
                        }
                    }
                }
                setOnClickListener { _ ->
                    this@FeatureToggleInventoryActivity.lifecycleScope.launch(dispatcherProvider.io()) {
                        val featureState = feature.getRawStoredState()
                        withContext(dispatcherProvider.main()) {
                            ActionBottomSheetDialog.Builder(this@FeatureToggleInventoryActivity)
                                .setTitle("Feature Flag info")
                                .setPrimaryItem(featureState?.toJsonString() ?: "[empty]")
                                .show()
                        }
                    }
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
