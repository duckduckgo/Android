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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.anr.internal.databinding.AnrsListViewBinding
import com.duckduckgo.app.anr.internal.databinding.ItemAnrBinding
import com.duckduckgo.app.anr.internal.feature.CrashANRsSettingPlugin
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@InjectWith(ViewScope::class)
class ANRsListView @JvmOverloads constructor(
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

    private val binding: AnrsListViewBinding by viewBinding()
    private lateinit var anrAdapter: ANRAdapterList

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        configureANRList()

        ViewTreeLifecycleOwner.get(this)?.lifecycleScope?.launch {
            crashANRsRepository.getANRs().flowOn(dispatcherProvider.io()).map {
                it.map { anr -> ANRAdapterList.AnrItem(anr.stackTrace) }
            }.collect {
                anrAdapter.setItems(it)
            }
        }
    }

    private fun configureANRList() {
        binding.anrList.layoutManager = LinearLayoutManager(context)
        anrAdapter = ANRAdapterList()
        binding.anrList.adapter = anrAdapter
    }
}

class ANRAdapterList() : Adapter<RecyclerView.ViewHolder>() {

    private var listItems = listOf<AnrItem>()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        return when (viewType) {
            ITEM_VIEW_TYPE_ANR -> {
                val binding = ItemAnrBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                ANRViewHolder(binding)
            }

            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun getItemCount(): Int {
        return listItems.size
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        when (holder) {
            is ANRViewHolder -> {
                val item = listItems[position]
                populateANRItem(item, holder)
            }
        }
    }

    fun setItems(items: List<AnrItem>) {
        listItems = items
        notifyDataSetChanged()
    }

    private fun populateANRItem(
        anr: AnrItem,
        viewHolder: ANRViewHolder,
    ) {
        with(viewHolder) {
            binding.anrItem.setPrimaryText(anr.stackTrace.take(500))
        }
    }

    data class AnrItem(
        val stackTrace: String,
    )

    open class ANRViewHolder(open val binding: ItemAnrBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private const val ITEM_VIEW_TYPE_ANR = 0
    }
}

@ContributesMultibinding(ActivityScope::class)
class ANRsListViewPlugin @Inject constructor() : CrashANRsSettingPlugin {
    override fun getView(context: Context): View {
        return ANRsListView(context)
    }
}
