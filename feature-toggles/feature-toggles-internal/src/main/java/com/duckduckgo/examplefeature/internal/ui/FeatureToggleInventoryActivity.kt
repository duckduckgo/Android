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
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.ActionBottomSheetDialog
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
    private val allToggles = MutableStateFlow<List<Toggle>?>(null)

    private val searchTextWatcher = object : TextChangedWatcher() {
        override fun afterTextChanged(editable: Editable) {
            featureNameFilter.value = editable.toString().trim()
        }
    }

    private val binding: ActivityFeatureToggleInventoryBinding by viewBinding()

    private val jsonAdapter by lazy {
        moshi.adapter(Toggle.State::class.java)
    }

    private val adapter by lazy {
        FeatureToggleAdapter(
            onToggleChanged = ::onToggleChanged,
            onItemClicked = ::onItemClicked,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        setupRecyclerView()
        binding.searchName.addTextChangedListener(searchTextWatcher)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { loadToggles() }
                launch { observeFilteredToggles() }
            }
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@FeatureToggleInventoryActivity)
            adapter = this@FeatureToggleInventoryActivity.adapter
            setHasFixedSize(true)
            setItemViewCacheSize(20)
            itemAnimator = null
        }
    }

    private suspend fun loadToggles() {
        val toggles = withContext(dispatcherProvider.io()) {
            featureTogglesInventory.getAll()
        }
        allToggles.value = toggles
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private suspend fun observeFilteredToggles() {
        combine(
            allToggles,
            featureNameFilter.debounce(200).distinctUntilChanged(),
        ) { toggles, filter ->
            toggles to filter
        }
            .filter { (toggles, _) -> toggles != null }
            .map { (toggles, filter) -> buildFilteredItems(toggles!!, filter) }
            .flowOn(dispatcherProvider.io())
            .collect { items ->
                withContext(dispatcherProvider.main()) {
                    adapter.submitList(items)
                    binding.progress.isVisible = false
                    binding.recyclerView.isVisible = true
                }
            }
    }

    private fun buildFilteredItems(toggles: List<Toggle>, nameFilter: String): List<FeatureToggleItem> {
        val match = nameFilter.trim()

        data class ToggleWithName(
            val toggle: Toggle,
            val featureName: Toggle.FeatureName,
        )

        fun Toggle.FeatureName.uniqueKey(): String = "${parentName.orEmpty()}|$name"

        val parentFeatures = ArrayList<ToggleWithName>(toggles.size)
        val subFeatures = ArrayList<ToggleWithName>(toggles.size)
        for (toggle in toggles) {
            val featureName = toggle.featureName()
            val item = ToggleWithName(toggle, featureName)
            if (featureName.parentName == null) {
                parentFeatures.add(item)
            } else {
                subFeatures.add(item)
            }
        }

        val nameComparator = Comparator<ToggleWithName> { a, b ->
            a.featureName.name.compareTo(b.featureName.name, ignoreCase = true)
        }
        parentFeatures.sortWith(nameComparator)
        subFeatures.sortWith(nameComparator)

        val subFeaturesByParentName = subFeatures.groupBy { it.featureName.parentName!! }
        val parentFeaturesByName = parentFeatures.associateBy { it.featureName.name }

        val features = if (match.isBlank()) {
            ArrayList<ToggleWithName>(toggles.size).apply {
                for (parent in parentFeatures) {
                    add(parent)
                    subFeaturesByParentName[parent.featureName.name]?.let { addAll(it) }
                }
            }
        } else {
            LinkedHashMap<String, ToggleWithName>(toggles.size).apply {
                for (parent in parentFeatures) {
                    if (parent.featureName.name.contains(match, ignoreCase = true)) {
                        putIfAbsent(parent.featureName.uniqueKey(), parent)
                        subFeaturesByParentName[parent.featureName.name]?.forEach { putIfAbsent(it.featureName.uniqueKey(), it) }
                    }
                }

                for (feature in subFeatures) {
                    if (feature.featureName.name.contains(match, ignoreCase = true)) {
                        feature.featureName.parentName?.let { parentName ->
                            parentFeaturesByName[parentName]?.let { putIfAbsent(it.featureName.uniqueKey(), it) }
                        }
                        putIfAbsent(feature.featureName.uniqueKey(), feature)
                    }
                }
            }.values.toList()
        }

        return features.map { item ->
            FeatureToggleItem(
                toggle = item.toggle,
                displayName = item.featureName.name,
                isSubFeature = item.featureName.parentName != null,
                isEnabled = item.toggle.isEnabled(),
            )
        }
    }

    private fun onToggleChanged(item: FeatureToggleItem, isChecked: Boolean) {
        lifecycleScope.launch(dispatcherProvider.io()) {
            val toggle = item.toggle
            toggle.getRawStoredState()?.let { state ->
                toggle.setRawStoredState(state.copy(remoteEnableState = isChecked))
            } ?: toggle.setRawStoredState(State(remoteEnableState = isChecked))

            val actualState = toggle.isEnabled()
            if (actualState != isChecked) {
                // Force-enabled toggle: correct switch immediately since DiffUtil won't see a change
                withContext(dispatcherProvider.main()) {
                    val position = adapter.currentList.indexOfFirst { it.featureKey == item.featureKey }
                    if (position >= 0) {
                        (binding.recyclerView.findViewHolderForAdapterPosition(position) as? FeatureToggleAdapter.ViewHolder)
                            ?.setSwitchState(actualState)
                    }
                }
            } else {
                // Normal toggle: trigger Flow rebuild to update adapter list
                allToggles.value = allToggles.value?.toList()
            }
        }
    }

    private fun onItemClicked(item: FeatureToggleItem) {
        lifecycleScope.launch(dispatcherProvider.io()) {
            val featureState = item.toggle.getRawStoredState()
            withContext(dispatcherProvider.main()) {
                ActionBottomSheetDialog.Builder(this@FeatureToggleInventoryActivity)
                    .setTitle("Feature Flag info")
                    .setPrimaryItem(featureState?.toJsonString() ?: "[empty]")
                    .show()
            }
        }
    }

    private fun Toggle.State.toJsonString(): String {
        val buffer = Buffer()
        val writer = JsonWriter.of(buffer).apply {
            indent = "  "
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
