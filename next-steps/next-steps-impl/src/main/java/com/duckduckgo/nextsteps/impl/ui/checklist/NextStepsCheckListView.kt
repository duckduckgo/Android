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
package com.duckduckgo.nextsteps.impl.ui.checklist

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.transition.ChangeBounds
import androidx.transition.Fade
import androidx.transition.Transition
import androidx.transition.TransitionListenerAdapter
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.bumptech.glide.Glide
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.android.support.AndroidSupportInjection
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.nextsteps.impl.R
import com.duckduckgo.nextsteps.impl.databinding.ViewNextStepsCheckListBinding
import com.duckduckgo.nextsteps.impl.databinding.ViewNextStepsCheckListItemBinding
import com.duckduckgo.nextsteps.impl.ui.NextStepsActionLauncher
import com.duckduckgo.nextsteps.impl.ui.checklist.NextStepsCheckListViewModel.Command
import com.duckduckgo.nextsteps.impl.ui.checklist.NextStepsCheckListViewModel.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(ViewScope::class)
class NextStepsCheckListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : ConstraintLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var actionLauncher: NextStepsActionLauncher

    private val binding: ViewNextStepsCheckListBinding by viewBinding()

    private val viewModel: NextStepsCheckListViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[NextStepsCheckListViewModel::class.java]
    }

    private val conflatedStateJob = ConflatedJob()
    private val conflatedCommandJob = ConflatedJob()
    private var completionAnimationRunning = false

    init {
        val horizontalPadding = resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.keyline_4)
        val bottomPadding = resources.getDimensionPixelSize(R.dimen.nextStepsStackPaddingBottom)
        setPadding(horizontalPadding, horizontalPadding, horizontalPadding, bottomPadding)
        clipToPadding = false
        clipChildren = false
        binding.closeButton.setOnClickListener { viewModel.onSectionDismissed() }
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        actionLauncher.register(context)

        val lifecycleOwner = findViewTreeLifecycleOwner()!!
        lifecycleOwner.lifecycle.addObserver(viewModel)
        viewModel.onViewAttached()
        conflatedStateJob += lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.viewState.collect { render(it) }
            }
        }
        conflatedCommandJob += viewModel.commands()
            .onEach { processCommand(it) }
            .launchIn(lifecycleOwner.lifecycleScope)
    }

    override fun onDetachedFromWindow() {
        completionAnimationRunning = false
        conflatedStateJob.cancel()
        conflatedCommandJob.cancel()
        viewModel.onViewDetached()
        actionLauncher.unregister()
        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(viewModel)
        super.onDetachedFromWindow()
    }

    private fun processCommand(command: Command) {
        when (command) {
            is Command.LaunchAddWidget -> actionLauncher.launchAddWidget(context, command.widgetType)
            is Command.LaunchDefaultBrowserDialog -> {
                if (actionLauncher.launchDefaultBrowserDialog(context)) viewModel.onDefaultBrowserDialogShown()
            }
            is Command.LaunchDefaultBrowserSettings -> actionLauncher.launchDefaultBrowserSettings(context)
            is Command.LaunchScreen -> actionLauncher.launchScreen(context, command.screen, command.payload)
            is Command.AnimateItemCompletion -> {
                animateItemCompletion(command.itemId, command.toPosition) { viewModel.onItemCompletionAnimated(command.itemId) }
            }
        }
    }

    private fun render(viewState: ViewState) {
        if (!completionAnimationRunning) {
            if (viewState.message == null || viewState.items.isEmpty()) {
                gone()
                return
            }
            binding.sectionTitle.text = viewState.title
            bindItems(viewState.items)
            show()
        }
    }

    private fun bindItems(items: List<ViewState.Item>) {
        val container = binding.itemsContainer
        val inflater = LayoutInflater.from(context)
        val existingRows = container.children.associateBy { it.tag }
        val rows = items.map { item ->
            existingRows[item.item.id] ?: ViewNextStepsCheckListItemBinding.inflate(inflater, container, false).root.apply { tag = item.item.id }
        }
        rows.forEachIndexed { index, row ->
            if (container.getChildAt(index) !== row) {
                container.removeView(row)
                container.addView(row, index)
            }
        }
        while (container.childCount > rows.size) {
            container.removeViewAt(container.childCount - 1)
        }
        items.forEachIndexed { index, item ->
            val row = ViewNextStepsCheckListItemBinding.bind(rows[index])
            row.divider.isVisible = index > 0
            row.bind(item)
        }
    }

    private fun ViewNextStepsCheckListItemBinding.bind(item: ViewState.Item) {
        itemTitle.text = item.item.titleText
        actionButton.alpha = 1f
        completedIcon.alpha = 1f
        if (item.completed) {
            actionButton.gone()
            completedIcon.show()
            itemRow.setOnClickListener(null)
            itemRow.isClickable = false
        } else {
            completedIcon.gone()
            actionButton.show()
            itemRow.setOnClickListener { viewModel.onItemClicked(item.item.id) }
        }
        val imageUrl = item.item.imageUrl
        if (imageUrl.isNullOrEmpty()) {
            Glide.with(itemImage).clear(itemImage)
            itemImage.tag = null
            itemImage.gone()
        } else if (itemImage.tag != imageUrl) {
            itemImage.tag = imageUrl
            itemImage.show()
            Glide.with(itemImage)
                .load(imageUrl)
                .fitCenter()
                .into(itemImage)
        }
    }

    private fun animateItemCompletion(
        itemId: String,
        toPosition: Int,
        onAnimationEnd: () -> Unit,
    ) {
        if (completionAnimationRunning) return
        val row = binding.itemsContainer.children.firstOrNull { it.tag == itemId } ?: return onAnimationEnd()
        completionAnimationRunning = true
        val finish = {
            completionAnimationRunning = false
            onAnimationEnd()
        }
        val rowBinding = ViewNextStepsCheckListItemBinding.bind(row)
        rowBinding.itemRow.setOnClickListener(null)
        rowBinding.itemRow.isClickable = false
        rowBinding.completedIcon.alpha = 0f
        rowBinding.completedIcon.show()
        rowBinding.completedIcon.animate().alpha(1f).setDuration(COMPLETION_CROSSFADE_MS).start()
        rowBinding.actionButton.animate()
            .alpha(0f)
            .setDuration(COMPLETION_CROSSFADE_MS)
            .withEndAction {
                rowBinding.actionButton.visibility = View.INVISIBLE
                row.postDelayed({ moveRow(row, toPosition, finish) }, COMPLETION_HOLD_MS)
            }
            .start()
    }

    private fun moveRow(
        row: View,
        toPosition: Int,
        onAnimationEnd: () -> Unit,
    ) {
        val container = binding.itemsContainer
        val rowIndex = container.indexOfChild(row)
        val targetIndex = toPosition.coerceIn(0, container.childCount - 1)
        if (rowIndex < 0 || rowIndex == targetIndex) return onAnimationEnd()

        val transition = TransitionSet()
            .addTransition(ChangeBounds())
            .addTransition(Fade())
            .setDuration(COMPLETION_MOVE_MS)
            .setInterpolator(FastOutSlowInInterpolator())
            .addListener(
                object : TransitionListenerAdapter() {
                    override fun onTransitionEnd(transition: Transition) = onAnimationEnd()
                },
            )
        TransitionManager.beginDelayedTransition(binding.checkListCard, transition)
        container.removeView(row)
        container.addView(row, targetIndex)
        container.children.forEachIndexed { index, child ->
            ViewNextStepsCheckListItemBinding.bind(child).divider.isVisible = index > 0
        }
    }

    private companion object {
        const val COMPLETION_CROSSFADE_MS = 200L
        const val COMPLETION_HOLD_MS = 350L
        const val COMPLETION_MOVE_MS = 350L
    }
}
